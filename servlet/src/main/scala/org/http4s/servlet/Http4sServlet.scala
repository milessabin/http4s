package org.http4s
package servlet

import javax.servlet.http.{HttpServletResponse, HttpServletRequest, HttpServlet}
import java.net.InetAddress
import scala.collection.JavaConverters._
import concurrent.{Future, ExecutionContext}
import javax.servlet.AsyncContext
import java.io.InputStream

class Http4sServlet(route: Route, chunkSize: Int = 32 * 1024)(implicit executor: ExecutionContext = ExecutionContext.global) extends HttpServlet {
  override def service(req: HttpServletRequest, resp: HttpServletResponse) {
    val request = toRequest(req)
    val ctx = req.startAsync()
    executor.execute(new Runnable {
      def run() {
        val responder = route(request)
        responder.onSuccess { case responder => renderResponse(responder, resp, ctx) }
      }
    })
  }

  protected def renderResponse(responder: Responder, resp: HttpServletResponse, ctx: AsyncContext) {
    resp.setStatus(responder.statusLine.code, responder.statusLine.reason)
    for (header <- responder.headers) {
      resp.addHeader(header.name, header.value)
    }
    def renderBody(ps: PromiseStreamOut[Chunk]) {
      ps.dequeue().foreach {
        case bytes if bytes.isEmpty => ctx.complete()
        case bytes =>
          resp.getOutputStream.write(bytes)
          renderBody(ps) // TODO not tail recursive
      }
    }
  }

  protected def toRequest(req: HttpServletRequest): Request =
    Request(
      requestMethod = Method(req.getMethod),
      scriptName = req.getContextPath + req.getServletPath,
      pathInfo = Option(req.getPathInfo).getOrElse(""),
      queryString = Option(req.getQueryString).getOrElse(""),
      protocol = ServerProtocol(req.getProtocol),
      headers = toHeaders(req),
      body = chunk(req.getInputStream),
      urlScheme = UrlScheme(req.getScheme),
      serverName = req.getServerName,
      serverPort = req.getServerPort,
      serverSoftware = ServerSoftware(getServletContext.getServerInfo),
      remote = InetAddress.getByName(req.getRemoteAddr) // TODO using remoteName would trigger a lookup
    )

  protected def toHeaders(req: HttpServletRequest): Headers = {
    val headers = for {
      name <- req.getHeaderNames.asScala
      value <- req.getHeaders(name).asScala
    } yield Header(name, value)
    Headers(headers.toSeq : _*)
  }

  protected def chunk(inputStream: InputStream) = {
    val promiseStream = PromiseStream[Chunk]()
    var read = 0
    var bytes: Array[Byte] = new Array[Byte](chunkSize)
    while (read >= 0) {
      read = inputStream.read(bytes)
      promiseStream << bytes.slice(0, read)
    }
    promiseStream
  }
}
