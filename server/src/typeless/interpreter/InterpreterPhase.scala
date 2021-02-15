package typeless.interpreter

import miksilo.editorParser.parsers.SourceElement
import miksilo.languageServer.core.language.{Compilation, Phase, SourcePathFromElement}
import miksilo.languageServer.core.smarts.FileDiagnostic
import typeless.ChainElement
import typeless.ast.{JavaScriptFile, Lambda}
import typeless.server.JavaScriptCompilation

object InterpreterPhase {

  val phase = Phase("interpreter", "where the interpreting happens", interpret)

  val maxCallDepth = 4

  def interpret(compilation: Compilation): Unit = {

    val uri = compilation.rootFile.get
    val javaScriptCompilation = compilation.asInstanceOf[JavaScriptCompilation]
    val programPath = compilation.program.asInstanceOf[ChainElement]
    val program = programPath.sourceElement.asInstanceOf[JavaScriptFile]

    var pathsByElement = Map.empty[SourceElement, ChainElement]
    programPath.foreach(e => pathsByElement += e.sourceElement -> e)

    val defaultState = StandardLibrary.createState()

    val scan = new Scan()
    val configuration = RunConfiguration(uri, maxCallDepth = maxCallDepth,
      allowUndefinedPropertyAccess = false, pathsByElement, mode = scan)
    val context = new Context(configuration, scope = defaultState)
    val result = program.evaluate(context)
    result match {
      case e: UserExceptionResult =>
        compilation.diagnostics += FileDiagnostic(uri, e.toDiagnostic)
      case _ =>
    }

    val rootEnvironment = context.scope.environment
    val functionsByName: Map[String, Closure] = rootEnvironment.flatMap(s => {
      s._2 match {
        case closure: Closure => Seq(s._1 -> closure)
        case _ => Seq.empty
      }
    }).toMap
    val testKeyword = "Test"
    val testsByName: Map[String, Closure] = functionsByName.flatMap(s => {
      if (s._1.endsWith(testKeyword) && s._2.lambda.arguments.isEmpty) {
        Seq(s._1 -> s._2)
      } else {
        Seq.empty
      }
    })
    javaScriptCompilation.tests = testsByName
    val functionsWithTests: Map[Lambda, Closure] = testsByName.flatMap(test => {
      val associatedFunctionName = test._1.dropRight(testKeyword.length)
      functionsByName.get(associatedFunctionName).map(f => (f.lambda, test._2)).toIterable
    })

    val tests = testsByName.values.map(c => c.lambda).toSet
    context.functionCorrectness = Some(new FunctionCorrectness(functionsWithTests, tests))

    javaScriptCompilation.context = context

    testsByName.foreach(test => {
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
