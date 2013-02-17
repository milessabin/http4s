package org.http4s.grizzly

import org.glassfish.grizzly.http.HttpRequestPacket
import org.glassfish.grizzly.websockets._
import concurrent.{ExecutionContext, Future}
import play.api.libs.iteratee.{Input, Iteratee}
import org.http4s.WebSocketApp

/**
 * @author Bryce Anderson
 * Created on 2/17/13 at 4:29 PM
 */

object GrizzlyWebSocketApp {
  def apply(uri: String)(route: WebSocketApp.WebSocketRoute)
           (implicit ctx: ExecutionContext = ExecutionContext.global) =
    new GrizzlyWebSocketApp(uri)(route)(ctx)
}

class GrizzlyWebSocketApp(val uri: String)(val route: WebSocketApp.WebSocketRoute)
     (implicit ctx: ExecutionContext = ExecutionContext.global) extends WebSocketApplication with WebSocketApp {

  def isApplicationRequest(request: HttpRequestPacket): Boolean = uri == request.getRequestURI

  override def createSocket(handler: ProtocolHandler,request: HttpRequestPacket,listeners: WebSocketListener*) = {
    val (_it,enum) = route
    var it: Future[Iteratee[String,_]] = Future.successful(_it)

    def feedSocket(in: Input[String]) = synchronized(it = it.flatMap(_.feed(in)))

    val socket = new DefaultWebSocket(handler,request,listeners:_*) {
      override def onMessage(str: String) = feedSocket(Input.El(str))

      // Is there something I should be doing with this DataFrame?
      override def onClose(frame: DataFrame) = {
        feedSocket(Input.EOF)
        super.onClose(frame)
      }
    }
    enum.run(Iteratee.foreach[String](socket.send(_)))
    socket
  }
}