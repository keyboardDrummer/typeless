package typeless.ast

import miksilo.editorParser.parsers.SourceElement
import miksilo.editorParser.parsers.editorParsers.OffsetPointerRange
import typeless.interpreter._

import scala.collection.mutable.ArrayBuffer

class New(range: OffsetPointerRange, target: Expression, arguments: Vector[Expression]) extends CallElement(range, target, arguments) {

  override def evaluateClosure(context: Context, argumentValues: ArrayBuffer[Value], closure: ClosureLike): ExpressionResult = {
    // TODO, assign __proto__ field from closure.prototype
    val newObj = new ObjectValue()
    newObj.createdAt = this
    context.setThis(newObj)
    super.evaluateClosure(context, argumentValues, closure) match {
      case _: UndefinedValue => newObj
      case result => result
    }
  }
}

class BracketAccess(val range: OffsetPointerRange, target: Expression, property: Expression)
  extends Expression with AssignmentTarget {

  override def evaluate(context: Context): ExpressionResult = {
    context.evaluateString(property).flatMap(propertyValue => {
      val stringValue = propertyValue.asInstanceOf[StringValue]
      context.getObjectProperty(this, target, stringValue.value, setAccessTarget = false)
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
    if (context.configuration.mode == FindScope(property)) {
      return context.evaluateObjectValue(target) match {
        case targetObject: ObjectValue => ScopeInformation(targetObject)
        case e => return e
      }
    }

    context.getObjectProperty(this, target, property.name, setAccessTarget = true).flatMap(memberValue => {
      property.addReference(context, memberValue)
      memberValue
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