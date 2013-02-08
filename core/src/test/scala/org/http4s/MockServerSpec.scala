package org.http4s

import scala.language.implicitConversions
import scala.language.reflectiveCalls

import scala.concurrent.Await
import scala.concurrent.duration._

import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions

import scala.concurrent.Future

class MockServerSpec extends Specification with NoTimeConversions {
  import scala.concurrent.ExecutionContext.Implicits.global

  /*
  def stringHandler(charset: Charset, maxSize: Int = Integer.MAX_VALUE)(f: String => Responder) = {
    Traversable.takeUpTo[Chunk](maxSize)
      .transform(Iteratee.consume[Chunk]().asInstanceOf[Iteratee[Chunk, Chunk]].map {
        bs => new String(bs, charset)
      })
      .flatMap(Iteratee.eofOrElse(Responder(statusLine = StatusLine.RequestEntityTooLarge)))
      .map(_.right.map(f).merge)
  }
  */

  val server = new MockServer({
    case req if req.requestMethod == Method.Post && req.pathInfo == "/echo" =>
      Future.successful(Responder(body = req.body))
    case req if req.requestMethod == Method.Post && req.pathInfo == "/sum" =>
      def sum(acc: Int, ps: PromiseStreamOut[Chunk]): Future[Int] = {
        ps.dequeue().flatMap {
          case bytes if bytes.isEmpty => Future { acc }
          case bytes => sum(acc + new String(bytes).toInt, ps) // TODO not tail recursive
        }
      }
      sum(0, req.body).map { case i => Responder(body = PromiseStream() += i.toString.getBytes += Array.empty[Byte] )}
    case req if req.pathInfo == "/fail" =>
      sys.error("FAIL")
  })

  def response(req: Request): MockServer.Response = {
    Await.result(server(req), 5 seconds)
  }

  "A mock server" should {
    "handle matching routes" in {
      val req = Request(requestMethod = Method.Post, pathInfo = "/echo",
        body = PromiseStream() += "one".getBytes += "two".getBytes += "three".getBytes += Array.empty[Byte])
      new String(response(req).body) should_==("onetwothree")
    }

    "runs a sum" in {
      val req = Request(requestMethod = Method.Post, pathInfo = "/sum",
        body = PromiseStream() += "1".getBytes += "2".getBytes += "3".getBytes += Array.empty[Byte])
      new String(response(req).body) should_==("6")
    }

    /*
    "runs too large of a sum" in {
      val req = Request(requestMethod = Method.Post, pathInfo = "/sum",
        body = PromiseStream() += "12345678\n901234567".getBytes += Array.empty[Byte])
      response(req).statusLine should_==(StatusLine.RequestEntityTooLarge)
    }
    */

    "fall through to not found" in {
      val req = Request(pathInfo = "/bielefield")
      response(req).statusLine should_== StatusLine.NotFound
    }

    "handle exceptions" in {
      val req = Request(pathInfo = "/fail")
      response(req).statusLine should_== StatusLine.InternalServerError
    }
  }
}
