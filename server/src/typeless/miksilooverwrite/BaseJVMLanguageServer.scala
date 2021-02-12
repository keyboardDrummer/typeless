package typeless.miksilooverwrite

import miksilo.editorParser.{LambdaLogger, LazyLogging}
import miksilo.lspprotocol.jsonRpc.{JVMMessageReader, JVMMessageWriter, JVMQueue, JsonRpcConnection, WorkItem}

class BaseJVMLanguageServer(builders: Seq[BaseLanguageBuilder]) extends BaseLanguageServerMain(builders,
  new JsonRpcConnection(new JVMMessageReader(System.in), new JVMMessageWriter(System.out)),
  new JVMQueue[WorkItem]) {
  LazyLogging.logger = new LambdaLogger(s => System.err.println(s))
}
