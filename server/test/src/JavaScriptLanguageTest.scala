import miksilo.editorParser.parsers.editorParsers.SourceRange
import miksilo.languageServer.core.language.Language
import miksilo.languageServer.server.{LanguageServerTest, MiksiloLanguageServer}
import miksilo.lspprotocol.lsp.{CompletionItem, Diagnostic, FileRange, HumanPosition}
import org.scalatest.funsuite.AnyFunSuite
import typeless.server.TypelessLanguageServer

class ExampleExpressionLanguageTest extends AnyFunSuite with LanguageServerTest {

  val server = new TypelessLanguageServer()

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
        |  assert.equal(fibonacci(3), 2);
        |  assert.equal(fibonacci(4), 3);
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

  test("problem in calling function") {
    val program =
      """const highLevelTest = () => {
        |  fibonacci("hello");
        |  // The value "hello" passed to fibonacci is not valid. Examples of valid values are: 2, 3
        |};
        |
        |const fibonacciTest = () => {
        |  assert(fibonacci(3) == 2);
        |  assert(fibonacci(4) == 3);
        |};
        |
        |const fibonacci = (n) => {
        |  if (n == 0) return 0;
        |  if (n == 1) return 1;
        |  return fibonacci(n-1) + fibonacci(n-2);
        |};
        |""".stripMargin

    // TODO, add linked diagnostic to actual failure location in fibonacci.
    // TODO, suggest example values in diagnostic
    val expected = Seq(Diagnostic(SourceRange(HumanPosition(2, 3), HumanPosition(2, 21)), Some(1), "This function call failed with arguments 'hello'."))
    val diagnostics = getDiagnostics(server, program)
    assertResult(expected)(diagnostics)
  }

  test("validating values") {
    val program =
      """const squareTest = () => {
        |  assert.strictEqual(square(2), 4);
        |  assert.strictEqual(square(3), 9);
        |  // Related location for the error below.
        |}
        |
        |const square = (x) => {
        |  return x + x;
        |  // The value 9 was expected but it was 6, with a link to the assert.strictEqual that caused this error.
        |}
        |""".stripMargin
    // TODO add a related location to the Diagnostic
    val expected = Seq(Diagnostic(SourceRange(HumanPosition(8, 10), HumanPosition(8, 15)), Some(1), "The value '9' was expected but it was '6'."))
    val diagnostics = getDiagnostics(server, program)
    assertResult(expected)(diagnostics)
  }

  test("validating values from object members") {
    val program =
      """const nameTest = () => {
        |  assert.strictEqual(new Person("Remy").name, "Remy");
        |}
        |
        |const Person = (name) => {
        |  this.name = "Elise";
        |  // The value "Remy" was expected but it was "Elise".
        |}
        |""".stripMargin

    val expected = Seq(Diagnostic(SourceRange(HumanPosition(6, 15), HumanPosition(6, 22)), Some(1), "The value 'Remy' was expected but it was 'Elise'."))
    val diagnostics = getDiagnostics(server, program)
    assertResult(expected)(diagnostics)
  }

  test("basic goto definition") {
    val program =
      """const variablesTest = () => {
        |  const x = 2;
        |  return x + 3;
        |  // Goto definition on x will jump to "x" in "const x";
        |}
        |""".stripMargin

    val expected = Seq(SourceRange(HumanPosition(2, 9), HumanPosition(2, 10)))
    val definitions = gotoDefinition(server, program,  HumanPosition(3, 10)).map(d => d.range)
    assertResult(expected)(definitions)
  }

  test("first dot assignment determines definition") {
    val program =
      """const memberAssignmentsTest = () => {
        |  const obj = {};
        |  obj["name"] = "Jeroen";
        |  obj.name = "Remy";
        |  obj.name = "Elise";
        |  obj.name
        |  // Goto definition on name will jump to "name" in the assignment "obj.name = 'Remy'";
        |}
        |""".stripMargin
    // TODO enable jumping a position forward in a line to get a range
    val expected = Seq(SourceRange(HumanPosition(4, 7), HumanPosition(4, 11)))
    val definitions = gotoDefinition(server, program,  HumanPosition(6, 8)).map(d => d.range)
    assertResult(expected)(definitions)
  }

  ignore("deleting a member allows redefining it") {
    val program =
      """const memberAssignmentsTest = () => {
        |  const obj = { name: "Remy" };
        |  delete obj.name;
        |  obj.name = "Jacques";
        |  obj.name
        |  // Goto definition on name will jump to "name" in the assignment "obj.name = 'Jacques'";
        |}
        |""".stripMargin
    // TODO enable jumping a position forward in a line to get a range
    val expected = Seq(SourceRange(HumanPosition(4, 7), HumanPosition(4, 11)))
    val definitions = gotoDefinition(server, program,  HumanPosition(5, 8)).map(d => d.range)
    assertResult(expected)(definitions)
  }

  test("code completion across methods") {
    val program =
      """const completionTest = () => {
        |  const person = { name: "Remy", age: 32 };
        |  assert.strictEquals(getName(person), "Remy");
        |};
        |
        |const getName = (person) => {
        |  return person.
        |  // After the dot, the completion results 'name' and 'age' are shown.
        |};
        |```
        |""".stripMargin
    val expected = Seq(CompletionItem("name"), CompletionItem("age"))
    val definitions = complete(server, program, HumanPosition(7, 18)).items
    assertResult(expected)(definitions)
  }

  ignore("call signature help") {
    val program =
      """const fibonacciTest = () => {
        |  fibonacci(2)
        |  fibonacci(3)
        |}
        |
        |const fibonacci = (n) => {}
        |
        |const fibonacciUser = () => {
        |  fibonacci(
        |  // After the opening parenthesis, Typeless will suggest calling fibonacci with either 2 or 3.
        |}
        |```
        |""".stripMargin
  }
}