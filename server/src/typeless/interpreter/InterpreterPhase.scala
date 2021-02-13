package typeless.interpreter

import miksilo.languageServer.core.language.{Compilation, Phase, SourcePathFromElement}
import miksilo.languageServer.core.smarts.FileDiagnostic
import typeless.ChainElement
import typeless.ast.JavaScriptFile
import typeless.server.JavaScriptCompilation

object InterpreterPhase {

  val phase = Phase("interpreter", "where the interpreting happens", interpret)

  val maxCallDepth = 4

  def interpret(compilation: Compilation): Unit = {

    val uri = compilation.rootFile.get
    val javaScriptCompilation = compilation.asInstanceOf[JavaScriptCompilation]
    val program = compilation.program.asInstanceOf[ChainElement].sourceElement.asInstanceOf[JavaScriptFile]
    val defaultState = StandardLibrary.createState()

    val scan = new Scan()
    val configuration = RunConfiguration(uri, maxCallDepth = maxCallDepth,
      allowUndefinedPropertyAccess = false, mode = scan)
    val context = new Context(configuration, scope = defaultState)
    val result = program.evaluate(context)
    result match {
      case e: UserExceptionResult =>
        compilation.diagnostics += FileDiagnostic(uri, e.toDiagnostic)
      case _ =>
    }

    val rootEnvironment = context.scope.environment
    val functions: Map[String, Closure] = rootEnvironment.flatMap(s => {
      s._2 match {
        case closure: Closure => Seq(s._1 -> closure)
        case _ => Seq.empty
      }
    }).toMap
    val testKeyword = "Test"
    val tests: Map[String, Closure] = functions.flatMap(s => {
      if (s._1.endsWith(testKeyword) && s._2.lambda.arguments.isEmpty) {
        Seq(s._1 -> s._2)
      } else {
        Seq.empty
      }
    })
    javaScriptCompilation.tests = tests
    val functionsWithTests: Map[Closure, Closure] = tests.flatMap(test => {
      functions.get(test._1.dropRight(testKeyword.length)).map(f => (f, test._2)).toIterable
    })

    context.functionCorrectness = Some(new FunctionCorrectness(functionsWithTests))

    javaScriptCompilation.context = context

    tests.foreach(test => {
      val result = context.runTest(test._2)
      result match {
        case e: UserExceptionResult =>
          compilation.diagnostics += FileDiagnostic(compilation.rootFile.get, e.toDiagnostic)
        case _ =>
      }
    })

    javaScriptCompilation.refs = new References(scan.referenceToDefinition)
  }
}
