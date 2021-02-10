package typeless.server

import miksilo.languageServer.core.language.{Compilation, CompilationCache}
import typeless.interpreter.{Closure, Context}

class JavaScriptCompilation(cache: CompilationCache, rootFile: Option[String])
  extends Compilation(cache: CompilationCache, rootFile: Option[String]) {
  var context: Context = _

  var tests: Map[String, Closure] = _
}
