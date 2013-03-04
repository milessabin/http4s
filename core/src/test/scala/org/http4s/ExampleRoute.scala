package org.http4s

import attributes._
import scala.language.reflectiveCalls
import concurrent.{Future, ExecutionContext}
import play.api.libs.iteratee._
import akka.util.ByteString

object ExampleRoute extends RouteHandler {
  import Status._
  import Writable._
  import BodyParser._

  object myVar extends Key[String]

  def apply(implicit executor: ExecutionContext = ExecutionContext.global): Route = {
    case Get(Root / "ping") =>
      Ok("pong")

    case Post(Root / "echo") =>
      Ok(Enumeratee.passAlong[HttpChunk])

    case Get(Root / "echo")  =>
      Ok(Enumeratee.map[HttpChunk] {
        case BodyChunk(e) => BodyChunk(e.slice(6, e.length)): HttpChunk
        case chunk => chunk
      })

    case Get(Root / "echo2") =>
      Ok(Enumeratee.map[HttpChunk]{
        case BodyChunk(e) => BodyChunk(e.slice(6, e.length)): HttpChunk
        case chunk => chunk
      })

    case req @ Post(Root / "sum")  =>
      text(req.charset, 16).respond { s =>
        val sum = s.split('\n').map(_.toInt).sum
        Ok(sum)
      }

    case req @ Post(Root / "trailer") =>
      trailer.respond { t => Ok(t.headers.length) }

    case req @ Post(Root / "body-and-trailer") =>
      (for {
        body <- text(req.charset)
        trailer <- trailer
      } yield Ok(s"$body\n${trailer.headers("Hi").value}")).respond(identity)

    case req @ Get(Root / "stream") =>
      Ok(Concurrent.unicast[ByteString]({
        channel =>
          for (i <- 1 to 10) {
            channel.push(ByteString("%d\n".format(i), req.charset.value))
            Thread.sleep(1000)
          }
          channel.eofAndEnd()
      }))

    case Get(Root / "bigstring") =>
      val builder = new StringBuilder(20*1028)
      Ok((0 until 1000) map { i => s"This is string number $i" })

    case Get(Root / "future") =>
      Done{
        Ok(Future("Hello from the future!"))
      }

      // Ross wins the challenge
    case req @ Get(Root / "challenge") =>
      Iteratee.head[HttpChunk].map {
        case Some(bits: BodyChunk) if (bits.decodeString(req.charset)).startsWith("Go") =>
          Ok(Enumeratee.heading(Enumerator(bits: HttpChunk)))
        case Some(bits: BodyChunk) if (bits.decodeString(req.charset)).startsWith("NoGo") =>
          BadRequest("Booo!")
        case _ =>
          BadRequest("No data!")
      }

    case req if req.pathInfo == "/root-element-name" =>
      xml(req.charset).respond { elem =>
        Ok(elem.label)
      }

    case req if req.pathInfo == "/fail" =>
      sys.error("FAIL")
  }
}