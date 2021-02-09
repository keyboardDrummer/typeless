import miksilo.editorParser.languages.json.JsonParser.{Parser, literal, stringLiteral, wholeNumber}
import miksilo.editorParser.languages.json.{JsonArray, JsonObject, JsonValue, NumberLiteral, StringLiteral, ValueHole}
import miksilo.editorParser.parsers.core.{Metrics, TextPointer}
import miksilo.editorParser.parsers.{RealSourceElement, SourceElement}
import miksilo.editorParser.parsers.editorParsers.{History, LeftRecursiveCorrectingParserWriter, OffsetPointerRange, SingleParseResult, SingleResultParser, StopFunction}
import miksilo.editorParser.parsers.strings.{CommonStringReaderParser, WhitespaceParserWriter}
import miksilo.languageServer.JVMLanguageServer
import miksilo.languageServer.core.language.{FileElement, Language}
import miksilo.languageServer.server.LanguageBuilder

import scala.collection.immutable.ListMap
import scala.collection.mutable

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

