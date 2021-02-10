package typeless.ast

import miksilo.editorParser.parsers.SourceElement
import miksilo.editorParser.parsers.editorParsers.OffsetPointerRange
import typeless._
import typeless.interpreter.{ClosureLike, Context, ExceptionResult, ExpressionResult, ObjectValue, TypeError, UndefinedMemberAccess, UndefinedValue, Value}

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

case class BracketAccess(range: OffsetPointerRange, target: Expression, property: Expression)
  extends Expression with AssignmentTarget {

  override def evaluate(context: Context): ExpressionResult = {
    context.evaluateExpression(target).flatMap(targetValue => {
      context.evaluateString(property).flatMap(propertyValue => {
        val stringValue = propertyValue.asInstanceOf[StringValue]
        targetValue.getMember(stringValue.value) match {
          case Some(memberValue) => memberValue
          case None => if (context.allowUndefinedPropertyAccess) {
            new UndefinedValue()
          } else {
            UndefinedMemberAccess(this, stringValue.value, targetValue)
          }
        }
      })
    })
  }

  override def childElements: Seq[SourceElement] = {
    Seq(target)
  }

  override def assign(context: Context, value: Expression): ExpressionResult = {
    context.evaluateExpression(target).flatMap(targetValue => {
      context.evaluateString(property).flatMap(propertyValue => {
        val stringValue = propertyValue.asInstanceOf[StringValue]
        context.evaluateExpression(value).flatMap(valueValue => {
          targetValue.setMember(stringValue.value, valueValue)
          valueValue
        })
      })
    })
  }
}

case class DotAccess(range: OffsetPointerRange, target: Expression, property: Name)
  extends Expression with AssignmentTarget {
  override def evaluate(context: Context): ExpressionResult = {
    context.evaluateExpression(target).flatMap(targetValue => {
      targetValue.getMember(property.value) match {
        case Some(memberValue) => memberValue
        case None => if (context.allowUndefinedPropertyAccess) {
          new UndefinedValue()
        } else {
          UndefinedMemberAccess(this, property.value, targetValue)
        }
      }
    })
  }

  override def childElements: Seq[SourceElement] = {
    Seq(target)
  }

  override def assign(context: Context, value: Expression): ExpressionResult = {
    context.evaluateExpression(target).flatMap(targetValue => {
      context.evaluateExpression(value).flatMap(valueValue => {
        valueValue.definedAt = Some(property)
        targetValue.setMember(property.value, valueValue)
        valueValue
      })
    })
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