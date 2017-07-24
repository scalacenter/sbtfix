package sbt.rewrite

import scala.meta._
import scala.meta.tokens.Token.{LeftParen, RightParen}
import scalafix.rewrite.{Rewrite, RewriteCtx}
import scalafix.patch.{Patch, TokenPatch}

/**
  * Migrates code from sbt 0.13.x to sbt 1.0.x.
  *
  * These rewrites are only syntactic, but they can use semantic runtime
  * information captured from sbt via the sbt plugin. Fully semantic rewrites
  * are not possible because Scala Meta does not cross-compile to Scala 2.10.
  *
  * Some of the rewrites here provided are speculative and don't work in 100%
  * of all the cases. These rewrites currently assume:
  *
  * - Sbt DSL operators are not defined by a third party.
  * - If they are defined, they are not binary operators.
  *
  * If those conditions are not met, the sbt rewriter will not work correctly.
  *
  * Note that some rewrites here present may need semantic information to
  * disambiguate which sbt macro should be executed. For instance, keys that
  * store tasks or settings need `.taskValue` instead of `.value`.
  *
  * The sbt runtime interpreter allows the rewrite to get access to all the
  * present sbt keys and analyze their type, however this information is not
  * reliable enough to be called "semantic". The reasons are the following:
  *
  * - Manifest pretty printer will produce inaccurate type representations.
  *   For instance, type closures don't follow Scala syntax.
  * - Manifest prints fully qualified names for all names not present in
  *   Scala jars, but there is no way to check this contract is not broken.
  *
  * Therefore, with all the previous explanation, these rewrites do a
  * best-effort to migrate sbt code from 0.13.x to 1.0.x. They will work in most
  * of the cases. In the rest, your builds may need some manual intervention.
  */
case class SbtOneZeroMigration(sbtContext: SbtContext) extends Rewrite {
  sealed abstract class SbtOperator {
    val operator: String
    val newOperator: String

    object SbtSelectors {
      val value = ".value"
      // val taskValue = ".taskValue"
      val evaluated = ".evaluated"
    }

    object SpecialCases {
      val ctx: Interpreted = sbtContext.interpretContext
      val keyOfTasks: Set[String] = ctx.keyOfTasks.toSet
      val inputKeys: Set[String] = ctx.inputKeys.toSet
      ctx.reportToUser()
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

    private def wrapInParenthesis(ctx: RewriteCtx,
                                  tokens: Tokens): List[Patch] = {
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

    def rewriteDslOperator(ctx: RewriteCtx,
                           lhs: Term,
                           opToken: Token,
                           rhs: Term): List[Patch] = {
      val wrapExpression = rhs match {
        case arg @ Term.Apply(_, Seq(_: Term.Block))
            if !isParensWrapped(arg.tokens) =>
          wrapInParenthesis(ctx, arg.tokens)
        case arg: Term.ApplyInfix if !isParensWrapped(arg.tokens) =>
          wrapInParenthesis(ctx, arg.tokens)
        case _ => Nil
      }

      val removeOperator = ctx.removeToken(opToken)
      val addNewOperator = ctx.addLeft(opToken, newOperator)
      val rewriteRhs = {
        val requiresEvaluated = existKeys(lhs, SpecialCases.inputKeys)
        val requiresValue = !existKeys(lhs, SpecialCases.keyOfTasks)
        val newSelector =
          if (requiresEvaluated) Some(SbtSelectors.evaluated)
          else if (requiresValue) Some(SbtSelectors.value)
          else None

        newSelector.map(ctx.addRight(rhs.tokens.last, _))
      }

      removeOperator :: addNewOperator :: wrapExpression ++ rewriteRhs.toList
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

  override def rewrite(ctx: RewriteCtx): Patch = {
    val patches = ctx.tree.collect {
      case `<<=`(lhs: Term, opToken: Token, rhs: Term) =>
        `<<=`.rewriteDslOperator(ctx, lhs, opToken, rhs)
      case `<+=`(lhs: Term, opToken: Token, rhs: Term) =>
        `<+=`.rewriteDslOperator(ctx, lhs, opToken, rhs)
      case `<++=`(lhs: Term, opToken: Token, rhs: Term) =>
        `<++=`.rewriteDslOperator(ctx, lhs, opToken, rhs)
    }.flatten
    patches.asPatch
  }
}
