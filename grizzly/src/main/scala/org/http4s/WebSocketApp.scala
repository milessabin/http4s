package org.http4s

import org.glassfish.grizzly.websockets._
import org.glassfish.grizzly.http.HttpRequestPacket
import play.api.libs.iteratee.{Input, Iteratee, Enumerator}
import concurrent.{Future,ExecutionContext}


/**
 * @author Bryce Anderson
 * Created on 2/16/13 at 6:20 PM
 */
class WebSocketApp(uri: String)(f:String => (Iteratee[String,Unit], Enumerator[String],String=>Unit) )
  (implicit ctx: ExecutionContext = ExecutionContext.global) extends WebSocketApplication {

  def isApplicationRequest(request: HttpRequestPacket): Boolean = uri == request.getRequestURI

  // Where will they be stored?
  override def createSocket(handler: ProtocolHandler,request: HttpRequestPacket,listeners: WebSocketListener*) = {
    val (_it,enum,end) = f("Stub")
    var it: Future[Iteratee[String,Unit]] = Future.successful(_it)

    val socket = new Http4sGrizzlyWebSocket(handler,request,listeners:_*) {
      override def onMessage(text: String) = { it = it.flatMap(_.feed(Input.El(text))) }
      override def onMessage(data: Array[Byte]) = onMessage(new String(data))
      override def onClose(frame: DataFrame) {

        end("Socket closing...") // TODO: Doesn't seem to be getting called.
        super.onClose(frame)
      }
    }
    enum.run(Iteratee.foreach[String](socket.send(_)))
    socket
  }

  // The message comes from this particular websocket?
  override def onMessage(websocket: WebSocket,data: String) = {}

  //override def onClose(websocket: WebSocket, frame: DataFrame): Unit = {}

}

object WebSocketApp{
  def apply(uri: String)(f:String => (Iteratee[String,Unit], Enumerator[String],String=>Unit) )
           (implicit ctx: ExecutionContext = ExecutionContext.global) =
    new WebSocketApp(uri)(f)(ctx)
}
