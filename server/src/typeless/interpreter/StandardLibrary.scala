package typeless.interpreter

import miksilo.editorParser.parsers.SourceElement
import miksilo.editorParser.parsers.editorParsers.OffsetPointerRange

case class NativeCallFailed(expectedValues: Seq[Value]) extends DiagnosticExceptionResult {

  override def message: String = "assertion failed"

  override def element: SourceElement = NativeElement
}

object NativeElement extends SourceElement {
  override def rangeOption: Option[OffsetPointerRange] = None
}

class Assert extends ObjectValue with ClosureLike {
  members.put("strictEqual", AssertStrictEqual)

  override def evaluate(context: Context, argumentValues: collection.Seq[Value]): ExpressionResult = {
    argumentValues.head match {
      case booleanValue: BooleanValue if booleanValue.value =>
        new UndefinedValue()
      case _ => NativeCallFailed(Seq(new BooleanValue(true)))
    }
  }
}

object StandardLibrary {
  def createState() = {
    val result = new Scope()
    result.declare("assert", new Assert())
    result
  }
}

object AssertStrictEqual extends ClosureLike {
  override def evaluate(context: Context, argumentValues: collection.Seq[Value]): ExpressionResult = {
    val actual = argumentValues(0)
    val expected = argumentValues(1)
    if (!Value.strictEqual(actual, expected)) {
      return AssertEqualFailure(actual, expected)
    }
    new UndefinedValue()
  }
}