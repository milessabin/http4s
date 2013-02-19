package org.http4s.grizzly

import org.glassfish.grizzly.http.HttpRequestPacket
import org.glassfish.grizzly.websockets._
import concurrent.{Await, ExecutionContext, Future}
import concurrent.duration._
import play.api.libs.iteratee.{Input, Iteratee, Enumerator}

import org.http4s.websocket._
import org.http4s._
import java.net.{InetAddress,URI}
import websocket.ByteMessage
import websocket.StringMessage
import org.http4s.RequestPrelude

/**
 * @author Bryce Anderson
 * Created on 2/17/13 at 4:29 PM
 */


class GrizzlyWebSocketApp(context: String, address: String, port: Int, route: Route)
     (implicit ctx: ExecutionContext = ExecutionContext.global)
  extends WebSocketApplication {

  def isApplicationRequest(request: HttpRequestPacket): Boolean = {
    route.lift(toRequest(request)) match {
      case Some(it) => {
        Await.result(Enumerator.eof.run(it), 1 second) match {
          case _: SocketResponder => true
          case _ => false
        }
      }
      case None => false
    }
  }

  private[this] def buildPathStr(req: HttpRequestPacket): String = {
    println(s"Websocket path: ${ctx + req.getRequestURI}")
    ctx + req.getRequestURI
  }

  override def createSocket(handler: ProtocolHandler,request: HttpRequestPacket,listeners: WebSocketListener*) = {
    // This is where we need to look for placing this in the normal route definitions
    val a: Future[ResponderBase] = Enumerator.eof.run(route(toRequest(request)))

    val (_it,enum) = Await.result(a, 1 second) match {
      case s: SocketResponder => s.socket()
      case _ :Responder => sys.error(s"Route captured a websocket path: ${buildPathStr(request)}")
    }

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
      uri = URI.create(buildRequestURL(req) + "?" + Option(req.getQueryString).getOrElse("")),
      pathInfo = req.getRequestURI, // Option(req.getPathInfo).getOrElse(""),
      protocol = ServerProtocol(req.getProtocol.getProtocolString),
      headers = toHeaders(req),
      serverSoftware = ServerSoftware(req.serverName.toString),
      remote = InetAddress.getByName(req.getRemoteAddress) // TODO using remoteName would trigger a lookup
    )
  }

  // This is needed to build the request URL form the HttpRequestPacket.
  protected def buildRequestURL(req: HttpRequestPacket): String = {
    val sb = new StringBuilder
    val scheme = if(req.isSecure) "https" else "http"
    sb.append(scheme)
    sb.append("://")
    sb.append(address)
    if ((scheme.equals("http") && (port != 80))
      || (scheme.equals("https") && (port != 443))) {
      sb.append(':')
      sb.append(port)
    }
    sb.append(req.getRequestURI());
    return sb.result
  }

  def toHeaders(req: HttpRequestPacket): Headers = {
    import scala.collection.JavaConverters._

    val headers = for {
      name <- req.getHeaders.names.asScala :Iterable[String]
    } yield HttpHeaders.RawHeader(name, req.getHeader(name))
    Headers(headers.toSeq : _*)
  }
}