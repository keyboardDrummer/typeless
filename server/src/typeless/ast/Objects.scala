package typeless.ast

import miksilo.editorParser.parsers.SourceElement
import miksilo.editorParser.parsers.editorParsers.OffsetPointerRange
import typeless.interpreter._

import scala.collection.mutable.ArrayBuffer

class New(range: OffsetPointerRange, target: Expression, arguments: Vector[Expression]) extends CallBase(range, target, arguments) {

  override def evaluateClosure(context: Context, argumentValues: ArrayBuffer[Value], closure: ClosureLike): Option[ExpressionResult] = {
    // TODO, assign __proto__ field from closure.prototype
    val newObj = new ObjectValue()
    newObj.createdAt = this
    context.setThis(newObj)
    super.evaluateClosure(context, argumentValues, closure).map {
      case _: UndefinedValue => newObj
      case result => result
    }
  }
}

class BracketAccess(val range: OffsetPointerRange, target: Expression, property: Expression)
  extends Expression with AssignmentTarget {

  override def evaluate(context: Context): ExpressionResult = {
    context.evaluateObjectValue(target).flatMap(targetValue => {
      val targetObject = targetValue.asInstanceOf[ObjectValue]
      context.evaluateString(property).flatMap(propertyValue => {
        val stringValue = propertyValue.asInstanceOf[StringValue]
        targetObject.getMember(stringValue.value) match {
          case Some(memberValue) => memberValue
          case None => if (context.configuration.allowUndefinedPropertyAccess) {
            new UndefinedValue()
          } else {
            UndefinedMemberAccess(this, stringValue.value, targetValue)
          }
        }
      })
    })
  }

  override def childElements: Seq[SourceElement] = {
    Seq(target, property)
  }

  override def assign(context: Context, value: Expression): ExpressionResult = {
    context.evaluateObjectValue(target).flatMap(targetValue => {
      val targetObject = targetValue.asInstanceOf[ObjectValue]
      context.evaluateString(property).flatMap(propertyValue => {
        val stringValue = propertyValue.asInstanceOf[StringValue]
        context.evaluateExpression(value).flatMap(valueValue => {
          targetObject.setMember(stringValue.value, valueValue)
          valueValue
        })
      })
    })
  }
}

class DotAccess(val range: OffsetPointerRange, target: Expression, property: Name)
  extends Expression with AssignmentTarget {
  override def evaluate(context: Context): ExpressionResult = {
    context.evaluateObjectValue(target).flatMap(targetValue => {
      val targetObject = targetValue.asInstanceOf[ObjectValue]
      if (context.configuration.mode == FindScope(property)) {
        return ScopeInformation(targetObject)
      }
      context.lastDotAccessTarget = Some(targetObject)
      targetObject.members.get(property.name).foreach(memberValue =>
        property.addReference(context, memberValue))
      targetObject.getMember(property.name) match {
        case Some(memberValue) =>
          property.addReference(context, memberValue)
          memberValue
        case None => if (context.configuration.allowUndefinedPropertyAccess) {
          new UndefinedValue()
        } else {
          UndefinedMemberAccess(this, property.name, targetValue)
        }
      }
    })
  }

  override def childElements: Seq[SourceElement] = {
    Seq(target, property)
  }

  override def assign(context: Context, value: Expression): ExpressionResult = {
    context.evaluateObjectValue(target).flatMap(targetValue => {
      val targetObject = targetValue.asInstanceOf[ObjectValue]
      targetObject.members.get(property.name).foreach(memberValue => property.addReference(context, memberValue))
      context.evaluateExpression(value).flatMap(valueValue => {
        valueValue.definedAt = Some(property)
        targetObject.setMember(property.name, valueValue)
        valueValue
      })
    })
  }
}

trait AssignmentTarget extends Expression {
  def assign(context: Context, value: Expression): ExpressionResult
}

class Assignment(val range: OffsetPointerRange, target: AssignmentTarget, value: Expression) extends Expression {
  override def evaluate(context: Context): ExpressionResult = {
    target.assign(context, value)
  }

  override def childElements: Seq[SourceElement] = {
    Seq(target, value)
  }
}

class ThisReference(val range: OffsetPointerRange) extends Expression {
  override def evaluate(context: Context): ExpressionResult = {
    context.getThis
  }
}