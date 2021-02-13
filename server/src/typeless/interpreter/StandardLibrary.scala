package typeless.interpreter

import miksilo.editorParser.parsers.SourceElement
import miksilo.editorParser.parsers.editorParsers.OffsetPointerRange
import miksilo.lspprotocol.lsp.Diagnostic

case class NativeCallFailed(expectedValues: Seq[Value]) extends ExceptionResult

object NativeElement extends SourceElement {
  override def rangeOption: Option[OffsetPointerRange] = None
}

object FakeSource extends SourceElement {
  override def rangeOption: Option[OffsetPointerRange] = None
}

class Assert extends ObjectValue with ClosureLike {
  members.put("strictEqual", AssertStrictEqual)

  override def evaluate(context: Context, argumentValues: collection.Seq[Value]): ExpressionResult = {
    argumentValues.head match {
      case booleanValue: BooleanValue if booleanValue.value =>
        new UndefinedValue()
      case _ =>
        val fakeBoolean = new BooleanValue(true)
        fakeBoolean.createdAt = FakeSource
        AssertEqualFailure(context.configuration.file, argumentValues.head, fakeBoolean)
    }
  }
}

object StandardLibrary {
  def createState(): Scope = {
    val result = new Scope()
    result.declare("assert", new Assert())
    result
  }
}

class NativeException(message: String) extends UserExceptionResult {
  override def canBeModified: Boolean = true

  override def toDiagnostic: Diagnostic = new Diagnostic(null, severity = Some(1), message = message)
}

object AssertStrictEqual extends ClosureLike {
  override def evaluate(context: Context, argumentValues: collection.Seq[Value]): ExpressionResult = {
    if (argumentValues.length != 2) {
      return new NativeException("strictEqual must be called with 2 arguments")
    }
    val actual = argumentValues(0)
    val expected = argumentValues(1)
    if (!Value.strictEqual(actual, expected)) {
      return AssertEqualFailure(context.configuration.file, actual, expected)
    }
    new UndefinedValue()
  }
}