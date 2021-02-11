package typeless.ast

import miksilo.editorParser.parsers.SourceElement
import miksilo.editorParser.parsers.editorParsers.OffsetPointerRange
import miksilo.languageServer.core.language.FileElement
import miksilo.lspprotocol.lsp.{Diagnostic, FileRange, RelatedInformation}
import typeless.interpreter._
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

// TODO remove file argument
class CorrectCallGaveException(file: String, exception: UserExceptionResult, call: Call,
                               closure: ClosureLike, argumentValues: collection.Seq[Value])
  extends UserExceptionResult {

  override def toDiagnostic: Diagnostic = {
    val values = argumentValues.map(a => a.represent()).reduce((left, right) => left + ", " + right)
    val message = s"Function call failed with arguments '$values'"
    val innerDiagnostic = exception.toDiagnostic
    val relatedInfo = RelatedInformation(FileRange(file, innerDiagnostic.range), innerDiagnostic.message)
    Diagnostic(call.rangeOption.get.toSourceRange, Some(1), message, relatedInformation = Seq(relatedInfo))
  }

  override def canBeModified: Boolean = true
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

        if (context.callDepth > context.configuration.maxCallDepth) {
          return MaxCallDepthReached(this)
        }
        context.callDepth += 1

        val result = evaluateClosure(context, argumentValues, closure) match {
          case exception: UserExceptionResult =>
            if (exception.canBeModified && context.isClosureCorrect(closure)) {
              new CorrectCallGaveException(context.configuration.file, exception, this, closure, argumentValues)
            }
            else {
              exception
            }
          case result => result
        }
        context.callDepth -= 1
        result
      case targetValue: Value =>
        TypeError(target, "that can be called", targetValue)
      case _ => targetResult
    }
  }

  def evaluateClosure(context: Context, argumentValues: ArrayBuffer[Value], closure: ClosureLike): ExpressionResult = {
    closure.evaluate(context, argumentValues)
  }
}

case class MaxCallDepthReached(element: SourceElement) extends SimpleExceptionResult {
  override def message: String = "Call takes too long for a test"
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