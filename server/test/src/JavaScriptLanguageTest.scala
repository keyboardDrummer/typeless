import miksilo.editorParser.parsers.editorParsers.{Position, SourceRange, TextEdit}
import miksilo.languageServer.server.LanguageServerTest
import miksilo.lspprotocol.lsp._
import org.scalatest.funsuite.AnyFunSuite
import typeless.server.TypelessLanguageServer

import scala.util.Random

class JavaScriptLanguageTest extends AnyFunSuite with LanguageServerTest {

  val server = new TypelessLanguageServer()


  ignore("first example test") {

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

    val (diagnostics, document) = openAndCheckDocument(server, program)
    assert(diagnostics.isEmpty)
    val fnsCompletion = server.complete(DocumentPosition(document, Position(4, 44)))
    assert(Seq(CompletionItem("reduce")).diff(fnsCompletion.items).isEmpty)

//    val fnsCompletion = server.hoverRequest(DocumentPosition(document, Position(4, 44)))
//    assert(Seq(CompletionItem("reduce")).diff(fnsCompletion.items).isEmpty)
  }

  test("basic crash test") {
    val program =
      """// The function 'fibonacciTest' is recognized as a test.
        |const fibonacciTest = () => {
        |  assert.equal(fibonacci(3), 2);
        |}
        |
        |const fibonacci = (n) => {
        |  return n.foo;
        |  // The member 'foo' is not available on value '3'.
        |}
        |""".stripMargin
    val expected = Seq(Diagnostic(HumanPosition(7, 10).span(1), Some(1), "Expected value with fields but got '3'"))
    val diagnostics = getDiagnostics(server, program)
    assertResult(expected)(diagnostics)
  }

  test("missing property") {
    val program =
      """// The function 'fibonacciTest' is recognized as a test.
        |const fibonacciTest = () => {
        |  assert.equal(getFoo({ foo: "Remy" }), "Remy");
        |}
        |
        |const getFoo = (n) => {
        |  return n.bar;
        |  // The member 'foo' is not available on value '3'.
        |};
        |""".stripMargin
    val expected = Seq(Diagnostic(HumanPosition(7, 10).span(5), Some(1), "The member 'bar' is not available on value '{ foo: Remy }'"))
    val diagnostics = getDiagnostics(server, program)
    assertResult(expected)(diagnostics)
  }

  ignore("problem in calling function") {
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

    // TODO, suggest example values in diagnostic
    val uri = Random.nextInt().toString
    val relatedInformation = RelatedInformation(FileRange(uri, HumanPosition(14,20).span(3)),
      "Expected value that supports subtraction but got 'hello'")
    val expected = Seq(Diagnostic(HumanPosition(2, 3).span(18), Some(1),
      "Function call failed with arguments 'hello'", relatedInformation = Seq(relatedInformation)))
    val (diagnostics, document) = openAndCheckDocument(server, program, uri)
    assertResult(expected)(diagnostics)

    val result2 = server.gotoDefinition(DocumentPosition(document, HumanPosition(8, 12))).head.range
    assertResult(HumanPosition(11, 7).span(9))(result2)
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

    val (diagnostics, document) = openAndCheckDocument(server, program)
    val related = RelatedInformation(FileRange(document.uri, HumanPosition(3, 33).span(1)), "9")
    val expected = Seq(Diagnostic(HumanPosition(8, 10).span(5), Some(1),
      "The value '9' was expected but it was '6'", relatedInformation = Seq(related)))
    assertResult(expected)(diagnostics)
  }

  // TODO remove the need for the parenthesis by fixing parser priorities.
  test("validating values from object members") {
    val program =
      """const nameTest = () => {
        |  assert.strictEqual((new Person("Remy")).name, "Remy");
        |}
        |
        |const Person = (name) => {
        |  this.name = "Elise";
        |  // The value "Remy" was expected but it was "Elise".
        |}
        |""".stripMargin


    val (diagnostics, document) = openAndCheckDocument(server, program)
    val related = RelatedInformation(FileRange(document.uri, HumanPosition(2, 49).span(6)), "Remy")
    val expected = Seq(Diagnostic(HumanPosition(6, 15).span(7), Some(1),
      "The value 'Remy' was expected but it was 'Elise'", relatedInformation = Seq(related)))
    assertResult(expected)(diagnostics)

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

    val expected = Seq(HumanPosition(2, 9).span(1))
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

    val expected = Seq(HumanPosition(4, 7).span(4))
    val definitions = gotoDefinition(server, program, HumanPosition(6, 8)).map(d => d.range)
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

    val expected = Seq(HumanPosition(4, 7).span(4))
    val definitions = gotoDefinition(server, program,  HumanPosition(5, 8)).map(d => d.range)
    assertResult(expected)(definitions)
  }

  test("code completion on variables") {
    val program =
      """const abcGlobal = "hello";
        |const completionTest = () => {
        |  abc;
        |
        |  const abcLocal = "goodbye";
        |  abc(abcG);
        |
        |};
        |""".stripMargin
    val globalItem = CompletionItem("abcGlobal", detail = Some("hello"))
    val localItem = CompletionItem("abcLocal", detail = Some("goodbye"))
    assertResult(Seq(globalItem))(complete(server, program, HumanPosition(3, 6)).items)

    assertResult(Seq(localItem, globalItem))(complete(server, program, HumanPosition(6, 6)).items)
    assertResult(Seq(globalItem))(complete(server, program, HumanPosition(6, 7)).items)

    // TODO enable accurate code completion between statements by inserting No-op statements where needed.
    // assertResult(Seq(globalItem))(complete(server, program, HumanPosition(4, 4)).items)
    // assertResult(Seq(globalItem, localItem))(complete(server, program, HumanPosition(7, 4)).items)
  }

  test("code completion across methods") {
    val program =
      """const getNameTest = () => {
        |  const person = { name: "Remy", age: 32 };
        |  assert.strictEquals(getName(person), "Remy");
        |};
        |
        |const getName = (person) => {
        |  return person.
        |  // After the dot, the completion results 'name' and 'age' are shown.
        |};
        |""".stripMargin
    val expected = Seq(CompletionItem("name", detail = Some("Remy")), CompletionItem("age", detail = Some("32")))
    val definitions = complete(server, program, HumanPosition(7, 17)).items
    assertResult(expected)(definitions)
  }

  test("references, local variable, global variable and object member") {
    val program =
      """const getNameTest = () => {
        |  const person = { name: "Remy", age: 32 };
        |  assert.strictEquals(getName(person), "Remy");
        |};
        |
        |const getName = (person) => {
        |  return person.name
        |};
        |""".stripMargin
    val expected = Seq(HumanPosition(3, 31).span(6))
    val definitions = references(server, program, HumanPosition(2, 9), includeDeclaration = false).map(fr => fr.range)
    assertResult(expected)(definitions)

    val expected2 = Seq(HumanPosition(7, 17).span(4))
    val definitions2 = references(server, program, HumanPosition(2, 20), includeDeclaration = false).map(fr => fr.range)
    assertResult(expected2)(definitions2)
  }

  // TODO simplify test program
  test("rename, local variable, global variable and object member") {
    val program =
      """const getNameTest = () => {
        |  const person = { name: "Remy", age: 32 };
        |  assert.strictEquals(getName(person), "Remy");
        |};
        |
        |const getName = (person) => {
        |  return person.name
        |};
        |""".stripMargin
    val expected = Set(
      TextEdit(HumanPosition(2, 9).span(6), "remy"),
      TextEdit(HumanPosition(3, 31).span(6), "remy"))
    val edits = rename(server, program, HumanPosition(2, 9), "remy").changes.head._2.toSet
    assertResult(expected)(edits)
  }

  // TODO move to Miksilo
  def hover(server: LanguageServer, program: String, position: Position): Option[Hover] = {
    val document = openDocument(server, program)
    server.asInstanceOf[HoverProvider].hover(DocumentPosition(document, position))
  }

  ignore("hover") {
    val program =
      """const personTest = () => {
        |  const person = { name: "Remy", age: 32 };
        |  person;
        |};
        |""".stripMargin
    val expected = Hover(Seq(new RawMarkedString("{ name: Remy, age: 32 }")),
      Some(HumanPosition(3, 3).span(6)))
    val result: Hover = hover(server, program, HumanPosition(3, 5)).get
    assertResult(expected)(result)
  }

  // TODO add test that verifies tests can't modify global state

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
        |""".stripMargin
  }

  test("infinite recursion stops") {
    val program =
      """const neverStopsTest = () => {
        |  neverStops();
        |}
        |
        |const neverStops = () => {
        |  neverStops();
        |}
        |""".stripMargin

    val expected = Seq(Diagnostic(HumanPosition(6, 3).span(12), Some(1), "Call takes too long for a test"))
    val diagnostics = getDiagnostics(server, program)
    assertResult(expected)(diagnostics)
  }
}