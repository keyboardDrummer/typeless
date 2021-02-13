package typeless

import miksilo.editorParser.parsers.editorParsers.UntilTimeStopFunction
import miksilo.languageServer.JVMLanguageServer
import miksilo.languageServer.core.language.Language
import miksilo.languageServer.server.LanguageBuilder
import miksilo.lspprotocol.lsp.LanguageServer
import typeless.ast.{JavaScriptFile, JavaScriptParser}
import typeless.interpreter.InterpreterPhase
import typeless.server.TypelessLanguageServer

object Program extends JVMLanguageServer(Seq(JavaScriptLanguageBuilder))

object JavaScriptLanguageBuilder extends LanguageBuilder {
  override def key: String = "typeless"

  override def build(arguments: collection.Seq[String]): LanguageServer = {
    new TypelessLanguageServer
  }
}

object JavaScriptLanguage extends Language {
  val parsePhase = Language.getParsePhase[JavaScriptFile]((file, uri) => file.addFile(uri),
    JavaScriptParser.javaScript.getWholeInputParser(), UntilTimeStopFunction(1000))

  compilerPhases = List(parsePhase, InterpreterPhase.phase)
}

