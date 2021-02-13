import miksilo.editorParser.parsers.editorParsers.{Position, SourceRange, TextEdit}
import miksilo.languageServer.server.LanguageServerTest
import miksilo.lspprotocol.lsp._
import org.scalatest.funsuite.AnyFunSuite
import typeless.server.TypelessLanguageServer

class JavaScriptLanguageTest extends AnyFunSuite with LanguageServerTest {

  val server = new TypelessLanguageServer()

  ignore("demo code completion") {
    val program =
      """function getNameTest() {
        |  const person = new Person("Remy", 32);
        |  assert(isPresenting(person));
        |}
        |
        |function Person(name, age) {
        |  this.name = name;
        |  this.age = age;
        |}
        |
        |function isPresenting(person) {
        |  return person. == "Remy";
        |}
        |""".stripMargin

    ???
  }

  test("demo hover and assert") {
    val program =
      """function getNameTest() {
        |  const person = new Person("Remy", 32);
        |  assert(isPresenting(person));
        |}
        |
        |function Person(name, age) {
        |  this.name = name;
        |  this.age = age;
        |}
        |
        |function isPresenting(person) {
        |  return person.name == "Remi";
        |}
        |""".stripMargin

    val (diagnostics, document) = openAndCheckDocument(server, program)
    val assertInformation = RelatedInformation(FileRange(document.uri, SourceRange(Position(2,2),Position(2,30))), "assertion")
    val diagnostic = Diagnostic(SourceRange(Position(11,9),Position(11,30)), Some(1), "Expression was 'false' while 'true' was expected",
      relatedInformation = Seq(assertInformation))
    assertResult(Seq(diagnostic))(diagnostics)

    val expectedPersonHover = Hover(Seq(new RawMarkedString("{ name: Remy, age: 32 }")),
      Some(HumanPosition(12, 10).span(6)))
    val personHover: Hover = hover(server, program, HumanPosition(12, 11)).get
    assertResult(expectedPersonHover)(personHover)

    val expectedNameHover = Hover(Seq(new RawMarkedString("Remy")),
      Some(HumanPosition(12, 10).span(11)))
    val nameHover: Hover = hover(server, program, HumanPosition(12, 17)).get
    assertResult(expectedNameHover)(nameHover)
  }

  test("assert diagnostics") {
    val program =
      """function fooTest() {
        |  assert(3);
        |}
        |""".stripMargin
    val (diagnostics, document) = openAndCheckDocument(server, program)

    // TODO assert more
    assert(diagnostics.size == 1)
  }

  test("first example test") {

    val program =
      """const pipeTest = () => {
        |  const plusOneTimesTwoMinusOne = pipe(x => x + 1, x => x * 2, x => x - 1);
        |  assert.strictEqual(plusOneTimesTwoMinusOne(0), 1)
        |  assert.strictEqual(plusOneTimesTwoMinusOne(1), 3)
        |}
        |const pipe = (...fns) => p => fns.reduce((acc, cur) => cur(acc), p);
        |
        |const isNameOfEvenLengthTest = () => {
        |  assert(isNameOfEvenLength({ name: "Remy" }))
        |  assert.strictEqual(isNameOfEvenLength({ name: "Elise" }), false)
        |}
        |const isNameOfEvenLength = pipe(person => person.name, str => str.length, x => x % 2 == 0)
        |
        |const isRemyEven = isNameOfEvenLength({ name: "Remy" })
        |""".stripMargin

    val (diagnostics, document) = openAndCheckDocument(server, program)
    assert(diagnostics.isEmpty)
    val fnsCompletion = server.complete(DocumentPosition(document, Position(5, 34)))
    assert(Seq(CompletionItem("reduce", detail = Some("native function"))).diff(fnsCompletion.items).isEmpty)

//    val fnsCompletion = server.hoverRequest(DocumentPosition(document, Position(4, 44)))
//    assert(Seq(CompletionItem("reduce")).diff(fnsCompletion.items).isEmpty)
  }

  test("basic crash test") {
    val program =
      """const fibonacciTest = () => {
        |  assert.equal(fibonacci(3), 2);
        |}
        |
        |const fibonacci = (n) => {
        |  return n.foo;
        |}
        |""".stripMargin
    val expected = Seq(Diagnostic(HumanPosition(6, 10).span(1), Some(1), "Expected value with fields but got '3'"))
    val diagnostics = getDiagnostics(server, program)
    assertResult(expected)(diagnostics)
  }

  test("missing property") {
    val program =
      """const fibonacciTest = () => {
        |  assert.equal(getFoo({ foo: "Remy" }), "Remy");
        |}
        |
        |const getFoo = (n) => {
        |  return n.bar;
        |};
        |""".stripMargin
    val expected = Seq(Diagnostic(HumanPosition(6, 10).span(5), Some(1), "The member 'bar' is not available on value '{ foo: Remy }'"))
    val diagnostics = getDiagnostics(server, program)
    assertResult(expected)(diagnostics)
  }

  // TODO, suggest example values in diagnostic
  test("problem in calling function") {
    val program =
      """const highLevelTest = () => {
        |  fibonacci("hello");
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

    val (diagnostics, document) = openAndCheckDocument(server, program)
    val relatedInformation = RelatedInformation(FileRange(document.uri, HumanPosition(13,20).span(3)),
      "Expected value that supports subtraction but got 'hello'")
    val expected = Seq(Diagnostic(HumanPosition(2, 3).span(18), Some(1),
      "Function call failed with arguments 'hello'", relatedInformation = Seq(relatedInformation)))
    assertResult(expected)(diagnostics)

    val result2 = server.gotoDefinition(DocumentPosition(document, HumanPosition(7, 12))).head.range
    assertResult(HumanPosition(10, 7).span(9))(result2)
  }

  test("validating values") {
    val program =
      """const squareTest = () => {
        |  assert.strictEqual(square(2), 4);
        |  assert.strictEqual(square(3), 9);
        |}
        |
        |const square = (x) => {
        |  return x + x;
        |}
        |""".stripMargin

    val (diagnostics, document) = openAndCheckDocument(server, program)
    val related = Seq(
      RelatedInformation(FileRange(document.uri, SourceRange(Position(2,2),Position(2,34))), "assertion"),
      RelatedInformation(FileRange(document.uri, HumanPosition(3, 33).span(1)), "expected value: 9"))
    val expected = Seq(Diagnostic(HumanPosition(7, 10).span(5), Some(1),
      "Expression was '6' while '9' was expected", relatedInformation = related))
    assertResult(expected)(diagnostics)
  }

  test("validating values from object members") {
    val program =
      """const nameTest = () => {
        |  assert.strictEqual((new Person("Remy")).name, "Remy");
        |}
        |
        |const Person = (name) => {
        |  this.name = "Elise";
        |}
        |""".stripMargin

    val (diagnostics, document) = openAndCheckDocument(server, program)
    val related = Seq(
      RelatedInformation(FileRange(document.uri, SourceRange(Position(1,2),Position(1,55))), "assertion"),
      RelatedInformation(FileRange(document.uri, HumanPosition(2, 49).span(6)), "expected value: Remy"))
    val expected = Seq(Diagnostic(HumanPosition(6, 15).span(7), Some(1),
      "Expression was 'Elise' while 'Remy' was expected", relatedInformation = related))
    assertResult(expected)(diagnostics)
  }

  test("basic goto definition") {
    val program =
      """const variablesTest = () => {
        |  const x = 2;
        |  return x + 3;
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
        |}
        |""".stripMargin

    val expected = Seq(HumanPosition(4, 7).span(4))
    val definitions = gotoDefinition(server, program, HumanPosition(5, 8)).map(d => d.range)
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
        |  assert.strictEquals(getName(person), remy);
        |};
        |
        |const getName = (person) => {
        |  return person.
        |};
        |""".stripMargin
    // TODO Assert that there is only a parsing diagnostic but not another one
    // assert(getDiagnostics(server, program).isEmpty)

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

  test("hover") {
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
        |  canStop(true);
        |}
        |
        |const canStopTest = () => {
        |  canStop(false);
        |}
        |
        |const canStop = (forever) => {
        |  if (forever)
        |    canStop(forever);
        |}
        |""".stripMargin

    val (diagnostics, document) = openAndCheckDocument(server, program)
    val related = RelatedInformation(FileRange(document.uri, HumanPosition(11, 5).span(16)), "Call takes too long for a test")
    val expected = Seq(Diagnostic(HumanPosition(2, 3).span(13), Some(1), "Function call failed with arguments 'true'", relatedInformation = Seq(related)))
    assertResult(expected)(diagnostics)
  }
}