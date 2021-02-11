package typeless.interpreter

import miksilo.editorParser.parsers.SourceElement
import miksilo.languageServer.core.language.{Compilation, Phase, SourcePathFromElement}
import miksilo.languageServer.core.smarts.FileDiagnostic
import miksilo.lspprotocol.lsp.{Diagnostic, FileRange, RelatedInformation}
import typeless.ast.{JavaScriptFile, Lambda, NameLike, Statement}
import typeless.server.JavaScriptCompilation

import scala.collection.mutable

trait StatementResult {
  def toExpressionResult: ExpressionResult
}

case class ReturnedValue(value: Value) extends StatementResult {
  override def toExpressionResult: ExpressionResult = value
}



// TODO turn into a monad.
trait ExpressionResult {
  def flatMap(f: Value => ExpressionResult): ExpressionResult
  def toValue: Option[Value]
}

trait UserExceptionResult extends ExceptionResult {
  def canBeModified: Boolean
  def toDiagnostic: Diagnostic
}

trait SimpleExceptionResult extends UserExceptionResult {
  def canBeModified = true
  def element: SourceElement
  def message: String

  def toDiagnostic: Diagnostic = {
    Diagnostic(element.rangeOption.get.toSourceRange, Some(1), message)
  }
}

trait ExceptionResult extends ExpressionResult with StatementResult {

  override def toValue: Option[Value] = None

  override def flatMap(f: Value => ExpressionResult): ExpressionResult = this

  override def toExpressionResult: ExpressionResult = this
}

case class UndefinedMemberAccess(element: SourceElement, name: String, value: Value)
  extends SimpleExceptionResult {
  override def message: String = s"The member '$name' is not available on value '${value.represent()}'"
}

trait CatchableExceptionResult extends ExceptionResult

case class ReferenceError(element: SourceElement, name: String)
  extends SimpleExceptionResult with CatchableExceptionResult {
  override def message: String = s"Variable $name was accessed but is not defined"
}
case class TypeError(element: SourceElement, expected: String, value: Value)
  extends SimpleExceptionResult with CatchableExceptionResult {
  override def message: String = s"Expected value $expected but got '${value.represent()}'"
}

class PrimitiveValue[T](val value: T) extends Value {
  override def represent(depth: Int = 1): String = value.toString
}

class IntValue(value: Int) extends PrimitiveValue[Int](value) {
}

class UndefinedValue extends Value {
}

class ObjectValue(var members: mutable.Map[String, Value] = mutable.Map.empty)
  extends Value with ScopeLike {

  def getMember(name: String): Option[Value] = {
    members.get(name)
  }

  def setMember(name: String, value: Value): Unit = {
    val definedAt = members.get(name).flatMap(v => v.definedAt)
    value.definedAt = definedAt.orElse(value.definedAt)
    members.put(name, value)
  }

  override def represent(depth: Int = 1): String = {
    if (depth == 0) {
      "Object"
    } else {
      val membersString = members.map(e => e._1 + ": " + e._2.represent(depth - 1)).mkString(", ")
      s"{ $membersString }"
    }
  }

  override def memberNames: Iterable[String] = members.keys

  override def getValue(member: String): Value = members(member)
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

  override def toValue: Option[Value] = Some(this)

  override def flatMap(f: Value => ExpressionResult): ExpressionResult = {
    f(this)
  }

  var definedAt: Option[NameLike] = None
  var createdAt: SourceElement = null
  var documentation: Option[String] = None

  def represent(depth: Int = 1): String = "some value"
}

object VoidResult extends StatementResult {
  override def toExpressionResult: ExpressionResult = new UndefinedValue()
}






class Scope(parentOption: Option[Scope] = None) extends ScopeLike {

  def nest(): Scope = new Scope(Some(this))

  var environment: mutable.HashMap[String, Value] = mutable.HashMap.empty

  def declare(name: String, value: Value): Boolean = {
    if (environment.contains(name)) {
      false
    } else {
      environment.put(name, value)
      true
    }
  }

  def assign(name: String, value: Value): Boolean = {
    if (environment.contains(name)) {
      environment.put(name, value)
      true
    } else {
      parentOption.fold(false)(parent => parent.assign(name, value))
    }
  }

  def get(element: SourceElement, name: String): ExpressionResult = {
    environment.getOrElse[ExpressionResult](name, {
      parentOption.fold[ExpressionResult](ReferenceError(element, name))(parent => parent.get(element, name))
    })
  }

  override def memberNames: Iterable[String] =
    environment.keys.concat(parentOption.fold(Iterable.empty[String])(p => p.memberNames))

  override def getValue(member: String): Value = {
    environment.getOrElse(member, parentOption.get.getValue(member))
  }
}

class Closure(val lambda: Lambda, val state: Scope) extends Value with ClosureLike {

  def evaluate(context: Context, argumentValues: collection.Seq[Value]): ExpressionResult = {
    val newContext = context.withScope(state.nest())
    lambda.arguments.zip(argumentValues).foreach(t => {
      newContext.declareWith(t._1, t._1.name, t._2)
    })
    Statement.evaluateBody(newContext, lambda.body).toExpressionResult
  }
}




class BooleanValue(value: Boolean) extends PrimitiveValue[Boolean](value) {
}

case class NotImplementedException(element: SourceElement) extends SimpleExceptionResult {
  override def message: String =
    "The interpreter functionality required to evaluate this code was not yet implemented."
}

trait ClosureLike extends Value {
  def evaluate(context: Context, argumentValues: collection.Seq[Value]): ExpressionResult
}


case class AssertEqualFailure(file: String, actual: Value, expected: Value) extends UserExceptionResult {

  override def toDiagnostic: Diagnostic = {
    val message = s"The value '${expected.represent()}' was expected but it was '${actual.represent()}'"
    val relatedInformation = RelatedInformation(FileRange(file, expected.createdAt.rangeOption.get.toSourceRange), expected.represent())
    Diagnostic(actual.createdAt.rangeOption.get.toSourceRange, Some(1), message, relatedInformation = Seq(relatedInformation))
  }

  override def canBeModified: Boolean = false
}