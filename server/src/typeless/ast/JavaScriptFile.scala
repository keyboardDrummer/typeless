package typeless.ast

import miksilo.editorParser.parsers.SourceElement
import miksilo.editorParser.parsers.editorParsers.OffsetPointerRange
import miksilo.languageServer.core.language.FileElement
import typeless.interpreter.{Context, StatementResult}

class JavaScriptFile(val range: OffsetPointerRange, statements: Vector[Statement]) extends FileElement {
  def evaluate(context: Context): StatementResult = {
    Statement.evaluateBody(context, statements)
  }

  override def childElements: Seq[SourceElement] = {
    statements
  }
}
