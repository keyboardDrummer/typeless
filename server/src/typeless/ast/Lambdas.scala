package typeless.ast

import miksilo.editorParser.parsers.SourceElement
import miksilo.editorParser.parsers.editorParsers.OffsetPointerRange
import miksilo.languageServer.core.language.FileElement
import miksilo.lspprotocol.lsp.{Diagnostic, FileRange, RelatedInformation}
import typeless.interpreter._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class Argument(val range: OffsetPointerRange, val name: String, varArgs: Boolean) extends FileElement with NameLike {

}

class Lambda(val range: OffsetPointerRange, val arguments: Vector[Argument], val body: Vector[Statement]) extends Expression {
  override def evaluate(context: Context): ExpressionResult = {
    new Closure(this, context.scope)
  }

  override def childElements: Seq[SourceElement] = {
    arguments ++ body
  }
}

class IncorrectNativeCall(file: String, exception: NativeCallFailed, call: Call, argumentValues: collection.Seq[Value])
  extends UserExceptionResult {
  override def canBeModified: Boolean = false

  override def toDiagnostic: Diagnostic = {
    val values = argumentValues.map(a => a.represent()).reduce((left, right) => left + ", " + right)
    // Consider using the exception expected arguments
    val message = s"Call failed with arguments '$values'."

    val related = argumentValues.map(value => value.toRelatedInformation(file))
    Diagnostic(call.rangeOption.get.toSourceRange, Some(1), message, relatedInformation = related.toSeq)
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

  // TODO consider allowing this to be modified and changing the currentClosureCanBeWrong behavior in Call
  override def canBeModified: Boolean = false
}

class Call(val range: OffsetPointerRange, target: Expression, arguments: Vector[Expression]) extends Expression {

  override def childElements: Seq[SourceElement] = {
    Seq(target) ++ arguments
  }

  override def evaluate(context: Context): ExpressionResult = {
    val targetResult = context.evaluateExpression(target)

    val argumentResults = arguments.map(argument => context.evaluateExpression(argument))
    val argumentValues = ArrayBuffer.empty[Value]
    for(argumentResult <- argumentResults) {
      argumentResult match {
        case argumentValue: Value => argumentValues.addOne(argumentValue)
        case query: QueryException => return query
        case _ =>
          if (!context.skipErrors)
            return argumentResult
      }
    }

    targetResult match {
      case closure: ClosureLike =>

        if (context.callStack.length > context.configuration.maxCallDepth) {
          return MaxCallDepthReached(this)
        }
        context.callStack.addOne(closure)
        val result = evaluateClosure(context, argumentValues, closure)
        context.callStack.remove(context.callStack.length - 1)
        val modifiedResult = result match {
          case native: NativeCallFailed =>
            val currentClosureCanBeWrong = !context.isClosureCorrect(context.callStack.last)
            if (currentClosureCanBeWrong) {
              new IncorrectNativeCall(context.configuration.file, native, this, argumentValues)
            } else {
              native
            }

          case exception: UserExceptionResult =>
            val currentClosureCanBeWrong = !context.isClosureCorrect(context.callStack.last)
            if (exception.canBeModified && context.isClosureCorrect(closure) && currentClosureCanBeWrong) {
              new CorrectCallGaveException(context.configuration.file, exception, this, closure, argumentValues)
            }
            else {
              exception
            }
          case _ => result
        }
        modifiedResult
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

class ReturnStatement(val range: OffsetPointerRange, expression: Expression) extends Statement {
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