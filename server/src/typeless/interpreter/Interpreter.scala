package typeless.interpreter

import miksilo.editorParser.parsers.SourceElement
import miksilo.languageServer.core.language.{Compilation, Phase, SourcePathFromElement}
import miksilo.languageServer.core.smarts.FileDiagnostic
import miksilo.lspprotocol.lsp.Diagnostic
import typeless.ast.{JavaScriptFile, Lambda, Statement}
import typeless.server.JavaScriptCompilation

import scala.collection.mutable

trait StatementResult {
  def toExpressionResult(): ExpressionResult
}


case class ReturnedValue(value: Value) extends StatementResult {
  override def toExpressionResult(): ExpressionResult = value
}



// TODO turn into a monad.
trait ExpressionResult {
  def flatMap(f: Value => ExpressionResult): ExpressionResult
}

trait DiagnosticExceptionResult extends ExceptionResult {
  def element: SourceElement
  def message: String

  def toDiagnostic: Diagnostic = {
    Diagnostic(element.rangeOption.get.toSourceRange, Some(1), message)
  }
}

trait ExceptionResult extends ExpressionResult with StatementResult {

  override def flatMap(f: Value => ExpressionResult): ExpressionResult = this

  override def toExpressionResult(): ExpressionResult = this
}

case class UndefinedMemberAccess(element: SourceElement, name: String, value: Value) extends DiagnosticExceptionResult {
  override def message: String = s"The member '$name' is not available on value '${value.represent()}'"
}

trait CatchableExceptionResult extends ExceptionResult

case class ReferenceError(element: SourceElement, name: String)
  extends DiagnosticExceptionResult with CatchableExceptionResult {
  override def message: String = s"Variable $name was accessed but is not defined"
}
case class TypeError(element: SourceElement, expected: String, value: Value)
  extends DiagnosticExceptionResult with CatchableExceptionResult {
  override def message: String = s"Expected value of type $expected but got '${value.represent()}'"
}

class PrimitiveValue[T](val value: T) extends Value {
  override def represent(): String = value.toString
}

class IntValue(value: Int) extends PrimitiveValue[Int](value) {
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

  override def flatMap(f: Value => ExpressionResult): ExpressionResult = {
    f(this)
  }

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

class FunctionCorrectness(functionsWithTests: Map[Closure, Closure]) {
  var functionCorrectness = Map.empty[Closure, Boolean]

  def isClosureCorrect(context: Context, closure: Closure): Boolean = {
    functionCorrectness.get(closure) match {
      case Some(correct) => correct
      case None =>
        val testOption = functionsWithTests.get(closure)
        testOption.fold(false)(test => {
          if (context.isRunningTest(test)) {
            false
          } else {
            val testPassed = context.runTest(test) match {
              case _: ExceptionResult => false
              case _ => true
            }
            functionCorrectness += closure -> testPassed
            testPassed
          }
        })
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

class Closure(val lambda: Lambda, val state: Scope) extends Value with ClosureLike {

  def evaluate(context: Context, argumentValues: collection.Seq[Value]): ExpressionResult = {
    val newContext = context.withState(state.nest())
    lambda.arguments.zip(argumentValues).foreach(t => {
      newContext.declareWith(t._1, t._1.name, t._2)
    })
    Statement.evaluateBody(newContext, lambda.body).toExpressionResult()
  }
}

object InterpreterPhase {

  val phase = Phase("interpreter", "where the interpreting happens", interpret)

  def interpret(compilation: Compilation): Unit = {

    val javaScriptCompilation = compilation.asInstanceOf[JavaScriptCompilation]
    val program = compilation.program.asInstanceOf[SourcePathFromElement].sourceElement.asInstanceOf[JavaScriptFile]
    val defaultState = StandardLibrary.createState()
    val context = new Context(false, None, Set.empty, None, defaultState)
    val result = program.evaluate(context)
    result match {
      case e: DiagnosticExceptionResult =>
        compilation.diagnostics += FileDiagnostic("uri", e.toDiagnostic)
      case _ =>
    }

    val rootEnvironment = context.state.environment
    val functions: Map[String, Closure] = rootEnvironment.flatMap(s => {
      s._2 match {
        case closure: Closure => Seq(s._1 -> closure)
        case _ => Seq.empty
      }
    }).toMap
    val testKeyword = "Test"
    val tests: Map[String, Closure] = functions.flatMap(s => {
      if (s._1.endsWith(testKeyword) && s._2.lambda.arguments.isEmpty) {
        Seq(s._1 -> s._2)
      } else {
        Seq.empty
      }
    })
    javaScriptCompilation.tests = tests
    val functionsWithTests: Map[Closure, Closure] = tests.flatMap(test => {
      functions.get(test._1.dropRight(testKeyword.length)).map(f => (f, test._2)).toIterable
    })

    context.functionCorrectness = Some(new FunctionCorrectness(functionsWithTests))
    javaScriptCompilation.context = context

    tests.foreach(test => {
      val result = context.runTest(test._2)
      result match {
        case e: DiagnosticExceptionResult =>
          compilation.diagnostics += FileDiagnostic(compilation.rootFile.get, e.toDiagnostic)
        case _ =>
      }
    })
  }
}


class BooleanValue(value: Boolean) extends PrimitiveValue[Boolean](value) {
}


trait ClosureLike extends Value {
  def evaluate(context: Context, argumentValues: collection.Seq[Value]): ExpressionResult
}


case class AssertEqualFailure(actual: Value, expected: Value) extends DiagnosticExceptionResult {
  override def element: SourceElement = actual.createdAt

  override def message: String = s"The value '${expected.represent()}' was expected but it was '${actual.represent()}'."

  override def toDiagnostic: Diagnostic = {
    // TODO add related information linking to assertion.
    super.toDiagnostic
  }
}