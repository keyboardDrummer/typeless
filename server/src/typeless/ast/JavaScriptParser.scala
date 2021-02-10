package typeless.ast

import miksilo.editorParser.parsers.editorParsers.{History, LeftRecursiveCorrectingParserWriter}
import miksilo.editorParser.parsers.strings.{CommonStringReaderParser, WhitespaceParserWriter}
import typeless.ast

import scala.collection.immutable.ListMap

object JavaScriptParser extends CommonStringReaderParser with LeftRecursiveCorrectingParserWriter with WhitespaceParserWriter {

  val statementEnd = ";" | "\n"

  //  lazy val array = ("[" ~> valueParser.manySeparated(",", "value") ~< "]").
  //    withSourceRange((range, value) => JsonArray(Some(range), value.toArray))
  lazy val objectMember = (parseIdentifier | stringLiteral) ~< ":" ~ expression
  lazy val objectLiteral = (literal("{", 2 * History.missingInputPenalty) ~>
    objectMember.manySeparated(",", "member") ~< "}").
    withSourceRange((range, value) => ObjectLiteral(range, ListMap.from(value)))

  val booleanParser = ("true" ~> succeed(true) | "false" ~> succeed(false)).
    withSourceRange((range, value) => BooleanLiteral(range, value))

  val lineComment = parseRegex("""//[^\n]*""".r, "line comment")

  override def trivia: Parser[String] = lineComment | whiteSpace

  val stringLiteralParser: Parser[StringLiteral] = stringLiteral.withSourceRange((range, value) => StringLiteral(range, value))
  val numberLiteral = wholeNumber.withSourceRange((range, number) => WholeNumber(range, Integer.parseInt(number)))

  // Priority is not yet correctly established
  val addition = (expression ~< "+" ~ expression).withSourceRange((range, t) => Addition(range, t._1, t._2))
  val subtraction = (expression ~< "-" ~ expression).withSourceRange((range, t) => Subtraction(range, t._1, t._2))
  val multiplication = (expression ~< "*" ~ expression).withSourceRange((range, t) => Multiplication(range, t._1, t._2))
  val modulo = (expression ~< "%" ~ expression).withSourceRange((range, t) => ast.Multiplication(range, t._1, t._2))
  val equalsParser = (expression ~< "==" ~ expression).withSourceRange((range, t) => Equals(range, t._1, t._2))
  val lessThanParser = (expression ~< "<" ~ expression).withSourceRange((range, t) => LessThan(range, t._1, t._2))
  val variableExpression: Parser[VariableReference] = parseIdentifier.withSourceRange((range, name) => VariableReference(range, name))

  val newParser: Parser[New] = ("new" ~> expression ~< "(" ~ expression.manySeparated(",", "argument") ~< ")").
    withSourceRange((range, t) => new New(range, t._1, t._2))

  lazy val callExpression: Parser[Call] = (expression ~< "(" ~ expression.manySeparated(",", "argument") ~< ")").
    withSourceRange((range, t) => Call(range, t._1, t._2))

  val argument: Parser[Argument] = ("...".option ~ parseIdentifier).
    withSourceRange((range, name) => Argument(range, name._2, name._1.isEmpty))
  val arguments = "(" ~> argument.manySeparated(",", "argument") ~< ")"
  lazy val lambdaBody = body | expression.map(expr => Vector(ExpressionStatement(expr.range, expr)))
  val lambdaArguments: JavaScriptParser.ParserBuilder[Vector[Argument]] = arguments | argument.map(a => Vector(a))
  lazy val lambda: Parser[Lambda] = (lambdaArguments ~< "=>" ~ lambdaBody).
    withSourceRange((range, t) => Lambda(range, t._1, t._2))

  val bracketAccess: Parser[BracketAccess] = (expression ~< "[" ~ expression ~< "]").
    withSourceRange((range, t) => BracketAccess(range, t._1, t._2))
  val dotAccess: Parser[DotAccess] = (expression ~< "." ~ name).
    withSourceRange((range, t) => DotAccess(range, t._1, t._2))

  val assignmentTarget = dotAccess | variableExpression | bracketAccess
  val assignment: Parser[Assignment] = (assignmentTarget ~< "=" ~ expression).
    withSourceRange((range, t) => Assignment(range, t._1, t._2))

  val thisParser = ("this": Parser[String]).withSourceRange((range, _) => ThisReference(range))
  lazy val expression: Parser[Expression] = new Lazy(thisParser | callExpression | lambda | numberLiteral | variableExpression |
    addition | subtraction | multiplication | dotAccess | bracketAccess | assignment | modulo | objectLiteral | stringLiteralParser
    | booleanParser | equalsParser | newParser | lessThanParser)

  val name = (parseIdentifier | Fallback("", "name")).withSourceRange((range, name) => new Name(range, name))
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
