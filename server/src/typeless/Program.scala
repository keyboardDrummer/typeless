package typeless

import miksilo.languageServer.core.language.Language
import miksilo.lspprotocol.lsp.LanguageServer
import typeless.ast.{JavaScriptFile, JavaScriptParser}
import typeless.interpreter.InterpreterPhase
import typeless.miksilooverwrite.{BaseJVMLanguageServer, BaseLanguageBuilder}
import typeless.server.TypelessLanguageServer

object Program extends BaseJVMLanguageServer(Seq(JavaScriptLanguageBuilder)) {
}

object JavaScriptLanguageBuilder extends BaseLanguageBuilder {
  override def key: String = "typeless"

  override def build(arguments: collection.Seq[String]): LanguageServer = {
    new TypelessLanguageServer
  }
}

object JavaScriptLanguage extends Language {
  val parsePhase = Language.getParsePhase[JavaScriptFile]((file, uri) => file.addFile(uri),
    JavaScriptParser.javaScript.getWholeInputParser())

  compilerPhases = List(parsePhase, InterpreterPhase.phase)
}

