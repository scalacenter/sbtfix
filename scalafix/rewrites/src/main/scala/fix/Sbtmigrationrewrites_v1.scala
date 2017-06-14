package fix

import scala.collection.immutable.Seq
import scala.meta._
import scala.meta._
import scala.meta.sbthost.Sbthost
import scala.meta.tokens.Token.LeftParen
import scala.meta.tokens.Token.RightParen
import scalafix._

case class Sbtmigrationrewrites_v1(brokenMirror: Mirror)
    extends SemanticRewrite(Sbthost.patchMirror(brokenMirror)) {
  val mirror = ImplicitMirror
  def rewrite(ctx: RewriteCtx): Patch = {
    ctx.reporter.info(mirror.database.toString())
    ctx.reporter.info(ctx.tree.syntax)
    ctx.reporter.info(ctx.tree.structure)
    SbtOneZeroMigration(mirror, ctx).rewrite.asPatch
  }
}

/**
  * Migrates code from sbt 0.13.x to sbt 1.0.x.
  *
  */
case class SbtOneZeroMigration(mirror: Mirror, ctx: RewriteCtx) {
  sealed abstract class SbtOperator {
    val operator: String
    val newOperator: String

    object SbtSelectors {
      val value = ".value"
      val taskValue = ".taskValue"
      // val evaluated = ".evaluated"
    }

    object SpecialCases {
//      val ctx: Interpreted = sbtContext.interpretContext
      val keyOfTasks: Set[String] = Set.empty
      val inputKeys: Set[String] = Set.empty
    }

    def unapply(tree: Term): Option[(Term, Token, Term.Arg)] = tree match {
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

    private def wrapInParenthesis(tokens: Tokens): List[Patch] = {
      List(
        ctx.addLeft(tokens.head, "("),
        ctx.addRight(tokens.last, ")")
      )
    }

    private def isParensWrapped(tokens: Tokens): Boolean = {
      tokens.head.isInstanceOf[LeftParen] &&
      tokens.last.isInstanceOf[RightParen]
    }

    private def existKeys(lhs: Term, keyNames: Set[String]): Boolean = {
      val singleNames = lhs match {
        case tname @ Term.Name(name) if keyNames.contains(name) => tname :: Nil
        case _ => Nil
      }
      val scopedNames = lhs.collect {
        case Term.Select(Term.Name(name), Term.Name("in"))
            if keyNames.contains(name) =>
          name
        case Term.ApplyInfix(Term.Name(name), Term.Name("in"), _, _)
            if keyNames.contains(name) =>
          name
      }
      (singleNames ++ scopedNames).nonEmpty
    }

    def rewriteDslOperator(lhs: Term,
                           opToken: Token,
                           rhs: Term.Arg): List[Patch] = {
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
        val requiresTaskValue = existKeys(lhs, SpecialCases.keyOfTasks)
        // val requiresEvaluated = existKeys(lhs, SpecialCases.inputKeys)
        val newSelector =
          if (requiresTaskValue) SbtSelectors.taskValue
          // else if (requiresEvaluated) SbtSelectors.evaluated
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
      case `<<=`(lhs: Term, opToken: Token, rhs: Term.Arg) =>
        `<<=`.rewriteDslOperator(lhs, opToken, rhs)
      case `<+=`(lhs: Term, opToken: Token, rhs: Term.Arg) =>
        `<+=`.rewriteDslOperator(lhs, opToken, rhs)
      case `<++=`(lhs: Term, opToken: Token, rhs: Term.Arg) =>
        `<++=`.rewriteDslOperator(lhs, opToken, rhs)
    }.flatten
  }
}
