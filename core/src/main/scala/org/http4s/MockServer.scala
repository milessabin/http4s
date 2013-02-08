package org.http4s

import scala.language.reflectiveCalls

import scala.concurrent.{ExecutionContext, Future}

class MockServer(route: Route)(implicit executor: ExecutionContext = ExecutionContext.global) {
  import MockServer.Response

  def apply(req: Request): Future[Response] = {
    try {
      route.lift(req).fold(Future.successful(onNotFound)) {
        responder => responder.flatMap(render)
      }
    } catch {
      case t: Throwable => Future.successful(onError(t))
    }
  }

  def render(responder: Responder): Future[Response] = {
    def buildBody(acc: Array[Byte], ps: PromiseStreamOut[Chunk]): Future[Array[Byte]] = {
      ps.dequeue().flatMap {
        case bytes if bytes.isEmpty => Future { acc }
        case bytes => buildBody(acc ++ bytes, ps) // TODO not tail recursive
      }
    }
    buildBody(Array.empty, responder.body).map { body =>
      Response(statusLine = responder.statusLine, headers = responder.headers, body = body)
    }
  }

  def onNotFound: MockServer.Response = Response(statusLine = StatusLine.NotFound)

  def onError: PartialFunction[Throwable, Response] = {
    case e: Exception =>
      e.printStackTrace()
      Response(statusLine = StatusLine.InternalServerError)
  }
}

object MockServer {
  case class Response(
    statusLine: StatusLine = StatusLine.Ok,
    headers: Headers = Headers.Empty,
    body: Array[Byte] = Array.empty
  )
}
