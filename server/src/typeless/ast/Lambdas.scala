package typeless.ast

import miksilo.editorParser.parsers.SourceElement
import miksilo.editorParser.parsers.editorParsers.OffsetPointerRange
import miksilo.languageServer.core.language.FileElement
import miksilo.lspprotocol.lsp.{Diagnostic, FileRange, RelatedInformation}
import typeless.interpreter._

import scala.collection.mutable.ArrayBuffer

class Argument(val range: OffsetPointerRange, val name: String, val varArgs: Boolean) extends FileElement with NameLike {

}

case class FunctionDocumentation(description: String, parameters: Map[String, String], returnValue: Option[String])

class Lambda(val range: OffsetPointerRange, val arguments: Vector[Argument], val body: Vector[Statement],
             val nameOption: Option[String], val documentationOption: Option[FunctionDocumentation]) extends Expression {
  override def evaluate(context: Context): ExpressionResult = {
    new Closure(this, context.scope)
  }

  override def childElements: Seq[SourceElement] = {
    arguments ++ body
  }
}

case class IncorrectNativeCall(callStack: List[Frame], file: String, exception: NativeCallFailed, call: CallBase, argumentValues: collection.Seq[Value])
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
case class CorrectCallGaveException(callStack: List[Frame], file: String, exception: UserExceptionResult, call: CallBase,
                               closure: ClosureLike, argumentValues: collection.Seq[Value])
  extends UserExceptionResult {

  override def toDiagnostic: Diagnostic = {
    val values = argumentValues.map(a => a.represent()).mkString(", ")
    val message = s"Function call failed with arguments ($values)"
    val innerDiagnostic = exception.toDiagnostic
    val relatedInfo = RelatedInformation(FileRange(file, innerDiagnostic.range), innerDiagnostic.message)
    Diagnostic(call.rangeOption.get.toSourceRange, Some(1), message, relatedInformation = Seq(relatedInfo))
  }
}

class Call(range: OffsetPointerRange, target: Expression, arguments: Vector[Expression]) extends CallBase(range, target, arguments) {

  override def evaluate(context: Context): ExpressionResult = {
    context.lastDotAccessTarget = None
    super.evaluate(context)
  }

  override def evaluateClosure(context: Context, argumentValues: ArrayBuffer[Value], closure: ClosureLike): ExpressionResult = {
    context.lastDotAccessTarget.foreach(target => context.setThis(target))
    super.evaluateClosure(context, argumentValues, closure)
  }
}

class CallBase(val range: OffsetPointerRange, target: Expression, arguments: Vector[Expression]) extends Expression {

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

        val result = evaluateClosure(context, argumentValues, closure)
        val modifiedResult = result match {
          case native: NativeCallFailed =>
            val currentClosureCanBeWrong = !context.isCurrentContextTrusted
            if (currentClosureCanBeWrong) {
              IncorrectNativeCall(context.callStack, context.configuration.file, native, this, argumentValues)
            } else {
              native
            }

          case exception: UserExceptionResult =>
            val exceptionInTrustedContext = context.isClosureTrusted(exception.callStack.head.closure)
            if (exception.canBeModified && exceptionInTrustedContext && !context.isCurrentContextTrusted) {
              CorrectCallGaveException(context.callStack, context.configuration.file, exception, this, closure, argumentValues)
            }
            else {
              exception
            }
          case _ => result
        }
        modifiedResult
      case targetValue: Value =>
        TypeError(context.callStack, target, "that can be called", targetValue)
      case _ => targetResult
    }
  }

  def evaluateClosure(context: Context, argumentValues: ArrayBuffer[Value], closure: ClosureLike): ExpressionResult = {
    context.evaluateClosure(this, closure, argumentValues)
  }
}

case class MaxCallDepthReached(callStack: List[Frame]) extends SimpleExceptionResult {
  override def message: String = "Call takes too long for a test"

  override def element: SourceElement = callStack.head.call
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