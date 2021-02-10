package typeless.server

import miksilo.editorParser.parsers.SourceElement
import miksilo.editorParser.parsers.core.ParseText
import miksilo.languageServer.core.language.{CompilationCache, SourcePathFromElement}
import miksilo.lspprotocol.lsp._
import typeless.JavaScriptLanguage
import typeless.interpreter.{ExpressionResult, ReturnInformationWithThrow, Value}

class TypelessLanguageServer extends BaseMiksiloLanguageServer[JavaScriptCompilation](JavaScriptLanguage)
  with DefinitionProvider
//  with ReferencesProvider
//  with CompletionProvider
//  with DocumentSymbolProvider
//  with RenameProvider
{
  def getSourceElementValue(element: SourceElement): Option[Value] = {
    getSourceElementResult(element).flatMap(r => r match {
      case value: Value => Some(value)
      case _ => None
    })
  }

  def getSourceElementResult(element: SourceElement): Option[ExpressionResult] = {
    val context = getCompilation.context
    context.throwAtElementResult = Some(element)
    for(test <- getCompilation.tests.values) {
      val result = test.evaluate(context, Seq.empty)
      result match {
        case information: ReturnInformationWithThrow =>
          return Some(information.result)
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

//    override def getOptions: CompletionOptions = CompletionOptions(resolveProvider = false, Seq.empty)
//  override def complete(params: DocumentPosition): CompletionList = {
//    val text: ParseText = documentManager.getFileParseText(params.textDocument.uri)
//    val position = params.position
//    val offset = text.getOffset(position)
//    logger.debug("Went into complete")
//    val completions: Seq[CompletionItem] = for {
//      proofs <- getProofs.toSeq
//      scopeGraph = proofs.scopeGraph
//      element <- getSourceElement(text, FilePosition(params.textDocument.uri, position)).toSeq
//      reference <- scopeGraph.getReferenceFromSourceElement(element).toSeq
//      prefixLength = offset - reference.origin.get.rangeOption.get.from.offset
//      prefix = reference.name.take(prefixLength)
//      declaration <- scopeGraph.resolveWithoutNameCheck(reference).
//        filter(declaration => declaration.name.startsWith(prefix))
//      insertText = declaration.name
//      completion = CompletionItem(declaration.name, kind = Some(CompletionItemKind.Variable), insertText = Some(insertText))
//    } yield completion
//
//    CompletionList(isIncomplete = false, completions)
//  }
//
//  def getDefinitionFromDefinitionOrReferencePosition(proofs: Proofs, element: SourcePath): Option[NamedDeclaration] = {
//    proofs.scopeGraph.findDeclaration(element).orElse(proofs.gotoDefinition(element))
//  }
//
//  override def references(parameters: ReferencesParams): collection.Seq[FileRange] = {
//    logger.debug("Went into references")
//    val text: ParseText = documentManager.getFileParseText(parameters.textDocument.uri)
//    val maybeResult = for {
//      proofs <- getProofs
//      element <- getSourceElement(text, FilePosition(parameters.textDocument.uri, parameters.position))
//      definition <- getDefinitionFromDefinitionOrReferencePosition(proofs, element)
//    } yield {
//
//      val referencesRanges: collection.Seq[FileRange] = for {
//        references <- proofs.findReferences(definition)
//        range <- references.origin.flatMap(e => e.fileRange.map(FileRange.fromOffsetRange(text, _))).toSeq
//      } yield range
//
//      var fileRanges: collection.Seq[FileRange] = referencesRanges
//      if (parameters.context.includeDeclaration)
//        fileRanges = definition.origin.flatMap(o => o.fileRange.map(FileRange.fromOffsetRange(text, _))).toSeq ++ fileRanges
//
//      fileRanges
//    }
//    maybeResult.getOrElse(Seq.empty)
//  }
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
//  override def rename(params: RenameParams): WorkspaceEdit = {
//    val locations = references(ReferencesParams(params.textDocument, params.position, ReferenceContext(true)))
//    WorkspaceEdit(locations.groupBy(l => l.uri).map(t => {
//      (t._1, t._2.map(r => TextEdit(r.range, params.newName)))
//    }))
//  }
  override def createCompilation(cache: CompilationCache, rootFile: Option[String]): JavaScriptCompilation =
    new JavaScriptCompilation(cache, rootFile)
}
