package typeless.ast

import miksilo.editorParser.parsers.SourceElement
import miksilo.editorParser.parsers.editorParsers.OffsetPointerRange
import miksilo.languageServer.core.language.FileElement
import typeless.interpreter.{BooleanValue, Context, ExceptionResult, ExpressionResult, ReturnedValue, StatementResult, TypeError, Value}
import typeless.interpreter

case class ExpressionStatement(range: OffsetPointerRange, expression: Expression) extends Statement {
  override def evaluate(context: Context): StatementResult = {
    context.evaluateExpression(expression) match {
      case _: Value => interpreter.Void
      case statementResult: StatementResult => statementResult
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
      references <- context.referencesOption
    } yield {
      references.referenceToDefinition += this -> definedAt
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
        interpreter.Void
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
      if (context.collectScopeAtElement.contains(statement)) {
        return ScopeInformation(context.scope)
      }
      statement.evaluate(context) match {
        case interpreter.Void =>
        case returnedValue: ReturnedValue => return returnedValue
        case query: QueryException => return query
        case exceptionResult: ExceptionResult =>
          if (context.collectScopeAtElement.isEmpty && context.throwAtElementResult.isEmpty) {
           return exceptionResult
          }
      }
    }
    interpreter.Void
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
      case conditionValue: Value => TypeError(this, "boolean", conditionValue)
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