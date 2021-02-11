package typeless.ast

import miksilo.editorParser.parsers.SourceElement
import miksilo.editorParser.parsers.editorParsers.OffsetPointerRange
import miksilo.languageServer.core.language.FileElement
import typeless.interpreter.{BooleanValue, Context, ExceptionResult, ExpressionResult, FindScope, QueryException, ReturnedValue, Scan, ScopeInformation, StatementResult, TypeError, Value, VoidResult}
import typeless.interpreter

case class ExpressionStatement(range: OffsetPointerRange, expression: Expression) extends Statement {
  override def evaluate(context: Context): StatementResult = {
    context.evaluateExpression(expression) match {
      case _: Value => VoidResult
      case exceptionResult: ExceptionResult => exceptionResult
    }
  }

  override def childElements: Seq[SourceElement] = {
    Seq(expression)
  }
}

trait NameLike extends SourceElement {
  val name: String

  def addReference(context: Context, result: ExpressionResult): Unit = {
    for {
      value <- result.toValue
      definedAt <- value.definedAt
      references <- context.configuration.mode match {
        case scan: Scan => Some(scan)
        case _ => None
      }
    } yield {
      references.addReference(this, definedAt)
    }
  }
}

class Name(val range: OffsetPointerRange, val name: String) extends FileElement with NameLike

case class Declaration(range: OffsetPointerRange, name: Name, value: Expression) extends Statement {
  override def evaluate(context: Context): StatementResult = {
    val evaluated = context.evaluateExpression(value)
    evaluated match {
      case value: Value =>
        context.declareWith(name, name.name, value)
        interpreter.VoidResult
      case statementResult: StatementResult => statementResult
    }
  }

  override def childElements: Seq[SourceElement] = {
    Seq(name, value)
  }
}

object Statement {
  def evaluateBody(context: Context, statements: Seq[Statement]): StatementResult = {
    for(statement <- statements) {
      if (context.configuration.mode == FindScope(statement)) {
        return ScopeInformation(context.scope)
      }
      statement.evaluate(context) match {
        case interpreter.VoidResult =>
        case returnedValue: ReturnedValue => return returnedValue
        case query: QueryException =>
          return query
        case exceptionResult: ExceptionResult =>
          if (!context.configuration.skipErrors) {
           return exceptionResult
          }
      }
    }
    interpreter.VoidResult
  }
}

case class IfStatement(range: OffsetPointerRange,
                       condition: Expression,
                       thenBody: Seq[Statement],
                       elseBody: Seq[Statement]) extends Statement {
  override def evaluate(context: Context): StatementResult = {
    val conditionResult = context.evaluateExpression(condition)
    conditionResult match {
      case conditionValue: BooleanValue =>
        val result = if (conditionValue.value) {
          Statement.evaluateBody(context, thenBody)
        } else {
          Statement.evaluateBody(context, elseBody)
        }
        result
      case conditionValue: Value => TypeError(this, "that's true or false", conditionValue)
      case e: ExceptionResult => e
    }
  }

  override def childElements: Seq[SourceElement] = {
    Seq(condition) ++ thenBody ++ elseBody
  }
}

trait Statement extends FileElement {
  def evaluate(context: Context): StatementResult
}