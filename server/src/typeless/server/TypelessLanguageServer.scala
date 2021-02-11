package typeless.server

import miksilo.editorParser.parsers.SourceElement
import miksilo.editorParser.parsers.core.ParseText
import miksilo.editorParser.parsers.editorParsers.TextEdit
import miksilo.languageServer.core.language.{CompilationCache, SourcePathFromElement}
import miksilo.lspprotocol.lsp._
import typeless.JavaScriptLanguage
import typeless.ast.NameLike
import typeless.interpreter.{ExpressionResult, ReturnInformationWithThrow, ScopeInformation, ScopeLike, Value}
import typeless.miksilooverwrite.BaseMiksiloLanguageServer

class TypelessLanguageServer extends BaseMiksiloLanguageServer[JavaScriptCompilation](JavaScriptLanguage)
  with DefinitionProvider
  with CompletionProvider
  with ReferencesProvider
  with RenameProvider
  with HoverProvider
  //  with DocumentSymbolProvider
{
  def getSourceElementValue(element: SourceElement): Option[Value] = {
    getSourceElementResult(element).flatMap(r => r match {
      case value: Value => Some(value)
      case _ => None
    })
  }

  def getSourceElementResult(element: SourceElement): Option[ExpressionResult] = {
    val context = getCompilation.context
    context.collectScopeAtElement = None
    context.throwAtElementResult = Some(element)
    for (test <- getCompilation.tests.values) {
      val result = test.evaluate(context, Seq.empty)
      result match {
        case information: ReturnInformationWithThrow =>
          return Some(information.result)
        case _ =>
      }
    }
    None
  }

  def getSourceElementScope(element: SourceElement): Option[ScopeLike] = {
    val context = getCompilation.context
    context.collectScopeAtElement = Some(element)
    context.throwAtElementResult = None
    for (test <- getCompilation.tests.values) {
      val result = test.evaluate(context, Seq.empty)
      result match {
        case information: ScopeInformation =>
          return Some(information.scope)
        case _ =>
      }
    }
    None
  }

  def createCompilation(parameters: TextDocumentItem): JavaScriptCompilation = {
    new JavaScriptCompilation(compilationCache, Some(parameters.uri))
  }

  override def gotoDefinition(parameters: DocumentPosition): Seq[FileRange] = {
    logger.debug("Went into gotoDefinition")
    val text: ParseText = documentManager.getFileParseText(parameters.textDocument.uri)
    val sourceElementOption = getSourceElement(text, FilePosition(parameters.textDocument.uri, parameters.position))
    sourceElementOption.fold(Seq.empty[FileRange])(element => {
      val resultOption = getSourceElementValue(element.asInstanceOf[SourcePathFromElement].sourceElement)
      val definitions: Seq[SourceElement] = resultOption.fold(Seq.empty[SourceElement])(result => {
        result.definedAt.toSeq
      })
      val ranges: Seq[FileRange] = definitions.flatMap(d =>
        d.rangeOption.map(r => FileRange(parameters.textDocument.uri, r.toSourceRange)).toSeq)
      ranges
    })
  }

  override def getOptions: CompletionOptions = CompletionOptions(resolveProvider = false, Seq.empty)

  override def complete(parameters: DocumentPosition): CompletionList = {
    logger.debug("Went into complete")
    val text: ParseText = documentManager.getFileParseText(parameters.textDocument.uri)

    val sourceElementOption = getSourceElement(text, FilePosition(parameters.textDocument.uri, parameters.position))
    val completions: Seq[CompletionItem] = sourceElementOption.fold(Iterable.empty[CompletionItem])(element => {

      val sourceElement = element.asInstanceOf[SourcePathFromElement].sourceElement
      val scopeOption = getSourceElementScope(sourceElement)
      scopeOption.fold(Iterable.empty[CompletionItem])(scope => {
        val memberNames = sourceElement match {
          case nameLike: NameLike => scope.memberNames.filter(member => member.startsWith(nameLike.name))
          case _ => scope.memberNames
        }
        memberNames.map(member => {
          CompletionItem(member, detail = Some(scope.getValue(member).represent()))
        })
      })
    }).toSeq

    CompletionList(isIncomplete = false, completions)
  }

  override def references(parameters: ReferencesParams): collection.Seq[FileRange] = {
    logger.debug("Went into references")
    val text: ParseText = documentManager.getFileParseText(parameters.textDocument.uri)
    val sourceElementOption = getSourceElement(text, FilePosition(parameters.textDocument.uri, parameters.position))

    sourceElementOption.fold(Seq.empty[FileRange])(element => {
      val sourceElement = element.asInstanceOf[SourcePathFromElement].sourceElement
      sourceElement match {
        case name: NameLike =>
          getCompilation.references.getReferences(name).
            toSeq.flatMap(n => n.rangeOption.map(r => FileRange(parameters.textDocument.uri, r.toSourceRange)).toSeq)
      }
    })
  }

  override def rename(params: RenameParams): WorkspaceEdit = {
    val locations = references(ReferencesParams(params.textDocument, params.position, ReferenceContext(true)))
    WorkspaceEdit(locations.groupBy(l => l.uri).map(t => {
      (t._1, t._2.map(r => TextEdit(r.range, params.newName)))
    }))
  }

  //
  //  override def documentSymbols(params: DocumentSymbolParams): Seq[SymbolInformation] = {
  //    val proofs = getCompilation.proofs
  //    if (proofs == null)
  //      return Seq.empty
  //
  //    val text = documentManager.getFileParseText(params.textDocument.uri)
  //
  //    val declarations = getCompilation.proofs.scopeGraph.declarationsPerFile.getOrElse(params.textDocument.uri, Seq.empty).toSeq
  //    declarations.
  //      filter(declaration => declaration.name.nonEmpty && {
  //        if (declaration.origin.isEmpty) {
  //          logger.error(s"[BUG] Empty origin for declaration ${declaration.name}")
  //          false
  //        } else if (declaration.origin.get.fileRange.isEmpty) {
  //          logger.error(s"[BUG] Empty fileRange for declaration ${declaration.name}")
  //          false
  //        } else {
  //          true
  //        }
  //      }).
  //      map(declaration => {
  //        val fileRange = FileRange.fromOffsetRange(text, declaration.origin.get.fileRange.get)
  //        SymbolInformation(declaration.name, SymbolKind.Variable, fileRange, None)
  //      })
  //  }
  //
  override def createCompilation(cache: CompilationCache, rootFile: Option[String]): JavaScriptCompilation =
    new JavaScriptCompilation(cache, rootFile)

  override def hoverRequest(request: TextDocumentHoverRequest): Hover = {
    val uri = request.params.textDocument.uri
    val text: ParseText = documentManager.getFileParseText(uri)
    val sourceElementOption = getSourceElement(text, FilePosition(uri, request.params.position))
    sourceElementOption.fold[Hover](null)(element => {
      val resultOption = getSourceElementValue(element.asInstanceOf[SourcePathFromElement].sourceElement)
      val hover: Option[Hover] = resultOption.map(result => {
        Hover(Seq(new RawMarkedString(result.represent())), element.rangeOption.map(r => r.toSourceRange))
      })
      hover.orNull
    })
  }
}
