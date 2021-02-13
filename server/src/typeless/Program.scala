package typeless

import miksilo.editorParser.parsers.{RealSourceElement, SourceElement}
import miksilo.editorParser.parsers.editorParsers.{OffsetPointerRange, UntilTimeStopFunction}
import miksilo.languageServer.JVMLanguageServer
import miksilo.languageServer.core.language.Language
import miksilo.languageServer.server.{LanguageBuilder, SourcePath}
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
  val parsePhase = Language.getParsePhase[JavaScriptFile]((file, uri) => ChainElement(new RootElement(uri), file),
    JavaScriptParser.javaScript.getWholeInputParser(), UntilTimeStopFunction(100))

  compilerPhases = List(parsePhase, InterpreterPhase.phase)
}

trait PathElement {
  def uriOption: Option[String]
  def ancestors: List[SourceElement]
}

class RootElement(uri: String) extends PathElement {
  def uriOption = Some(uri)

  override def ancestors: List[SourceElement] = Nil
}

case class ChainElement(parent: PathElement, sourceElement: SourceElement) extends SourcePath with PathElement {

  def ancestors: List[SourceElement] = sourceElement :: parent.ancestors

  override def uriOption: Option[String] = parent.uriOption

  override def rangeOption = sourceElement.rangeOption

  override def childElements: Seq[ChainElement] = {
    sourceElement.childElements.map(e => ChainElement(this, e))
  }
}