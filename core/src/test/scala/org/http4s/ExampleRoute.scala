package org.http4s

import scala.language.reflectiveCalls
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.iteratee._
import org.http4s.Method.Post

object ExampleRoute {
  def apply(implicit executor: ExecutionContext = ExecutionContext.global): Route = {
    case Root / "ping" =>
      Future.successful(Responder(body = Enumerator("pong".getBytes())))

    case req @ Post(Root / "echo") =>
      Future.successful(Responder(body = req.body))

    case req @ Root / "echo" =>
      Future.successful(Responder(body = req.body))

    case req @ Root / "echo2" =>
      Future.successful(Responder(body = req.body &> Enumeratee.map[Chunk](e => e.slice(6, e.length))))

    case req @ Post(Root / "sum") =>
      stringHandler(req, 16) { s =>
        val sum = s.split('\n').map(_.toInt).sum
        Responder[Chunk](body = Enumerator(sum.toString.getBytes))
      }

    case req @ Root / "stream" =>
      Future.successful(Responder(body = Concurrent.unicast({
        channel =>
          for (i <- 1 to 10) {
            channel.push("%d\n".format(i).getBytes)
            Thread.sleep(1000)
          }
          channel.eofAndEnd()
      })))

    case req @ Root / "/igstring" =>
      Future.successful{
        val builder = new StringBuilder(20*1028)

        Responder( body =Enumerator(((0 until 1000) map { i =>
          s"This is string number $i".getBytes
        }): _*) )
      }

    // Reads the whole body before responding
    case req @ Root / "determine_echo1" =>
      req.body.run( Iteratee.getChunks).map {bytes =>
        Responder( body = Enumerator(bytes:_*))
      }

    // Demonstrate how simple it is to read some and then continue
    case req @ Root / "determine_echo2" =>
      val bit: Future[Option[Chunk]] = req.body.run(Iteratee.head)
      bit.map {
        case Some(bit) => Responder( body = Enumerator(bit) >>> req.body )
        case None => Responder( body = Enumerator.eof )
      }

    case req @ Root / "fail" =>
      sys.error("FAIL")
}

  def stringHandler(req: Request[Chunk], maxSize: Int = Integer.MAX_VALUE)(f: String => Responder[Chunk]): Future[Responder[Chunk]] = {
    val it = (Traversable.takeUpTo[Chunk](maxSize)
                transform bytesAsString(req)
                flatMap eofOrRequestTooLarge(f)
                map (_.merge))
    req.body.run(it)
  }

  private[this] def bytesAsString(req: Request[Chunk]) =
    Iteratee.consume[Chunk]().asInstanceOf[Iteratee[Chunk, Chunk]].map(new String(_, req.charset))

  private[this] def eofOrRequestTooLarge[B](f: String => Responder[Chunk])(s: String) =
    Iteratee.eofOrElse[B](Responder(statusLine = StatusLine.RequestEntityTooLarge, body = EmptyBody))(s).map(_.right.map(f))

}