package typeless.ast

import miksilo.editorParser.parsers.SourceElement
import miksilo.editorParser.parsers.editorParsers.OffsetPointerRange
import miksilo.languageServer.core.language.FileElement
import typeless.interpreter.{BooleanValue, Context, ExceptionResult, ExpressionResult, IntValue, ObjectValue, TypeError, Value}

import scala.collection.immutable.ListMap

case class BooleanLiteral(range: OffsetPointerRange, value: Boolean) extends Expression {
  override def evaluate(context: Context): ExpressionResult = ???
}

case class StringValue(value: String) extends Value {
  override def represent(): String = value
}

case class StringLiteral(range: OffsetPointerRange, value: String) extends Expression {
  override def evaluate(context: Context): ExpressionResult = {
    StringValue(value)
  }
}

case class ObjectLiteral(range: OffsetPointerRange, members: ListMap[String, Expression]) extends Expression {
  override def evaluate(context: Context): ExpressionResult = {
    val result = new ObjectValue()
    for(e <- members) {
      context.evaluateExpression(e._2) match {
        case e: ExceptionResult => return e
        case value: Value => result.members.put(e._1, value)
      }
    }
    result
  }

  override def childElements: Seq[SourceElement] = {
    members.values.toSeq
  }
}

case class VariableReference(range: OffsetPointerRange, name: String)
  extends Expression with AssignmentTarget {
  override def evaluate(context: Context): ExpressionResult = {
    if (context.collectScopeAtElement.contains(this)) {
      return ScopeInformation(context.scope)
    }
    context.get(this, name)
  }

  override def assign(context: Context, value: Expression): ExpressionResult = {
    val valueResult = context.evaluateExpression(value)
    valueResult match {
      case valueValue: Value =>
        context.assign(this, name, valueValue)
        valueValue
      case e => e
    }
  }
}

trait BinaryExpression extends Expression {
  def left: Expression
  def right: Expression

  override def childElements: Seq[SourceElement] = {
    Seq(left, right)
  }

  def evaluate(context: Context, leftValue: Value, rightValue: Value): ExpressionResult

  override def evaluate(context: Context): ExpressionResult = {
    val leftResult = context.evaluateExpression(left)
    leftResult match {
      case leftValue: Value =>
        val rightResult = context.evaluateExpression(right)
        rightResult match {
          case rightValue: Value => evaluate(context, leftValue, rightValue)
          case _ => rightResult
        }
      case _ => leftResult
    }
  }
}

case class WholeNumber(range: OffsetPointerRange, int: Int) extends Expression {
  override def evaluate(context: Context): ExpressionResult = new IntValue(int)
}

case class Modulo(range: OffsetPointerRange, left: Expression, right: Expression) extends BinaryExpression {
  override def evaluate(context: Context, leftValue: Value, rightValue: Value): ExpressionResult = ???
}
case class Subtraction(range: OffsetPointerRange, left: Expression, right: Expression) extends BinaryExpression {
  override def evaluate(context: Context, leftValue: Value, rightValue: Value): ExpressionResult = {
    (leftValue, rightValue) match {
      case (leftInt: IntValue, rightInt: IntValue) => new IntValue(leftInt.value - rightInt.value)
      case _ => TypeError(this, "supports -", leftValue)
    }
  }
}

case class LessThan(range: OffsetPointerRange, left: Expression, right: Expression) extends BinaryExpression {
  override def evaluate(context: Context, leftValue: Value, rightValue: Value): ExpressionResult = {
    (leftValue, rightValue) match {
      case (leftInt: IntValue, rightInt: IntValue) => new BooleanValue(leftInt.value < rightInt.value)
      case _ => TypeError(left, "something that supports the '<' operator", leftValue)
    }
  }
}

case class Equals(range: OffsetPointerRange, left: Expression, right: Expression) extends BinaryExpression {
  override def evaluate(context: Context, leftValue: Value, rightValue: Value): ExpressionResult = {
    new BooleanValue(Value.strictEqual(leftValue, rightValue))
  }
}

case class Multiplication(range: OffsetPointerRange, left: Expression, right: Expression) extends BinaryExpression {
  override def evaluate(context: Context, leftValue: Value, rightValue: Value): ExpressionResult = {
    (leftValue, rightValue) match {
      case (leftInt: IntValue, rightInt: IntValue) => new IntValue(leftInt.value * rightInt.value)
      case _ => ???
    }
  }
}

case class Addition(range: OffsetPointerRange, left: Expression, right: Expression) extends BinaryExpression {
  override def evaluate(context: Context, leftValue: Value, rightValue: Value): ExpressionResult = {
    (leftValue, rightValue) match {
      case (leftInt: IntValue, rightInt: IntValue) => new IntValue(leftInt.value + rightInt.value)
      case _ => ???
    }
  }
}

trait Expression extends FileElement {
  def evaluate(context: Context): ExpressionResult
}