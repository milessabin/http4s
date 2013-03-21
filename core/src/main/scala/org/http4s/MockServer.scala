package org.http4s

import scala.language.reflectiveCalls

import concurrent.{Await, ExecutionContext, Future}
import concurrent.duration._
import play.api.libs.iteratee.{Enumerator, Iteratee}
import util.Spool
import akka.util.ByteString

class MockServer(route: Route)(implicit executor: ExecutionContext = ExecutionContext.global) {
  import MockServer.Response

  def apply(req: RequestPrelude, spool: Spool[HttpChunk]): Future[Response] = {
    try {
//      route.lift(req).fold(Future.successful(onNotFound)) { parser =>
//        val it: Iteratee[HttpChunk, Response] = parser.flatMap { responder =>
//          val responseBodyIt: Iteratee[BodyChunk, BodyChunk] = Iteratee.consume()
//          responder.body ><> BodyParser.whileBodyChunk &>> responseBodyIt map { bytes: BodyChunk =>
//            Response(responder.prelude.status, responder.prelude.headers, body = bytes.toArray)
//          }
//        }
//        enum.run(it)
//      }
      route.lift(req).fold(Future.successful(onNotFound)) { handler: (Spool[HttpChunk] => Future[Responder]) =>
        handler(spool).flatMap { responder =>
          responder.body.fold(ByteString.empty)( (b, chunk) => chunk match {
            case BodyChunk(bytes) => println(new String(bytes.toArray)); b ++ bytes
            case _ => b
          })
          .map( byteStr => Response(responder.prelude.status, responder.prelude.headers, byteStr.toArray))
        }
      }
    } catch {
      case t: Throwable => Future.successful(onError(t))
    }
  }

  def response(req: RequestPrelude,
               body: Spool[HttpChunk] = Spool.empty,
               wait: Duration = 5.seconds): MockServer.Response = {
    Await.result(apply(req, body), 5.seconds)
  }

  def onNotFound: MockServer.Response = Response(statusLine = Status.NotFound, body ="Not found".getBytes)

  def onError: PartialFunction[Throwable, Response] = {
    case e: Exception =>
      e.printStackTrace()
      Response(statusLine = Status.InternalServerError)
  }
}

object MockServer {
  private[MockServer] val emptyBody = Array.empty[Byte]   // Makes direct Response comparison possible

  case class Response(
    statusLine: Status = Status.Ok,
    headers: HttpHeaders = HttpHeaders.Empty,
    body: Array[Byte] = emptyBody
  )
}
