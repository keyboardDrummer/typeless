package typeless.interpreter

import miksilo.editorParser.parsers.SourceElement
import typeless.ast.{CallBase, Expression, Lambda, MaxCallDepthReached, NameLike, StringValue}

trait RunMode {
  def skipErrors: Boolean
}
case class FindValue(expression: SourceElement) extends RunMode {
  override def skipErrors = true
}
case class FindScope(element: SourceElement) extends RunMode {
  override def skipErrors = true
}

class References(val fromReference: Map[NameLike, NameLike]) {
  val fromDeclaration: Map[NameLike, Set[NameLike]] =
    fromReference.groupMapReduce(e => e._2)(e => Set(e._1))((a, b) => a ++ b)
}

class Scan extends RunMode {
  override def skipErrors = false

  private var _referenceToDefinition: Map[NameLike, NameLike] = Map.empty

  def addReference(reference: NameLike, definition: NameLike): Unit = {
    _referenceToDefinition += reference -> definition
  }

  def referenceToDefinition: Map[NameLike, NameLike] = _referenceToDefinition
}

case class RunConfiguration(file: String,
                            maxCallDepth: Int,
                            allowUndefinedPropertyAccess: Boolean,
                            var mode: RunMode) {

  def skipErrors: Boolean = mode.skipErrors

}

case class Frame(call: CallBase, closure: ClosureLike)
class Context(var configuration: RunConfiguration,
              private var _callStack: List[Frame],
              var functionCorrectness: Option[FunctionCorrectness],
              var runningTests: Set[Closure],
              val scope: Scope) {

  def callStack  = _callStack
  def currentFrame(): Frame = callStack.head

  var lastDotAccessTarget: Option[ObjectValue] = None

  def this(configuration: RunConfiguration, scope: Scope) = {
    this(configuration, List.empty, None, Set.empty, scope)
  }

  def evaluateClosure(call: CallBase, closure: ClosureLike, argumentValues: collection.Seq[Value]): ExpressionResult = {
    val newFrame = Frame(call, closure)
    if (callStack.length > configuration.maxCallDepth) {
      return MaxCallDepthReached(newFrame :: callStack)
    }
    _callStack ::= Frame(call, closure)
    val result = closure.evaluate(this, argumentValues)
    _callStack = _callStack.tail
    result
  }

  def withFreshCallStack(): Context = {
    new Context(configuration, List.empty, functionCorrectness, runningTests, scope)
  }

  def isRunningTest(test: Closure): Boolean = {
    runningTests.contains(test)
  }

  def runTest(test: Closure): ExpressionResult = {
    runningTests += test
    //callStack.addOne(test)
    val result = test.evaluate(this, Seq.empty)
    //callStack.remove(callStack.length - 1)
    runningTests -= test
    result
  }

  def isCurrentContextTrusted: Boolean = {
    if (callStack.isEmpty)
      false
    else {
      isClosureTrusted(callStack.head.closure)
    }
  }

  def isClosureTrusted(closureLike: ClosureLike): Boolean = {
    closureLike match {
      case closure: Closure =>
        // TODO traverse to highest ancestor lambda.
        val lambda = closure.lambda
        functionCorrectness.fold(true)(c => {
          c.isLambdaCorrect(this, lambda)
        })
      case _ => true
    }
  }

  var _this: Value = new IntValue(42)

  def setThis(value: ObjectValue): Unit = _this = value
  def getThis: Value = _this

  def withScope(newScope: Scope): Context = {
    val result = new Context(configuration, callStack, functionCorrectness, runningTests, newScope)
    result._this = _this
    result
  }

  def get(element: SourceElement, name: String): ExpressionResult = scope.get(this, element, name)

  def declareWith(source: NameLike, name: String, value: Value): Boolean = {
    value.definedAt = Some(source)
    scope.declare(name, value)
  }

  def declare(element: NameLike, name: String): Unit = {
    declareWith(element, name, new UndefinedValue)
  }

  def evaluateString(expression: Expression): ExpressionResult = {
    val result = evaluateExpression(expression)
    result match {
      case value: StringValue => value
      case value: Value => TypeError(callStack, expression, "that is text", value)
      case e: ExceptionResult => e
    }
  }

  def evaluateObjectValue(expression: Expression): ExpressionResult = {
    val result = evaluateExpression(expression)
    result match {
      case value: ObjectValue => value
      case value: Value =>
        TypeError(callStack, expression, "with properties", value)
      case e: ExceptionResult => e
    }
  }

  def skipErrors: Boolean = configuration.skipErrors

  def evaluateExpression(expression: Expression): ExpressionResult = {
    if (configuration.mode == FindScope(expression)) {
      return ScopeInformation(scope)
    }

    val result = expression.evaluate(this)
    result match {
      case value: Value if value.createdAt == null => value.createdAt = expression
      case _ =>
    }
    if (configuration.mode == FindValue(expression)) {
      ReturnInformationWithThrow(result)
    } else {
      result
    }
  }

  def assign(element: SourceElement, name: String, newValue: Value): ExpressionResult = {
    get(element, name) match {
      case value: Value =>
        newValue.definedAt = value.definedAt
        newValue.documentation = value.documentation
        scope.assign(name, newValue)
        value
      case error =>
        // TODO change error?
        error
    }
  }
}

trait QueryException extends ExceptionResult
case class ScopeInformation(scope: ScopeLike) extends QueryException

trait ScopeLike {
  def memberNames: Iterable[String]
  def getValue(member: String): Value
}

case class ReturnInformationWithThrow(result: ExpressionResult) extends QueryException