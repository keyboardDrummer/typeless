package typeless.ast

import miksilo.editorParser.parsers.editorParsers.{History, LeftRecursiveCorrectingParserWriter}
import miksilo.editorParser.parsers.strings.{CommonStringReaderParser, WhitespaceParserWriter}
import typeless.ast

import scala.collection.immutable.ListMap

//noinspection TypeAnnotation
object JavaScriptParser extends CommonStringReaderParser
  with LeftRecursiveCorrectingParserWriter
  with WhitespaceParserWriter {

  override def trivia: Parser[String] = {
    val lineComment = parseRegex("""//[^\n]*""".r, "line comment")
    lineComment | whiteSpace
  }

  val booleanParser = ("true" ~> succeed(true) | "false" ~> succeed(false)).
    withSourceRange((range, value) => BooleanLiteral(range, value))
  val stringLiteralParser: Parser[StringLiteral] = stringLiteral.withSourceRange((range, value) => StringLiteral(range, value))
  val numberLiteral = wholeNumber.withSourceRange((range, number) => WholeNumber(range, Integer.parseInt(number)))
  val variableExpression: Parser[VariableReference] = parseIdentifier.withSourceRange((range, name) => new VariableReference(range, name))
  val thisParser: Parser[ThisReference] = ("this": Parser[String]).withSourceRange((range, _) => ThisReference(range))

  val nonRecursiveExpression = booleanParser | thisParser | numberLiteral | stringLiteralParser | variableExpression

  //  lazy val array = ("[" ~> valueParser.manySeparated(",", "value") ~< "]").
  //    withSourceRange((range, value) => JsonArray(Some(range), value.toArray))
  lazy val objectMember = name ~< ":" ~ expression
  lazy val objectLiteral = (literal("{", 2 * History.missingInputPenalty) ~>
    objectMember.manySeparated(",", "member") ~< "}").
    withSourceRange((range, value) => ObjectLiteral(range, ListMap.from(value)))
  val nonAssociatedExpression = nonRecursiveExpression | objectLiteral

  val parenthesis: Parser[Expression] = "(" ~> expression ~< ")"
  val expression21: Parser[Expression] = parenthesis | nonAssociatedExpression

  val name = (parseIdentifier | Fallback("", "name")).withSourceRange((range, name) => new Name(range, name))
  val bracketAccess: Parser[BracketAccess] = (expression20 ~< "[" ~ expression ~< "]").
    withSourceRange((range, t) => BracketAccess(range, t._1, t._2))
  val dotAccess: Parser[DotAccess] = (expression20 ~< "." ~ name).
    withSourceRange((range, t) => DotAccess(range, t._1, t._2))

  val newParser: Parser[New] = ("new" ~> expression20 ~< "(" ~ expression.manySeparated(",", "argument") ~< ")").
    withSourceRange((range, t) => new New(range, t._1, t._2))

  val callExpression: Parser[Call] = (expression20 ~< "(" ~ expression.manySeparated(",", "argument") ~< ")").
    withSourceRange((range, t) => Call(range, t._1, t._2))
  lazy val expression20: Parser[Expression] = new Lazy(newParser | callExpression | dotAccess | bracketAccess | expression21)

  val multiplication = (expression15 ~< "*" ~ expression15).withSourceRange((range, t) => Multiplication(range, t._1, t._2))
  val modulo = (expression15 ~< "%" ~ expression15).withSourceRange((range, t) => ast.Multiplication(range, t._1, t._2))
  lazy val expression15: Parser[Expression] = new Lazy(multiplication | modulo | expression20)

  val addition = (expression14 ~< "+" ~ expression14).withSourceRange((range, t) => Addition(range, t._1, t._2))
  val subtraction = (expression14 ~< "-" ~ expression14).withSourceRange((range, t) => Subtraction(range, t._1, t._2))
  lazy val expression14: Parser[Expression] = new Lazy(addition | subtraction | expression15)

  val lessThanParser = (expression12 ~< "<" ~ expression12).withSourceRange((range, t) => LessThan(range, t._1, t._2))
  lazy val expression12: Parser[Expression] = new Lazy(lessThanParser | expression14)

  val equalsParser = (expression11 ~< "==" ~ expression11).withSourceRange((range, t) => Equals(range, t._1, t._2))
  lazy val expression11: Parser[Expression] = new Lazy(equalsParser | expression12)

  val assignmentTarget: Parser[AssignmentTarget] = dotAccess | variableExpression | bracketAccess
  val assignment: Parser[Assignment] = (assignmentTarget ~< "=" ~ expression3).
    withSourceRange((range, t) => Assignment(range, t._1, t._2))
  lazy val expression3: Parser[Expression] = new Lazy(assignment | expression11)

  val argument: Parser[Argument] = ("...".option ~ parseIdentifier).
    withSourceRange((range, name) => Argument(range, name._2, name._1.isEmpty))
  val arguments: Parser[Vector[Argument]] = "(" ~> argument.manySeparated(",", "argument") ~< ")"
  lazy val lambdaBody = body | expression.map(expr => Vector(ExpressionStatement(expr.range, expr)))
  val lambdaArguments: Parser[Vector[Argument]] = arguments | argument.map(a => Vector(a))
  lazy val lambda: Parser[Lambda] = (lambdaArguments ~< "=>" ~ lambdaBody).
    withSourceRange((range, t) => Lambda(range, t._1, t._2))
  val expression0: Parser[Expression] = lambda | expression3

  lazy val expression: Parser[Expression] = new Lazy(expression0)

  // STATEMENTS

  val statementEnd = ";" | "\n"
  val declaration: Parser[Declaration] = ("const" ~> name ~< "=" ~ expression ~< statementEnd).
    withSourceRange((range, t) => Declaration(range, t._1, t._2))
  val expressionStatement: Parser[ExpressionStatement] = (expression ~< statementEnd).
    withSourceRange((range, expr) => ExpressionStatement(range, expr))

  val returnStatement = ("return" ~> expression ~< statementEnd).withSourceRange((range, expr) => ReturnStatement(range, expr))

  lazy val statement: Parser[Statement] = new Lazy(ifStatement | declaration | expressionStatement | returnStatement)
  lazy val statements = statement.*

  val singleStatementOrBody = statement.map(s => Vector(s)) | "{" ~> statements ~< "}"
  val elsePart = ("else" ~> singleStatementOrBody).option.map(o => o.getOrElse(Seq.empty))
  val ifStatement = ("if" ~> "(" ~> expression ~< ")" ~ singleStatementOrBody ~ elsePart).
    withSourceRange((range, t) => IfStatement(range, t._1._1, t._1._2, t._2))

  lazy val body = "{" ~> statements ~< "}"
  val file: Parser[JavaScriptFile] = statements.withSourceRange((range, body) => ast.JavaScriptFile(range, body))

  val javaScript = file ~< trivias
}
