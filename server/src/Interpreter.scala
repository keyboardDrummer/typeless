import miksilo.editorParser.parsers.SourceElement
import miksilo.languageServer.core.language.{Phase, SourcePathFromElement}
import miksilo.languageServer.core.smarts.FileDiagnostic
import miksilo.lspprotocol.lsp.Diagnostic

import scala.collection.mutable

trait StatementResult {
  def toExpressionResult(): ExpressionResult
}


case class ReturnedValue(value: Value) extends StatementResult {
  override def toExpressionResult(): ExpressionResult = value
}



trait ExpressionResult {
}

trait ExceptionResult extends ExpressionResult with StatementResult {
  def element: SourceElement
  def message: String

  def toDiagnostic: Diagnostic = {
    Diagnostic(element.rangeOption.get.toSourceRange, Some(1), message)
  }

  override def toExpressionResult(): ExpressionResult = this
}

case class UndefinedMemberAccess(element: SourceElement, name: String, value: Value) extends ExceptionResult {
  override def message: String = s"The member '$name' is not available on value '${value.represent()}'"
}

trait CatchableExceptionResult extends ExceptionResult

case class ReferenceError(element: SourceElement, name: String) extends CatchableExceptionResult {
  override def message: String = s"Variable $name was accessed but is not defined"
}
case class TypeError(element: SourceElement, expected: String, value: Value) extends CatchableExceptionResult {
  override def message: String = s"Expected value of type $expected but got '${value.represent()}'"
}


class IntValue(val value: Int) extends Value {
  override def represent(): String = value.toString
}

class UndefinedValue extends Value {
}

class ObjectValue(var members: mutable.Map[String, Value] = mutable.Map.empty) extends Value {

  override def getMember(name: String): Option[Value] = {
    members.get(name)
  }

  override def setMember(name: String, value: Value) = {
    members.put(name, value)
  }

  override def represent(): String = "Object"
}

object Value {
  def strictEqual(first: Value, second: Value): Boolean = {
    if (first == second)
      return true

    (first, second) match {
      case (firstInt: IntValue, secondInt: IntValue) if firstInt.value == secondInt.value => true
      case _ => false
    }

  }
}


trait Value extends ExpressionResult {
  def getMember(name: String): Option[Value] = None
  def setMember(name: String, value: Value): Unit =
    ???

  var definedAt: Option[SourceElement] = None
  var createdAt: SourceElement = null
  var documentation: Option[String] = None

  def represent(): String = "some value"
}

object Void extends StatementResult {
  override def toExpressionResult(): ExpressionResult = new UndefinedValue()
}

class Context(val allowUndefinedPropertyAccess: Boolean,
              throwAtElementResult: Option[SourceElement] = None,
              val state: Scope = new Scope()) {

  var _this: ObjectValue = null

  def setThis(value: ObjectValue): Unit = _this = value
  def getThis(): ObjectValue = _this

  def withState(state: Scope): Context = {
    val result = new Context(allowUndefinedPropertyAccess, state = state)
    result.setThis(_this)
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
    result
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

class Scope(parentOption: Option[Scope] = None) {

  def nest(): Scope = new Scope(Some(this))

  var environment: mutable.HashMap[String, Value] = mutable.HashMap.empty

  def declare(name: String, value: Value): Unit = {
    if (environment.contains(name)) {
      ???
    }
    environment.put(name, value)
  }

  def assign(name: String, value: Value): Unit = {
    if (environment.contains(name)) {
      environment.put(name, value)
    } else {
      parentOption.fold(???)(parent => parent.assign(name, value))
    }
  }

  def get(element: SourceElement, name: String): ExpressionResult = {
    environment.getOrElse[ExpressionResult](name, {
      parentOption.fold[ExpressionResult](ReferenceError(element, name))(parent => parent.get(element, name))
    })
  }
}

object InterpreterPhase {
  val phase = Phase("interpreter", "where the interpreting happens", compilation => {
    val program = compilation.program.asInstanceOf[SourcePathFromElement].sourceElement.asInstanceOf[JavaScriptFile]
    val defaultState = StandardLibrary.createState()
    val context = new Context(false, state = defaultState)
    val result = program.evaluate(context)
    result match {
      case e: ExceptionResult =>
        compilation.diagnostics += FileDiagnostic("uri", e.toDiagnostic)
      case _ =>
    }

    val rootEnvironment = context.state.environment
    val tests = rootEnvironment.filter(s => {
      if (!s._1.endsWith("Test")) {
        false
      } else {
        s._2 match {
          case closure: Closure if closure.lambda.arguments.isEmpty => true
          case _ => false
        }
      }
    })

    tests.foreach(test => {
      val result = test._2.asInstanceOf[Closure].evaluate(context, Seq.empty)
      result match {
        case e: ExceptionResult =>
          compilation.diagnostics += FileDiagnostic(compilation.rootFile.get, e.toDiagnostic)
        case _ =>
      }
    })

  })
}