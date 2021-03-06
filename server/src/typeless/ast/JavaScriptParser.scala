package typeless.ast

import miksilo.editorParser.parsers.editorParsers.{History, LeftRecursiveCorrectingParserWriter, OffsetPointerRange}
import miksilo.editorParser.parsers.strings.{CommonStringReaderParser, WhitespaceParserWriter}

import scala.collection.immutable.ListMap

//noinspection TypeAnnotation
object JavaScriptParser extends CommonStringReaderParser
  with LeftRecursiveCorrectingParserWriter
  with WhitespaceParserWriter {

  // override val maxListDepth: Int = 2000

  override def trivia: Parser[String] = {
    val lineComment = parseRegex("""//[^\n]*""".r, "line comment")
    lineComment | whiteSpace
  }

  val booleanParser = ("true" ~> succeed(true) | "false" ~> succeed(false)).
    withSourceRange((range, value) => new BooleanLiteral(range, value))
  val stringLiteralParser: Parser[StringLiteral] = parseRegex(""""([^"])*"""".r, "string literal").
    map(s => s.substring(1, s.length - 1)).
    withSourceRange((range, value) => new StringLiteral(range, value))
  //val stringLiteralParser: Parser[StringLiteral] = stringLiteral.withSourceRange((range, value) => StringLiteral(range, value))
  val numberLiteral = wholeNumber.withSourceRange((range, number) => new WholeNumber(range, Integer.parseInt(number)))

  val keywords = Set("if", "else", "this", "true", "false", "function", "new", "const", "return")
  val validIdentifier = parseIdentifier.
    filter(i => !keywords.contains(i), s => s"$s is a keyword")
  val variableExpression: Parser[VariableReference] = validIdentifier.
    withSourceRange((range, name) => new VariableReference(range, name))
  val thisParser: Parser[ThisReference] = ("this": Parser[String]).withSourceRange((range, _) => new ThisReference(range))

  val nonRecursiveExpression = booleanParser | numberLiteral | stringLiteralParser | thisParser | variableExpression

  //  lazy val array = ("[" ~> valueParser.manySeparated(",", "value") ~< "]").
  //    withSourceRange((range, value) => JsonArray(Some(range), value.toArray))
  lazy val objectMember = name ~< ":" ~ expression
  lazy val objectLiteral = (literal("{", 2 * History.missingInputPenalty) ~>
    objectMember.manySeparated(",", "member") ~< "}").
    withSourceRange((range, value) => new ObjectLiteral(range, ListMap.from(value)))
  val nonAssociatedExpression = nonRecursiveExpression | objectLiteral

  val parenthesis: Parser[Expression] = "(" ~> expression ~< ")"
  val expression21: Parser[Expression] = parenthesis | nonAssociatedExpression

  val name = (validIdentifier | Fallback("", "name")).withSourceRange((range, name) => new Name(range, name))
  val bracketAccess: Parser[BracketAccess] = (expression20 ~< "[" ~ expression ~< "]").
    withSourceRange((range, t) => new BracketAccess(range, t._1, t._2))
  val dotAccess: Parser[DotAccess] = (expression20 ~< "." ~ name).
    withSourceRange((range, t) => new DotAccess(range, t._1, t._2))

  val newParser: Parser[New] = ("new" ~> expression20 ~< "(" ~ expression.manySeparated(",", "argument") ~< ")").
    withSourceRange((range, t) => new New(range, t._1, t._2))

  val callExpression: Parser[Call] = (expression20 ~< "(" ~ expression.manySeparated(",", "argument") ~< ")").
    withSourceRange((range, t) => new Call(range, t._1, t._2))
  lazy val expression20: Parser[Expression] = new Lazy(newParser | callExpression | dotAccess | bracketAccess | expression21)

  val negateParser = ("!" ~> expression17).withSourceRange((range, expr) => new Negate(range, expr))
  lazy val expression17: Parser[Expression] = new Lazy(negateParser | expression20)

  val multiplication = (expression15 ~< "*" ~ expression15).withSourceRange((range, t) => new Multiplication(range, t._1, t._2))
  val modulo = (expression15 ~< "%" ~ expression15).withSourceRange((range, t) => new Modulo(range, t._1, t._2))
  lazy val expression15: Parser[Expression] = new Lazy(multiplication | modulo | expression17)

  val addition = (expression14 ~< "+" ~ expression14).withSourceRange((range, t) => new Addition(range, t._1, t._2))
  val subtraction = (expression14 ~< "-" ~ expression14).withSourceRange((range, t) => new Subtraction(range, t._1, t._2))
  lazy val expression14: Parser[Expression] = new Lazy(addition | subtraction | expression15)

  val moreThanParser = (expression12 ~< ">" ~ expression12).withSourceRange((range, t) => new MoreThan(range, t._1, t._2))
  val lessThanParser = (expression12 ~< "<" ~ expression12).withSourceRange((range, t) => new LessThan(range, t._1, t._2))
  lazy val expression12: Parser[Expression] = new Lazy(lessThanParser | moreThanParser | expression14)

  val equalsParser = (expression11 ~< "==" ~ expression11).withSourceRange((range, t) => new Equals(range, t._1, t._2))
  lazy val expression11: Parser[Expression] = new Lazy(equalsParser | expression12)

  val assignmentTarget: Parser[AssignmentTarget] = dotAccess | variableExpression | bracketAccess
  val assignment: Parser[Assignment] = (assignmentTarget ~< "=" ~ expression3).
    withSourceRange((range, t) => new Assignment(range, t._1, t._2))
  lazy val expression3: Parser[Expression] = new Lazy(assignment | expression11)

  val argument: Parser[Argument] = ("...".option ~ validIdentifier).
    withSourceRange((range, name) => new Argument(range, name._2, name._1.nonEmpty))
  val arguments: Parser[Vector[Argument]] = "(" ~> argument.manySeparated(",", "argument") ~< ")"
  lazy val lambdaBody = body | expression.map(expr => Vector(new ReturnStatement(expr.range, expr)))
  val lambdaArguments: Parser[Vector[Argument]] = arguments | argument.map(a => Vector(a))
  lazy val lambda: Parser[Lambda] = (lambdaArguments ~< "=>" ~ lambdaBody).
    withSourceRange((range, t) => new Lambda(range, t._1, t._2, None, None))
  val expression0: Parser[Expression] = lambda | expression3

  lazy val expression: Parser[Expression] = new Lazy(expression0)

  // STATEMENTS

  val statementEnd = ";" | "\n"

  val jsDocComment = RegexParser("""/\*\*+[^*]*\*+(?:[^/*][^*]*\*+)*/""".r, "block comment")

  val functionDeclaration = (jsDocComment.option ~ ("function" ~> name ~ arguments ~ body).
    withSourceRange((range, t) => (range, t))).map({ case (doc, (range, t)) => new Declaration(range, t._1._1,
      new Lambda(range, t._1._2, t._2, Some(t._1._1.name), doc)) })

  val declaration: Parser[Declaration] = ("const" ~> name ~< "=" ~ expression ~< statementEnd).
    withSourceRange((range, t) => new Declaration(range, t._1, t._2))
  val expressionStatement: Parser[ExpressionStatement] = (expression ~< statementEnd).
    withSourceRange((range, expr) => new ExpressionStatement(range, expr))

  val returnStatement = ("return" ~> expression ~< statementEnd).withSourceRange((range, expr) => new ReturnStatement(range, expr))

  lazy val statement: Parser[Statement] = new Lazy(ifStatement | declaration | functionDeclaration | expressionStatement | returnStatement)
  lazy val statements = statement.*

  val singleStatementOrBody = statement.map(s => Vector(s)) | "{" ~> statements ~< "}"
  val elsePart = ("else" ~> singleStatementOrBody).option.map(o => o.getOrElse(Seq.empty))
  val ifStatement = ("if" ~> "(" ~> expression ~< ")" ~ singleStatementOrBody ~ elsePart).
    withSourceRange((range, t) => new IfStatement(range, t._1._1, t._1._2, t._2))

  lazy val body = "{" ~> statements ~< "}"
  val file: Parser[JavaScriptFile] = statements.withSourceRange((range, body) => new JavaScriptFile(range, body))

  val javaScript = file ~< trivias
}
