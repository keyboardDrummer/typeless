import miksilo.editorParser.parsers.{RealSourceElement, SourceElement}
import miksilo.editorParser.parsers.editorParsers.OffsetPointerRange
import miksilo.languageServer.core.language.FileElement

import scala.collection.immutable.ListMap
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

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
  override def evaluate(context: Context): ExpressionResult = ???

  override def childElements: Seq[SourceElement] = {
    members.values.toSeq
  }
}

case class ThisReference(range: OffsetPointerRange) extends Expression {
  override def evaluate(context: Context): ExpressionResult = {
    context.getThis()
  }
}

// Let's also use this for variable assignment
case class MemberAssignment(range: OffsetPointerRange, target: Expression, name: String, value: Expression) extends Expression {
  override def evaluate(context: Context): ExpressionResult = {
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

  override def childElements: Seq[SourceElement] = {
    Seq(target, value)
  }
}

case class MemberAccess(range: OffsetPointerRange, target: Expression, name: String) extends Expression {
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
}

case class VariableReference(range: OffsetPointerRange, name: String) extends Expression {
  override def evaluate(context: Context): ExpressionResult = context.get(this, name)
}


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

class CorrectCallGaveException(exception: ExceptionResult, call: Call,
                               closure: ClosureLike, argumentValues: collection.Seq[Value])
  extends DiagnosticExceptionResult {
  override def element: SourceElement = call

  override def message: String = {
    val values = argumentValues.map(a => a.represent()).reduce((left, right) => left + ", " + right)
    s"This function call failed with arguments '$values'."
  }
}

case class Call(range: OffsetPointerRange, target: Expression, arguments: Vector[Expression]) extends Expression {

  override def childElements: Seq[SourceElement] = {
    Seq(target) ++ arguments
  }

  override def evaluate(context: Context): ExpressionResult = {
    val targetResult = context.evaluateExpression(target)
    val argumentResults = arguments.map(argument => context.evaluateExpression(argument))
    val argumentValues = mutable.ArrayBuffer.empty[Value]
    for(argumentResult <- argumentResults) {
      argumentResult match {
        case argumentValue: Value => argumentValues.addOne(argumentValue)
        case _ => return argumentResult
      }
    }
    targetResult match {
      case closure: ClosureLike =>
        evaluateClosure(context, argumentValues, closure) match {
          case exception: ExceptionResult =>
            if (context.isClosureCorrect(closure)) {
              new CorrectCallGaveException(exception, this, closure, argumentValues)
            }
            else {
              exception
            }
          case result => result
        }
      case targetValue: Value =>
        TypeError(target, "closure", targetValue)
      case _ => targetResult
    }
  }

  def evaluateClosure(context: Context, argumentValues: ArrayBuffer[Value], closure: ClosureLike): ExpressionResult = {
    closure.evaluate(context, argumentValues)
  }
}

case class ReturnStatement(range: OffsetPointerRange, expression: Expression) extends Statement {
  override def evaluate(context: Context): StatementResult = {
    context.evaluateExpression(expression) match {
      case value: Value => ReturnedValue(value)
      case result: ExceptionResult => result
    }
  }

  override def childElements: Seq[SourceElement] = {
    Seq(expression)
  }
}

case class ExpressionStatement(range: OffsetPointerRange, expression: Expression) extends Statement {
  override def evaluate(context: Context): StatementResult = {
    context.evaluateExpression(expression) match {
      case _: Value => Void
      case statementResult: StatementResult => statementResult
    }
  }

  override def childElements: Seq[SourceElement] = {
    Seq(expression)
  }
}

case class JavaScriptFile(range: OffsetPointerRange, statements: Vector[Statement]) extends FileElement {
  def evaluate(context: Context): StatementResult = {
    Statement.evaluateBody(context, statements)
  }

  override def childElements: Seq[SourceElement] = {
    statements
  }
}

case class DeclarationName(range: OffsetPointerRange, value: String) extends FileElement

case class Declaration(range: OffsetPointerRange, name: DeclarationName, value: Expression) extends Statement {
  override def evaluate(context: Context): StatementResult = {
    val evaluated = context.evaluateExpression(value)
    evaluated match {
      case value: Value =>
        context.declareWith(name, name.value, value)
        Void
      case statementResult: StatementResult => statementResult
    }
  }

  override def childElements: Seq[SourceElement] = {
    Seq(value)
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

case class Argument(range: OffsetPointerRange, name: String, varArgs: Boolean) extends FileElement {

}

case class Lambda(range: OffsetPointerRange, arguments: Vector[Argument], body: Vector[Statement]) extends Expression {
  override def evaluate(context: Context): ExpressionResult = {
    new Closure(this, context.state)
  }

  override def childElements: Seq[SourceElement] = {
    arguments ++ body
  }
}

object Statement {
  def evaluateBody(context: Context, statements: Seq[Statement]): StatementResult = {
    for(statement <- statements) {
      statement.evaluate(context) match {
        case Void =>
        case returnedValue: ReturnedValue => return returnedValue
        case exceptionResult: ExceptionResult => return exceptionResult
      }
    }
    Void
  }
}

case class IfStatement(range: OffsetPointerRange,
                       condition: Expression,
                       thenBody: Seq[Statement],
                       elseBody: Seq[Statement]) extends Statement {
  override def evaluate(context: Context): StatementResult = {
    val conditionResult = context.evaluateExpression(condition)
    conditionResult match {
      case conditionValue: BooleanValue =>
        val result = if (conditionValue.value) {
          Statement.evaluateBody(context, thenBody)
        } else {
          Statement.evaluateBody(context, elseBody)
        }
        result
      case conditionValue: Value => TypeError(this, "boolean", conditionValue)
      case e: ExceptionResult => e
    }
  }

  override def childElements: Seq[SourceElement] = {
    Seq(condition) ++ thenBody ++ elseBody
  }
}

trait Statement extends FileElement {
  def evaluate(context: Context): StatementResult
}