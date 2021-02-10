package typeless

import miksilo.languageServer.JVMLanguageServer
import miksilo.languageServer.core.language.Language
import miksilo.languageServer.server.LanguageBuilder
import typeless.ast.{JavaScriptFile, JavaScriptParser}
import typeless.interpreter.InterpreterPhase

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

  compilerPhases = List(parsePhase, InterpreterPhase.phase)
}

