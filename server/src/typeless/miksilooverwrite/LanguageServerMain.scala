package typeless.miksilooverwrite

import miksilo.editorParser.LazyLogging
import miksilo.languageServer.core.language.Language
import miksilo.languageServer.server.MiksiloLanguageServer
import miksilo.lspprotocol.jsonRpc.{JsonRpcConnection, SerialWorkQueue, WorkItem}
import miksilo.lspprotocol.lsp.{LanguageServer, SharedLSPServer}

import scala.util.Try

trait BaseLanguageBuilder {
  def key: String
  def build(arguments: collection.Seq[String]): LanguageServer
}

case class SimpleLanguageBuilder(key: String, language: Language) extends BaseLanguageBuilder {
  override def build(arguments: collection.Seq[String]) = new MiksiloLanguageServer(language)
}

class BaseLanguageServerMain(builders: Seq[BaseLanguageBuilder],
                         connection: JsonRpcConnection,
                         workQueue: SerialWorkQueue[WorkItem]) extends LazyLogging {

  private val languageMap = builders.map(l => (l.key, l)).toMap

  def main(args: Array[String]): Unit = {
    val languageServerOption = getLanguage(args)
    languageServerOption.foreach(languageServer => {
      logger.debug(s"Starting server in ${System.getenv("PWD")}")
      val lspServer = Try {
        new SharedLSPServer(languageServer, connection, workQueue)
      }
      lspServer.recover{case e => logger.error(e.getMessage); e.printStackTrace() }
      connection.listen()
    })
  }

  def getLanguage(args: collection.Seq[String]): Option[LanguageServer] = {
    if (builders.isEmpty) {
      logger.error("Miksilo was not configured with any languages")
      return None
    }
    if (builders.size == 1) {
      Some(builders.head.build(args))
    } else {
      if (args.isEmpty) {
        logger.error("Please specify with which language to run Miksilo")
        return None
      }

      val languageOption = languageMap.get(args.head)
      languageOption match {
        case None =>
          logger.error("Please specify with which language to run Miksilo")
          None
        case Some(languageBuilder) =>
          Some(languageBuilder.build(args.drop(1)))
      }
    }
  }
}

