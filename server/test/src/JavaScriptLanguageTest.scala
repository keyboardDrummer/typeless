import miksilo.editorParser.parsers.editorParsers.SourceRange
import miksilo.languageServer.core.language.Language
import miksilo.languageServer.server.{LanguageServerTest, MiksiloLanguageServer}
import miksilo.lspprotocol.lsp.{Diagnostic, HumanPosition}
import org.scalatest.funsuite.AnyFunSuite

class ExampleExpressionLanguageTest extends AnyFunSuite with LanguageServerTest {

  val language: Language = JavaScriptLanguage
  val server = new MiksiloLanguageServer(language)

  test("first example test") {

    val program =
      """const pipeTest = () => {
        |  const plusOneTimesTwoMinusOne = pipe(x => x + 1, x => x * 2, x => x - 1);
        |  assert(1, plusOneTimesTwoMinusOne(0))
        |  assert(3, plusOneTimesTwoMinusOne(1))
        |}
        |const pipe = (...fns) => p => fns.reduce((acc, cur) => cur(acc), p);
        |// When typing 'fns.' code completion for arrays is shown.
        |// When typing 'reduce(', example arguments like 'x => x + 1' are shown.
        |// Hovering over 'cur', 'acc' or 'p' shows us the values these variables can get when running the test.
        |
        |const isNameOfEvenLengthTest = () => {
        |  assert(true, isNameOfEvenLength({ name: "Remy" }))
        |  assert(false, isNameOfEvenLength({ name: "Elise" }))
        |}
        |const isNameOfEvenLength = pipe(person => person.name, str => str.length, x => x % 2 == 0)
        |// When typing 'pipe(', example arguments to pipe such as 'x => x + 1' are shown.
        |// When typing 'person.', code completion suggests 'name'.
        |// When typing 'str.', code completion for string members is shown.
        |
        |const isRemyEven = isNameOfEvenLength({ name: "Remy" })
        |// Hovering over isRemyEven shows us that it can have the values 'true' and 'false'
        |""".stripMargin

    val diagnostics = getDiagnostics(server, program)
    assert(diagnostics.isEmpty)
  }

  test("basic crash test") {
    val program =
      """// The function 'fibonacciTest' is recognized as a test.
        |const fibonacciTest = () => {
        |  assert.equal(fibonacci(3), 3);
        |  assert.equal(fibonacci(4), 5);
        |}
        |
        |const fibonacci = (n) => {
        |  return n.foo;
        |  // The member 'foo' is not available on value '3'.
        |}
        |""".stripMargin
    val expected = Seq(Diagnostic(SourceRange(HumanPosition(8, 10), HumanPosition(8, 15)), Some(1), "The member 'foo' is not available on value '3'"))
    val diagnostics = getDiagnostics(server, program)
    assertResult(expected)(diagnostics)
  }
}