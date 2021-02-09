import miksilo.editorParser.parsers.SourceElement
import miksilo.editorParser.parsers.editorParsers.OffsetPointerRange
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
  override def message: String = s"Expected value of typed $expected but got ${value.represent()}"
}

class IntValue(value: Int) extends Value {
  override def represent(): String = value.toString
}

class UndefinedValue extends Value {

}

class ObjectValue(var members: Map[String, Value] = Map.empty) extends Value {

  override def getMember(name: String): Option[Value] = {
    members.get(name)
  }
}

class Value extends ExpressionResult {

  def getMember(name: String): Option[Value] = None

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
              val state: State = new State()) {

  def withState(state: State): Context = {
    new Context(allowUndefinedPropertyAccess, state = state)
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
      case value: Value => value.createdAt = expression
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

class State {

  var environment: List[mutable.HashMap[String, Value]] = List(mutable.HashMap.empty)

  def declare(name: String, value: Value): Unit = {
    if (environment.head.contains(name)) {
      ???
    }
    environment.head.put(name, value)
  }

  def assign(name: String, value: Value): Unit = {
    for(layer <- environment) {
      if (layer.contains(name)) {
        layer.put(name, value)
      } else {
        ???
      }
    }
  }

  def get(element: SourceElement, name: String): ExpressionResult = {
    for(layer <- environment) {
      layer.get(name) match {
        case Some(value) => return value
        case _ =>
      }
    }
    ReferenceError(element, name)
  }
}

object InterpreterPhase {
  val phase = Phase("interpreter", "where the interpreting happens", compilation => {
    val program = compilation.program.asInstanceOf[SourcePathFromElement].sourceElement.asInstanceOf[JavaScriptFile]
    val context = new Context(false)
    val result = program.evaluate(context)
    result match {
      case e: ExceptionResult =>
        compilation.diagnostics += FileDiagnostic("uri", e.toDiagnostic)
      case _ =>
    }

    val rootEnvironment = context.state.environment.head
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