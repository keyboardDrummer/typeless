package typeless.ast

import miksilo.editorParser.parsers.SourceElement
import miksilo.editorParser.parsers.editorParsers.OffsetPointerRange
import typeless._
import typeless.interpreter.{ClosureLike, Context, ExceptionResult, ExpressionResult, ObjectValue, UndefinedMemberAccess, UndefinedValue, Value}

import scala.collection.mutable.ArrayBuffer

class New(range: OffsetPointerRange, target: Expression, arguments: Vector[Expression]) extends Call(range, target, arguments) {

  override def evaluateClosure(context: Context, argumentValues: ArrayBuffer[Value], closure: ClosureLike): ExpressionResult = {
    // TODO, assing __proto__ field from closure.prototype
    val newObj = new ObjectValue()
    context.setThis(newObj)
    closure.evaluate(context, argumentValues) match {
      case _: UndefinedValue => newObj
      case result => result
    }
  }
}


case class MemberAccess(range: OffsetPointerRange, target: Expression, name: String)
  extends Expression with AssignmentTarget {
  override def evaluate(context: Context): ExpressionResult = {
    val targetResult = context.evaluateExpression(target)
    targetResult match {
      case targetValue: Value =>
        targetValue.getMember(name) match {
          case Some(memberValue) => memberValue
          case None => if (context.allowUndefinedPropertyAccess) {
            new UndefinedValue()
          } else {
            UndefinedMemberAccess(this, name, targetValue)
          }
        }
      case e: ExceptionResult => e
    }
  }

  override def childElements: Seq[SourceElement] = {
    Seq(target)
  }

  override def assign(context: Context, value: Expression): ExpressionResult = {
    val targetResult = context.evaluateExpression(target)
    targetResult match {
      case targetValue: Value =>
        val valueResult = context.evaluateExpression(value)
        valueResult match {
          case valueValue: Value =>
            targetValue.setMember(name, valueValue)
            valueValue
          case e => e
        }
      case e: ExceptionResult => e
    }
  }
}

trait AssignmentTarget extends Expression {
  def assign(context: Context, value: Expression): ExpressionResult
}

case class Assignment(range: OffsetPointerRange, target: AssignmentTarget, value: Expression) extends Expression {
  override def evaluate(context: Context): ExpressionResult = {
    target.assign(context, value)
  }

  override def childElements: Seq[SourceElement] = {
    Seq(target, value)
  }
}

case class ThisReference(range: OffsetPointerRange) extends Expression {
  override def evaluate(context: Context): ExpressionResult = {
    context.getThis()
  }
}