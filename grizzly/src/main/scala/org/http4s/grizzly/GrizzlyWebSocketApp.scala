package org.http4s.grizzly

import org.glassfish.grizzly.http.HttpRequestPacket
import org.glassfish.grizzly.websockets._
import concurrent.{ExecutionContext, Future}
import play.api.libs.iteratee.{Input, Iteratee}

import org.http4s.websocket._
import org.http4s._
import java.net.InetAddress
import websocket.ByteMessage
import websocket.StringMessage
import org.http4s.RequestPrelude

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
     (implicit ctx: ExecutionContext = ExecutionContext.global)
  extends WebSocketApplication with WebSocketApp {

  def isApplicationRequest(request: HttpRequestPacket): Boolean = uri == request.getRequestURI

  override def createSocket(handler: ProtocolHandler,request: HttpRequestPacket,listeners: WebSocketListener*) = {
    // This is where we need to look for placing this in the normal route definitions
    val (_it,enum) = route(toRequest(request))
    var it: Future[Iteratee[WebMessage,_]] = Future.successful(_it)

    def feedSocket(in: Input[WebMessage]) = synchronized(it = it.flatMap(_.feed(in)))

    val socket = new DefaultWebSocket(handler,request,listeners:_*) {
      override def onMessage(str: String) = {
        feedSocket(Input.El(StringMessage(str)))
      }

      override def onMessage(data: Raw) = {
        feedSocket(Input.El(ByteMessage(data)))
      }

      override def onClose(frame: DataFrame) = {
        feedSocket(Input.EOF)
        super.onClose(frame)
      }
    }

    enum.run(Iteratee.foreach[WebMessage]{
        case StringMessage(str) => socket.send(str)
        case ByteMessage(data)  => socket.send(data)
      })

    socket
  }

  def toRequest(req: HttpRequestPacket): RequestPrelude = {

    RequestPrelude(                // TODO: fix all these
      requestMethod = Method(req.getMethod.toString),
      scriptName = "", // req.getContextPath, // + req.getServletPath,
      pathInfo = "", // Option(req.getPathInfo).getOrElse(""),
      queryString = Option(req.getQueryString).getOrElse(""),
      protocol = ServerProtocol(req.getProtocol.getProtocolString),
      headers = toHeaders(req),
      urlScheme = UrlScheme("http"), //UrlScheme(req.getScheme),  // TODO: fix this.
      serverName = req.serverName.toString,
      serverPort = req.getServerPort,
      serverSoftware = ServerSoftware(req.serverName.toString),
      remote = InetAddress.getByName(req.getRemoteAddress) // TODO using remoteName would trigger a lookup
    )
  }

  def toHeaders(req: HttpRequestPacket): Headers = {
    import scala.collection.JavaConverters._

    val headers = for {
      name <- req.getHeaders.names.asScala :Iterable[String]
    } yield HttpHeaders.RawHeader(name, req.getHeader(name))
    Headers(headers.toSeq : _*)
  }
}