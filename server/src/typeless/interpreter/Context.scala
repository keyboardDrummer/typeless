package typeless.interpreter

import miksilo.editorParser.parsers.SourceElement
import typeless._
import typeless.ast.Expression

class Context(val allowUndefinedPropertyAccess: Boolean,
              var functionCorrectness: Option[FunctionCorrectness],
              var runningTests: Set[Closure],
              var throwAtElementResult: Option[SourceElement],
              val state: Scope) {


  def isRunningTest(test: Closure): Boolean = {
    runningTests.contains(test)
  }


  def runTest(test: Closure): ExpressionResult = {
    runningTests += test
    val result = test.evaluate(this, Seq.empty)
    runningTests -= test
    result
  }


  def isClosureCorrect(closureLike: ClosureLike): Boolean = {
    closureLike match {
      case closure: Closure =>
        functionCorrectness.fold(true)(c => c.isClosureCorrect(this, closure))
      case _ => true
    }
  }

  var _this: Option[ObjectValue] = None

  def setThis(value: ObjectValue): Unit = _this = Some(value)
  def getThis(): ObjectValue = _this.head

  def withState(newState: Scope): Context = {
    val result = new Context(allowUndefinedPropertyAccess, functionCorrectness,
      runningTests, throwAtElementResult, newState)
    result._this = _this
    result
  }

  def get(element: SourceElement, name: String): ExpressionResult = state.get(element, name)

  def declareWith(source: SourceElement, name: String, value: Value): Unit = {
    value.definedAt = Some(source)
    state.declare(name, value)
  }

  def declare(source: SourceElement, name: String): Unit = {
    val defaultValue = new UndefinedValue()
    defaultValue.definedAt = Some(source)
    state.declare(name, defaultValue)
  }

  def evaluateExpression(expression: Expression): ExpressionResult = {
    val result = expression.evaluate(this)
    result match {
      case value: Value if value.createdAt == null => value.createdAt = expression
      case _ =>
    }
    if (throwAtElementResult.contains(expression)) {
      new ReturnInformationWithThrow(result)
    } else {
      result
    }
  }

  def assign(element: SourceElement, name: String, newValue: Value): ExpressionResult = {
    get(element, name) match {
      case value: Value =>
        newValue.definedAt = value.definedAt
        newValue.documentation = value.documentation
        value
      case error =>
        // TODO change error?
        error
    }
  }
}

case class ReturnInformationWithThrow(result: ExpressionResult) extends ExceptionResult {

}