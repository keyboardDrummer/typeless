package typeless.miksilooverwrite

import miksilo.editorParser.LazyLogging
import miksilo.editorParser.parsers.core.ParseText
import miksilo.languageServer.core.language.exceptions.BadInputException
import miksilo.languageServer.core.language.{Compilation, CompilationCache, Language}
import miksilo.languageServer.server.{SourcePath, TextDocumentManager}
import miksilo.lspprotocol.lsp._

abstract class BaseMiksiloLanguageServer[MyCompilation <: Compilation](val language: Language) extends LanguageServer
  // with CodeActionProvider
  with LazyLogging {

  var client: LanguageClient = _
  protected val documentManager = new TextDocumentManager()
  val compilationCache: CompilationCache = new CompilationCache(language, documentManager)
  var compilation: MyCompilation = _

  override def textDocumentSync: Int = TextDocumentSyncKind.Incremental

  override def didOpen(parameters: TextDocumentItem): Unit = {
    compilation = createCompilation(compilationCache, Some(parameters.uri))
    documentManager.onOpenTextDocument(parameters)
    sendDiagnostics()
  }

  def createCompilation(cache: CompilationCache, rootFile: Option[String]): MyCompilation

  override def didClose(parameters: TextDocumentIdentifier): Unit = documentManager.onCloseTextDocument(parameters)

  override def didSave(parameters: DidSaveTextDocumentParams): Unit = {}

  override def didChange(parameters: DidChangeTextDocumentParams): Unit = {
    compilation = createCompilation(compilationCache, Some(parameters.textDocument.uri))
    if (parameters.contentChanges.nonEmpty) {
      val start = System.currentTimeMillis()
      documentManager.onChangeTextDocument(parameters.textDocument, parameters.contentChanges)
      compilation.metrics.measure("Apply change time", System.currentTimeMillis() - start)
    }
    sendDiagnostics()
  }

  private def sendDiagnostics(): Unit = {
    val uri = compilation.rootFile.get
    val diagnostics = getCompilation.diagnosticsForFile(uri)
    if (client != null) {
      client.sendDiagnostics(PublishDiagnostics(uri, diagnostics))
    }
  }

  def compile(): Unit = {
    try {
      compilation.runPhases()
    } catch {
      case e: BadInputException => //TODO move to diagnostics.
        logger.debug(e.toString)
    }
  }

  def getCompilation: MyCompilation = {
    if (!compilation.isStarted)
      compile()
    compilation
  }

  def getSourceElement(text: ParseText, position: FilePosition): Option[SourcePath] = {
    val fileOffset = FileOffset(position.uri, text.getOffset(position.position))
    val program = getCompilation.program
    if (program == null)
      return None

    program.getChildForPosition(fileOffset)
  }

  override def initialize(parameters: InitializeParams): Unit = {}

  override def initialized(): Unit = {}

  override def setClient(client: LanguageClient): Unit = {
    this.client = client
    compilationCache.metrics = client.trackMetric
  }

//  override def getCodeActions(parameters: CodeActionParams): Seq[CodeAction] = {
//    val diagnostics = parameters.context.diagnostics.map(d => d.identifier).toSet
//    val compilation = getCompilation
//    compilation.fixesPerDiagnostics.
//      filter(entry => diagnostics.contains(entry._1)).flatMap(entry => entry._2).toSeq
//  }
}