import JavaScriptParser.{Parser, literal, parseIdentifier, parseRegex, stringLiteral, succeed, wholeNumber}
import miksilo.editorParser.parsers.editorParsers.{History, LeftRecursiveCorrectingParserWriter}
import miksilo.editorParser.parsers.strings.{CommonStringReaderParser, WhitespaceParserWriter}

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
  val modulo = (expression ~< "%" ~ expression).withSourceRange((range, t) => Multiplication(range, t._1, t._2))
  val equalsParser = (expression ~< "==" ~ expression).withSourceRange((range, t) => Equals(range, t._1, t._2))

  val variableExpression: Parser[VariableReference] = parseIdentifier.withSourceRange((range, name) => VariableReference(range, name))
  lazy val callExpression: Parser[Call] = (expression ~< "(" ~ expression.manySeparated(",", "argument") ~< ")").
    withSourceRange((range, t) => Call(range, t._1, t._2))

  val argument: Parser[Argument] = ("...".option ~ parseIdentifier).
    withSourceRange((range, name) => Argument(range, name._2, name._1.isEmpty))
  val arguments = "(" ~> argument.manySeparated(",", "argument") ~< ")"
  lazy val lambdaBody = body | expression.map(expr => Vector(ExpressionStatement(expr.range, expr)))
  val lambdaArguments: JavaScriptParser.ParserBuilder[Vector[Argument]] = arguments | argument.map(a => Vector(a))
  lazy val lambda: Parser[Lambda] = (lambdaArguments ~< "=>" ~ lambdaBody).
    withSourceRange((range, t) => Lambda(range, t._1, t._2))

  val memberAccess: Parser[MemberAccess] = (expression ~< "." ~ parseIdentifier).
    withSourceRange((range, t) => MemberAccess(range, t._1, t._2))
  lazy val expression: Parser[Expression] = new Lazy(callExpression | lambda | numberLiteral | variableExpression |
    addition | subtraction | multiplication | memberAccess | modulo | objectLiteral | stringLiteralParser
    | booleanParser | equalsParser)

  val declaration: Parser[Declaration] = ("const" ~> parseIdentifier ~< "=" ~ expression ~< statementEnd).
    withSourceRange((range, t) => Declaration(range, t._1, t._2))
  val expressionStatement: Parser[ExpressionStatement] = (expression ~< statementEnd).
    withSourceRange((range, expr) => ExpressionStatement(range, expr))

  val returnStatement = ("return" ~> expression ~< statementEnd).withSourceRange((range, expr) => ReturnStatement(range, expr))

  val statement: Parser[Statement] = declaration | expressionStatement | returnStatement
  val statements = statement.*
  lazy val body = "{" ~> statements ~< "}"
  val file: Parser[JavaScriptFile] = statements.withSourceRange((range, body) => JavaScriptFile(range, body))

  val javaScript = file ~< trivias
}