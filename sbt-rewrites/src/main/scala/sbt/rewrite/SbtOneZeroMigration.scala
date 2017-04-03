package sbt.rewrite

import scala.collection.immutable.Seq
import scala.meta._
import scala.meta.tokens.Token.{LeftParen, RightParen}
import scalafix.rewrite.{Rewrite, RewriteCtx}
import scalafix.util._

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
  * For instance, sbt settings or tasks that store themselves tasks or settings
  * are not rewritten correctly because they need `.taskValue` instead of the
  * regular `.value`. `sourceGenerators` and `resourceGenerator` are such a
  * case, but the migration tool special cases them to make sure they are
  * correctly handled.
  */
case class SbtOneZeroMigration(sbtContext: SbtContext) extends Rewrite[Any] {
  sealed abstract class SbtOperator {
    val operator: String
    val newOperator: String

    object SbtSelectors {
      val value = ".value"
      val taskValue = ".taskValue"
      val evaluated = ".evaluated"
    }

    object SpecialCases {
      // Default keys have to stay for `SbtOneZeroMigrationSpec` to work for now
      val defaultKeyOfTasks = List("sourceGenerators", "resourceGenerators")
      val defaultInputKeys = List("run", "runMain", "testOnly", "testQuick")
      val ctx: Interpreted = sbtContext.interpretContext
      ctx.reportToUser()
      val keyOfTasks: Set[String] = (defaultKeyOfTasks ++ ctx.keyOfTasks).toSet
      val inputKeys: Set[String] = (defaultInputKeys ++ ctx.inputKeys).toSet
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
        TokenPatch.AddLeft(tokens.head, "("),
        TokenPatch.AddRight(tokens.last, ")")
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

      val removeOperator = TokenPatch.Remove(opToken)
      val addNewOperator = TokenPatch.AddLeft(opToken, newOperator)
      val rewriteRhs = {
        val requiresTaskValue = existKeys(lhs, SpecialCases.keyOfTasks)
        val requiresEvaluated = existKeys(lhs, SpecialCases.inputKeys)
        val newSelector =
          if (requiresTaskValue) SbtSelectors.taskValue
          else if (requiresEvaluated) SbtSelectors.evaluated
          else SbtSelectors.value
        TokenPatch.AddRight(rhs.tokens.last, newSelector)
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

  def rewrite[T](ctx: RewriteCtx[T]): Seq[Patch] = {
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
