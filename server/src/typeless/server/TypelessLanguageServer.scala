package typeless.server

import miksilo.editorParser.parsers.SourceElement
import miksilo.editorParser.parsers.core.ParseText
import miksilo.editorParser.parsers.editorParsers.TextEdit
import miksilo.languageServer.core.language.{CompilationCache, SourcePathFromElement}
import miksilo.lspprotocol.lsp._
import typeless.{ChainElement, JavaScriptLanguage}
import typeless.ast.{Expression, NameLike}
import typeless.interpreter.{ExpressionResult, FindScope, FindValue, ReturnInformationWithThrow, ScopeInformation, ScopeLike, Value}
import typeless.miksilooverwrite.BaseMiksiloLanguageServer

class TypelessLanguageServer extends BaseMiksiloLanguageServer[JavaScriptCompilation](JavaScriptLanguage)
  with DefinitionProvider
  with CompletionProvider
  with ReferencesProvider
  with RenameProvider
  with HoverProvider
  with TypeDefinitionProvider
  // with DocumentSymbolProvider
{
  def getSourceElementValue(element: SourceElement): Option[Value] = {
    getSourceElementResult(element).flatMap(r => r match {
      case value: Value => Some(value)
      case _ => None
    })
  }

  def getSourceElementResult(element: SourceElement): Option[ExpressionResult] = {
    val context = getCompilation.context
    context.configuration.mode = FindValue(element)
    for (test <- getCompilation.tests.values) {
      // TODO add test that fails when we do 'val result = test.evaluate(context, Seq.empty)' here
      val result = context.runTest(test)
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
    context.configuration.mode = FindScope(element)
    for (test <- getCompilation.tests.values) {
      // TODO add test that fails when we do 'val result = test.evaluate(context, Seq.empty)' here
      val result = context.runTest(test)
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
      val sourceElement = element.asInstanceOf[ChainElement].sourceElement
      sourceElement match {
        case name: NameLike =>
          getCompilation.refs.fromReference.get(name).
            toSeq.flatMap(n => n.rangeOption.map(r => FileRange(parameters.textDocument.uri, r.toSourceRange)).toSeq)
        case _ => Seq.empty
      }
    })
  }

  override def getOptions: CompletionOptions = CompletionOptions(resolveProvider = false, Seq("."))

  override def complete(parameters: DocumentPosition): CompletionList = {
    logger.debug("Went into complete")
    val text: ParseText = documentManager.getFileParseText(parameters.textDocument.uri)

    val sourceElementOption = getSourceElement(text, FilePosition(parameters.textDocument.uri, parameters.position))
    val completions: Seq[CompletionItem] = sourceElementOption.fold(Iterable.empty[CompletionItem])(element => {

      val sourceElement = element.asInstanceOf[ChainElement].sourceElement
      val scopeOption = getSourceElementScope(sourceElement)
      scopeOption.fold(Iterable.empty[CompletionItem])(scope => {
        val memberNames = sourceElement match {
          case nameLike: NameLike => scope.memberNames.filter(member => member.startsWith(nameLike.name))
          case _ => scope.memberNames
        }
        memberNames.map(member => {
          val value = scope.getValue(member)
          val valueLine = s"Example value: `${value.represent()}`"
          val total = valueLine + value.documentation.map(d => "\n\n" + d).getOrElse("")

          CompletionItem(member, detail = None, documentation = Some(MarkupContent.markdown(total)))
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
      val sourceElement = element.asInstanceOf[ChainElement].sourceElement
      sourceElement match {
        case name: NameLike =>
          val declaration = getCompilation.refs.fromReference.getOrElse(name, name)
          val references = getCompilation.refs.fromDeclaration(declaration)
          val resultElements = references ++ (if (parameters.context.includeDeclaration) {
            Seq(declaration)
          } else {
            Seq.empty[NameLike]
          })
          resultElements.toSeq.
            flatMap(n => n.rangeOption).
            map(r => FileRange(parameters.textDocument.uri, r.toSourceRange))
        case _ => Seq.empty
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



  override def hover(request: DocumentPosition): Option[Hover] = {
    valueFromPosition(request).map(t => {
      val markedString = RawMarkedString("javascript", s"${t._2.represent()}")
      Hover(Seq(markedString), t._1.rangeOption.map(r => r.toSourceRange))
    })
  }

  def valueFromPosition(parameters: DocumentPosition): Option[(SourceElement, Value)] = {
    val uri = parameters.textDocument.uri
    val text: ParseText = documentManager.getFileParseText(uri)
    for {
      sourceElement <- getSourceElement(text, FilePosition(uri, parameters.position))
      expression <- sourceElement.asInstanceOf[ChainElement].ancestors.find(e => e.isInstanceOf[Expression])
      value <- getSourceElementValue(expression)
    } yield {
      (expression, value)
    }
  }

  override def gotoTypeDefinition(parameters: DocumentPosition): Seq[FileRange] = {
    val uri = parameters.textDocument.uri

    valueFromPosition(parameters).
      map(value => new FileRange(uri, value._2.createdAt.rangeOption.get.toSourceRange)).toSeq
  }
}
