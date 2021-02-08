import miksilo.editorParser.languages.json.JsonParser.{Parser, literal, stringLiteral, wholeNumber}
import miksilo.editorParser.languages.json.{JsonArray, JsonObject, JsonValue, NumberLiteral, StringLiteral, ValueHole}
import miksilo.editorParser.parsers.core.{Metrics, TextPointer}
import miksilo.editorParser.parsers.{RealSourceElement, SourceElement}
import miksilo.editorParser.parsers.editorParsers.{History, LeftRecursiveCorrectingParserWriter, OffsetPointerRange, SingleParseResult, SingleResultParser, StopFunction}
import miksilo.editorParser.parsers.strings.{CommonStringReaderParser, WhitespaceParserWriter}
import miksilo.languageServer.JVMLanguageServer
import miksilo.languageServer.core.language.{FileElement, Language}
import miksilo.languageServer.server.LanguageBuilder

import scala.collection.immutable.ListMap

object Program extends JVMLanguageServer(Seq()) {


}

object JavaScriptLanguageBuilder extends LanguageBuilder {
  override def key: String = "javascript"

  override def build(arguments: collection.Seq[String]): Language = {
    JavaScriptLanguage
  }
}

object JavaScriptLanguage extends Language {
  val parsePhase = Language.getCachingParsePhase[JavaScriptFile]((file, uri) => file.addFile(uri),
    JavaScriptParser.javaScript.getWholeInputParser(), indentationSensitive = false)

  compilerPhases = List(parsePhase)
}

object JavaScriptParser extends CommonStringReaderParser with LeftRecursiveCorrectingParserWriter with WhitespaceParserWriter {

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

  val declaration: Parser[Declaration] = ("const" ~> parseIdentifier ~< "=" ~ expression ~< ";").
    withSourceRange((range, t) => Declaration(range, t._1, t._2))
  val expressionStatement: Parser[ExpressionStatement] = expression.withSourceRange((range, expr) => ExpressionStatement(range, expr)) ~< ";"

  val statement: Parser[Statement] = declaration | expressionStatement
  val statements = statement.*
  lazy val body = "{" ~> statements ~< "}"
  val file: Parser[JavaScriptFile] = statements.withSourceRange((range, body) => JavaScriptFile(range, body))

  val javaScript = file ~< trivias
}

case class BooleanLiteral(range: OffsetPointerRange, value: Boolean) extends Expression

case class StringLiteral(range: OffsetPointerRange, value: String) extends Expression

case class ObjectLiteral(range: OffsetPointerRange, members: ListMap[String, Expression]) extends Expression

case class MemberAccess(range: OffsetPointerRange, target: Expression, name: String) extends Expression

case class VariableReference(range: OffsetPointerRange, name: String) extends Expression

case class Call(range: OffsetPointerRange, target: Expression, arguments: Vector[Expression]) extends Expression {

}

case class ExpressionStatement(range: OffsetPointerRange, expression: Expression) extends Statement {

}

case class JavaScriptFile(range: OffsetPointerRange, statements: Vector[Statement]) extends FileElement {
}

case class Declaration(range: OffsetPointerRange, name: String, value: Expression) extends Statement {

}

case class WholeNumber(range: OffsetPointerRange, int: Int) extends Expression {

}

case class Modulo(range: OffsetPointerRange, left: Expression, right: Expression) extends Expression {

}
case class Subtraction(range: OffsetPointerRange, left: Expression, right: Expression) extends Expression {

}
case class Equals(range: OffsetPointerRange, left: Expression, right: Expression) extends Expression {}
case class Multiplication(range: OffsetPointerRange, left: Expression, right: Expression) extends Expression {

}
case class Addition(range: OffsetPointerRange, left: Expression, right: Expression) extends Expression {

}

trait Expression extends FileElement {

}

case class Argument(range: OffsetPointerRange, name: String, varArgs: Boolean) extends FileElement {

}

case class Lambda(range: OffsetPointerRange, arguments: Vector[Argument], body: Vector[Statement]) extends Expression {
}

trait Statement extends FileElement {

}