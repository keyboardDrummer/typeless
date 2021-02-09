import miksilo.editorParser.parsers.SourceElement
import miksilo.editorParser.parsers.editorParsers.OffsetPointerRange
import miksilo.languageServer.core.language.FileElement
import miksilo.lspprotocol.lsp.Diagnostic

import scala.collection.immutable.ListMap
import scala.collection.mutable

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
}

case class VariableReference(range: OffsetPointerRange, name: String) extends Expression {
  override def evaluate(context: Context): ExpressionResult = context.get(this, name)
}

case class Call(range: OffsetPointerRange, target: Expression, arguments: Vector[Expression]) extends Expression {
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
        closure.evaluate(context, argumentValues)
      case targetValue: Value =>
        TypeError(target, "closure", targetValue)
      case _ => targetResult
    }
  }
}

case class ReturnStatement(range: OffsetPointerRange, expression: Expression) extends Statement {
  override def evaluate(context: Context): StatementResult = {
    context.evaluateExpression(expression) match {
      case value: Value => ReturnedValue(value)
      case result: ExceptionResult => result
    }
  }
}

case class ExpressionStatement(range: OffsetPointerRange, expression: Expression) extends Statement {
  override def evaluate(context: Context): StatementResult = {
    context.evaluateExpression(expression) match {
      case _: Value => Void
      case statementResult: StatementResult => statementResult
    }
  }
}

case class JavaScriptFile(range: OffsetPointerRange, statements: Vector[Statement]) extends FileElement {
  def evaluate(context: Context): StatementResult = {
    Statement.evaluateBody(context, statements)
  }
}

case class Declaration(range: OffsetPointerRange, name: String, value: Expression) extends Statement {
  override def evaluate(context: Context): StatementResult = {
    val evaluated = context.evaluateExpression(value)
    evaluated match {
      case value: Value =>
        context.declareWith(this, name, value)
        Void
      case statementResult: StatementResult => statementResult
    }
  }
}

trait BinaryExpression extends Expression {
  def left: Expression
  def right: Expression

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
  override def evaluate(context: Context, leftValue: Value, rightValue: Value): ExpressionResult = ???
}

case class BooleanValue(value: Boolean) extends Value {

}
case class Equals(range: OffsetPointerRange, left: Expression, right: Expression) extends BinaryExpression {
  override def evaluate(context: Context, leftValue: Value, rightValue: Value): ExpressionResult = {
    BooleanValue(Value.strictEqual(leftValue, rightValue))
  }
}

case class Multiplication(range: OffsetPointerRange, left: Expression, right: Expression) extends BinaryExpression {
  override def evaluate(context: Context, leftValue: Value, rightValue: Value): ExpressionResult = ???
}
case class Addition(range: OffsetPointerRange, left: Expression, right: Expression) extends BinaryExpression {
  override def evaluate(context: Context, leftValue: Value, rightValue: Value): ExpressionResult = {
    (leftValue, rightValue) match {
      case (leftInt: IntValue, rightInt: IntValue) => new IntValue(leftInt.value + rightInt.value)
      case _ => ???
    }
  }
}

case class New(range: OffsetPointerRange, target: Expression, arguments: Vector[Expression]) extends Expression {
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
        // TODO, assing __proto__ field from closure.prototype
        val newObj = new ObjectValue()
        context.setThis(newObj)
        closure.evaluate(context, argumentValues) match {
          case _: UndefinedValue => newObj
          case result => result
        }
      case targetValue: Value =>
        TypeError(target, "closure", targetValue)
      case _ => targetResult
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
    Closure(this, context.state)
  }
}

case class Closure(lambda: Lambda, state: Scope) extends ClosureLike {

  def evaluate(context: Context, argumentValues: collection.Seq[Value]): ExpressionResult = {
    val newContext = context.withState(state.nest())
    lambda.arguments.zip(argumentValues).foreach(t => {
      newContext.declareWith(t._1, t._1.name, t._2)
    })
    Statement.evaluateBody(newContext, lambda.body).toExpressionResult()
  }
}

case class AssertEqualFailure(actual: Value, expected: Value) extends ExceptionResult {
  override def element: SourceElement = actual.createdAt

  override def message: String = s"The value '${expected.represent()}' was expected but it was '${actual.represent()}'."

  override def toDiagnostic: Diagnostic = {
    // TODO add related information linking to assertion.
    super.toDiagnostic
  }
}

object AssertStrictEqual extends ClosureLike {
  override def evaluate(context: Context, argumentValues: collection.Seq[Value]): ExpressionResult = {
    val actual = argumentValues(0)
    val expected = argumentValues(1)
    if (!Value.strictEqual(actual, expected)) {
      return AssertEqualFailure(actual, expected)
    }
    new UndefinedValue()
  }
}

trait ClosureLike extends Value {
  def evaluate(context: Context, argumentValues: collection.Seq[Value]): ExpressionResult
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

trait Statement extends FileElement {
  def evaluate(context: Context): StatementResult
}