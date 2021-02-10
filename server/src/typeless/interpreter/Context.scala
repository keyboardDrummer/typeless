package typeless.interpreter

import miksilo.editorParser.parsers.SourceElement
import typeless.ast.{Expression, NameLike, ScopeInformation, StringValue}

class References(var references: Map[NameLike, SourceElement] = Map.empty) {

}

class Context(val allowUndefinedPropertyAccess: Boolean,
              var functionCorrectness: Option[FunctionCorrectness],
              var runningTests: Set[Closure],
              var throwAtElementResult: Option[SourceElement],
              var collectScopeAtElement: Option[SourceElement],
              var referencesOption: Option[References],
              val scope: Scope) {


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
  def getThis: ObjectValue = _this.head

  def withScope(newScope: Scope): Context = {
    val result = new Context(allowUndefinedPropertyAccess, functionCorrectness,
      runningTests, throwAtElementResult, collectScopeAtElement, referencesOption, newScope)
    result._this = _this
    result
  }

  def get(element: SourceElement, name: String): ExpressionResult = scope.get(element, name)

  def declareWith(source: SourceElement, name: String, value: Value): Unit = {
    value.definedAt = Some(source)
    scope.declare(name, value)
  }

  def declare(source: SourceElement, name: String): Unit = {
    val defaultValue = new UndefinedValue()
    defaultValue.definedAt = Some(source)
    scope.declare(name, defaultValue)
  }

  def evaluateString(expression: Expression): ExpressionResult = {
    val result = evaluateExpression(expression)
    result match {
      case value: StringValue => value
      case value: Value => TypeError(expression, "string", value)
      case e: ExceptionResult => e
    }
  }

  def evaluateObjectValue(expression: Expression): ExpressionResult = {
    val result = evaluateExpression(expression)
    result match {
      case value: ObjectValue => value
      case value: Value => TypeError(expression, "object", value)
      case e: ExceptionResult => e
    }
  }

  def skipErrors: Boolean = collectScopeAtElement.nonEmpty || throwAtElementResult.nonEmpty

  def evaluateExpression(expression: Expression): ExpressionResult = {
    if (collectScopeAtElement.contains(expression)) {
      return ScopeInformation(scope)
    }

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