package typeless.ast

import miksilo.editorParser.parsers.SourceElement
import miksilo.editorParser.parsers.editorParsers.OffsetPointerRange
import miksilo.languageServer.core.language.FileElement
import typeless.interpreter.{Closure, ClosureLike, Context, DiagnosticExceptionResult, ExceptionResult, ExpressionResult, QueryException, ReturnedValue, StatementResult, TypeError, Value}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer


case class Argument(range: OffsetPointerRange, name: String, varArgs: Boolean) extends FileElement with NameLike {

}

case class Lambda(range: OffsetPointerRange, arguments: Vector[Argument], body: Vector[Statement]) extends Expression {
  override def evaluate(context: Context): ExpressionResult = {
    new Closure(this, context.scope)
  }

  override def childElements: Seq[SourceElement] = {
    arguments ++ body
  }
}

class CorrectCallGaveException(exception: ExceptionResult, call: Call,
                               closure: ClosureLike, argumentValues: collection.Seq[Value])
  extends DiagnosticExceptionResult {
  override def element: SourceElement = call

  override def message: String = {
    val values = argumentValues.map(a => a.represent()).reduce((left, right) => left + ", " + right)
    s"This function call failed with arguments '$values'."
  }
}

case class Call(range: OffsetPointerRange, target: Expression, arguments: Vector[Expression]) extends Expression {

  override def childElements: Seq[SourceElement] = {
    Seq(target) ++ arguments
  }

  override def evaluate(context: Context): ExpressionResult = {
    val targetResult = context.evaluateExpression(target)

    val argumentResults = arguments.map(argument => context.evaluateExpression(argument))
    val argumentValues = mutable.ArrayBuffer.empty[Value]
    for(argumentResult <- argumentResults) {
      argumentResult match {
        case argumentValue: Value => argumentValues.addOne(argumentValue)
        case query: QueryException => return query
        case _ => if (!context.skipErrors) return argumentResult
      }
    }

    targetResult match {
      case closure: ClosureLike =>


        evaluateClosure(context, argumentValues, closure) match {
          case exception: ExceptionResult =>
            if (context.isClosureCorrect(closure)) {
              new CorrectCallGaveException(exception, this, closure, argumentValues)
            }
            else {
              exception
            }
          case result => result
        }
      case targetValue: Value =>
        TypeError(target, "closure", targetValue)
      case _ => targetResult
    }
  }

  def evaluateClosure(context: Context, argumentValues: ArrayBuffer[Value], closure: ClosureLike): ExpressionResult = {
    closure.evaluate(context, argumentValues)
  }
}

case class ReturnStatement(range: OffsetPointerRange, expression: Expression) extends Statement {
  override def evaluate(context: Context): StatementResult = {
    context.evaluateExpression(expression) match {
      case value: Value => ReturnedValue(value)
      case result: ExceptionResult => result
    }
  }

  override def childElements: Seq[SourceElement] = {
    Seq(expression)
  }
}