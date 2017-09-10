package fix

import scala.collection.immutable.Seq
import scala.meta._
import scala.meta.internal.io.PathIO
import scala.meta.sbthost.Sbthost
import scala.meta.tokens.Token.LeftParen
import scala.meta.tokens.Token.RightParen
import scalafix._
import scalafix.internal.util.SemanticCtxImpl

case class Sbtfix_v1(sctx: SemanticCtx)
    extends SemanticRewrite(
      new SemanticCtxImpl(
        Sbthost.patchDatabase(sctx.database, PathIO.workingDirectory))) {
  def rewrite(ctx: RewriteCtx): Patch = {
    SbtOneZeroMigrator(ImplicitSemanticCtx, ctx).rewrite.asPatch
  }
}

/**
  * Migrates code from sbt 0.13.x to sbt 1.0.x.
  */
case class SbtOneZeroMigrator(sctx: SemanticCtx, ctx: RewriteCtx) {
  sealed abstract class SbtOperator {
    val operator: String
    val newOperator: String

    object SbtSelectors {
      val value = ".value"
      val evaluated = ".evaluated"
    }

    object SbtTypes {
      val inputKey: String = "sbt.InputKey["
    }

    def unapply(tree: Term): Option[(Term, Token, Term)] = tree match {
      case Term.ApplyInfix(lhs, o @ Term.Name(`operator`), _, Seq(rhs)) =>
        Some((lhs, o.tokens.head, rhs))
      case Term.Apply(Term.Select(lhs, o @ Term.Name(`operator`)), Seq(rhs)) =>
        Some((lhs, o.tokens.head, rhs))
      case Term.Apply(
          Term.ApplyType(Term.Select(lhs, o @ Term.Name(`operator`)), _),
          Seq(rhs)) =>
        Some((lhs, o.tokens.head, rhs))
      case _ =>
        None
    }

    private def wrapInParenthesis(tokens: Tokens): List[Patch] =
      List(ctx.addLeft(tokens.head, "("), ctx.addRight(tokens.last, ")"))

    private def isParensWrapped(tokens: Tokens): Boolean = {
      tokens.head.isInstanceOf[LeftParen] &&
      tokens.last.isInstanceOf[RightParen]
    }

    private def infoStartsWith(r: Term.Ref, prefix: String): Boolean =
      sctx
        .symbol(r.pos)
        .flatMap(sctx.denotation)
        .exists(denot => denot.info.startsWith(prefix))

    private def existKeys(lhs: Term, typePrefix: String): Boolean = {
      val singleNames = lhs match {
        case tn @ Term.Name(name) if infoStartsWith(tn, typePrefix) =>
          tn :: Nil
        case _ => Nil
      }
      val scopedNames = lhs.collect {
        case Term.Select(tn @ Term.Name(name), Term.Name("in"))
            if infoStartsWith(tn, typePrefix) =>
          name
        case Term.ApplyInfix(tn @ Term.Name(name), Term.Name("in"), _, _)
            if infoStartsWith(tn, typePrefix) =>
          name
      }
      (singleNames ++ scopedNames).nonEmpty
    }

    def rewriteDslOperator(lhs: Term, opToken: Token, rhs: Term): List[Patch] = {
      val wrapExpression = rhs match {
        case arg @ Term.Apply(_, Seq(_: Term.Block))
            if !isParensWrapped(arg.tokens) =>
          wrapInParenthesis(arg.tokens)
        case arg: Term.ApplyInfix if !isParensWrapped(arg.tokens) =>
          wrapInParenthesis(arg.tokens)
        case _ => Nil
      }

      val removeOperator = ctx.removeToken(opToken)
      val addNewOperator = ctx.addLeft(opToken, newOperator)
      val rewriteRhs = {
        val requiresEvaluated = existKeys(lhs, SbtTypes.inputKey)
        val newSelector =
          if (requiresEvaluated) SbtSelectors.evaluated
          else SbtSelectors.value
        ctx.addRight(rhs.tokens.last, newSelector)
      }

      (removeOperator :: addNewOperator :: wrapExpression) ++ Seq(rewriteRhs)
    }
  }

  object `<<=` extends SbtOperator {
    override final val operator = "<<="
    override final val newOperator: String = ":="
  }

  object `<+=` extends SbtOperator {
    override final val operator = "<+="
    override final val newOperator: String = "+="
  }

  object `<++=` extends SbtOperator {
    override final val operator = "<++="
    override final val newOperator: String = "++="
  }

  def rewrite: Seq[Patch] = {
    ctx.tree.collect {
      case `<<=`(lhs: Term, opToken: Token, rhs: Term) =>
        `<<=`.rewriteDslOperator(lhs, opToken, rhs)
      case `<+=`(lhs: Term, opToken: Token, rhs: Term) =>
        `<+=`.rewriteDslOperator(lhs, opToken, rhs)
      case `<++=`(lhs: Term, opToken: Token, rhs: Term) =>
        `<++=`.rewriteDslOperator(lhs, opToken, rhs)
    }.flatten
  }
}
