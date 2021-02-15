package typeless.interpreter

import miksilo.editorParser.parsers.SourceElement
import miksilo.editorParser.parsers.editorParsers.OffsetPointerRange
import typeless.ast.CallElement

// TODO fix calls of this function
case class NativeCallFailed(expectedValues: Seq[Value]) extends ExceptionResult

object NativeElement extends SourceElement {
  override def rangeOption: Option[OffsetPointerRange] = None
}

object FakeSource extends SourceElement {
  override def rangeOption: Option[OffsetPointerRange] = None
}

class Assert extends ObjectValue with ClosureLike {
  members.put("strictEqual", AssertStrictEqual)

  val fakeBoolean = new BooleanValue(true)
  fakeBoolean.createdAt = FakeSource

  override def evaluate(context: Context, argumentValues: collection.Seq[Value]): ExpressionResult = {
    if (argumentValues.isEmpty) {
      return NativeCallFailed(Seq(fakeBoolean))
    }
    argumentValues.head match {
      case booleanValue: BooleanValue if booleanValue.value =>
        new UndefinedValue()
      case _ =>
        AssertEqualFailure(context.callStack, context.configuration.file,
          context.currentFrame().call.asInstanceOf[CallElement], argumentValues.head, fakeBoolean)
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

object ArrayReduce extends ClosureLike {

  override def evaluate(context: Context, argumentValues: collection.Seq[Value]): ExpressionResult = {
    if (argumentValues.isEmpty)
      return NativeCallFailed(Seq.empty)

    val hasSeed = argumentValues.size > 1
    val reduceFunctionValue = argumentValues.head
    if (!reduceFunctionValue.isInstanceOf[ClosureLike]) {
      return TypeError(context.callStack, reduceFunctionValue.createdAt, "that can be called", reduceFunctionValue)
    }
    val reduceFunction = reduceFunctionValue.asInstanceOf[ClosureLike]

    val thisValue = context.getThis
    if (!thisValue.isInstanceOf[ArrayValue]) {
      return TypeError(context.callStack, reduceFunctionValue.createdAt, "that has elements", reduceFunctionValue)
    }
    val array = thisValue.asInstanceOf[ArrayValue]

    val startIndex = if (hasSeed) 0 else 1
    val seed = if (hasSeed) argumentValues(1) else array.get(0)
    val length = array.length

    var accumulator = seed
    for(index <- startIndex.until(length)) {
      val element = array.get(index)
      val result = context.evaluateClosure(context.currentFrame().call.asInstanceOf[CallElement], reduceFunction, Seq[Value](accumulator, element))
      result match {
        case e: ExceptionResult => return e
        case value: Value => accumulator = value
      }
    }
    accumulator
  }
}

object AssertStrictEqual extends ClosureLike {

  override def evaluate(context: Context, argumentValues: collection.Seq[Value]): ExpressionResult = {
    if (argumentValues.length != 2) {
      return NativeCallFailed(Seq.empty)
    }
    val actual = argumentValues(0)
    val expected = argumentValues(1)
    if (!Value.strictEqual(actual, expected)) {
      return AssertEqualFailure(context.callStack, context.configuration.file,
        context.currentFrame().call.asInstanceOf[CallElement], actual, expected)
    }
    new UndefinedValue()
  }
}