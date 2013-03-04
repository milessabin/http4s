package org.http4s

import scala.language.reflectiveCalls
import play.api.libs.iteratee._
import java.io._
import xml.{Elem, XML, NodeSeq}
import org.xml.sax.{SAXException, InputSource}
import javax.xml.parsers.SAXParser
import scala.util.{Success, Try}
import play.api.libs.iteratee.Enumeratee.CheckDone
import scalaz._
import scalaz.Scalaz._

object BodyParser {

  val DefaultMaxEntitySize = Http4sConfig.getInt("org.http4s.default-max-entity-size")

  private val BodyChunkConsumer: Iteratee[BodyChunk, BodyChunk] = Iteratee.consume[BodyChunk]()

  def apply[A](run: ChunkIteratee[Responder \/ A]): BodyParser[A] = EitherT[ChunkIteratee, Responder, A](run)

  implicit class BodyParserOps[A](bodyParser: BodyParser[A]) {
    def respond(f: A => Responder): ChunkIteratee[Responder] = bodyParser.map(f).fold(identity, identity)
  }

  def text[A](charset: HttpCharset, limit: Int = DefaultMaxEntitySize): BodyParser[String] =
    consumeUpTo(BodyChunkConsumer, limit).map(_.decodeString(charset))

  /**
   * Handles a request body as XML.
   *
   * TODO Not an ideal implementation.  Would be much better with an asynchronous XML parser, such as Aalto.
   *
   * @param charset the charset of the input
   * @param limit the maximum size before an EntityTooLarge error is returned
   * @param parser the SAX parser to use to parse the XML
   * @return a request handler
   */
  def xml(charset: HttpCharset,
          limit: Int = DefaultMaxEntitySize,
          parser: SAXParser = XML.parser,
          onSaxException: SAXException => Responder = { saxEx => saxEx.printStackTrace(); Status.BadRequest() })
  : BodyParser[Elem] =
    BodyParser(consumeUpTo(BodyChunkConsumer, limit).map[Responder \/ Elem] { bytes =>
      val in = bytes.iterator.asInputStream
      val source = new InputSource(in)
      source.setEncoding(charset.value)
      try {
        \/-(XML.loadXML(source, parser))
      } catch {
        case e: SAXException => -\/(onSaxException(e))
      }
    }.run.map(_.join))

  def ignoreBody: BodyParser[Unit] = BodyParser(whileBodyChunk &>> Iteratee.ignore[BodyChunk].map(\/-(_)))

  def trailer: BodyParser[TrailerChunk] = BodyParser(
    Enumeratee.dropWhile[HttpChunk](_.isInstanceOf[BodyChunk]) &>>
      (Iteratee.head[HttpChunk].map {
        case Some(trailer: TrailerChunk) => trailer
        case _ => TrailerChunk()
      }.map(\/-(_))))

  def consumeUpTo[A](consumer: Iteratee[BodyChunk, A], limit: Int = DefaultMaxEntitySize): BodyParser[A] = {
    val it = for {
      a <- Traversable.takeUpTo[BodyChunk](limit) &>> consumer
      tooLargeOrA <- Iteratee.eofOrElse(Status.RequestEntityTooLarge())(a)
    } yield tooLargeOrA
    BodyParser(whileBodyChunk &>> it.map(\/.fromEither))
  }

  val whileBodyChunk: Enumeratee[HttpChunk, BodyChunk] = new CheckDone[HttpChunk, BodyChunk] {
    def step[A](k: K[BodyChunk, A]): K[HttpChunk, Iteratee[BodyChunk, A]] = {
      case in @ Input.El(e: BodyChunk) =>
        new CheckDone[HttpChunk, BodyChunk] {
          def continue[A](k: K[BodyChunk, A]) = Cont(step(k))
        } &> k(in.asInstanceOf[Input[BodyChunk]])
      case in @ Input.El(e) =>
        Done(Cont(k), in)
      case in @ Input.Empty =>
        new CheckDone[HttpChunk, BodyChunk] { def continue[A](k: K[BodyChunk, A]) = Cont(step(k)) } &> k(in)
      case Input.EOF => Done(Cont(k), Input.EOF)
    }
    def continue[A](k: K[BodyChunk, A]) = Cont(step(k))
  }

  // File operations
  def binFile(file: java.io.File)(f: => Responder): Iteratee[HttpChunk,Responder] = {
    val out = new java.io.FileOutputStream(file)
    whileBodyChunk &>> Iteratee.foreach[BodyChunk]{ d => out.write(d.toArray) }.map{ _ => out.close(); f }
  }

  def textFile(req: RequestPrelude, in: java.io.File)(f: => Responder): Iteratee[HttpChunk,Responder] = {
    val is = new java.io.PrintStream(new FileOutputStream(in))
    whileBodyChunk &>> Iteratee.foreach[BodyChunk]{ d => is.print(d.decodeString(req.charset)) }.map{ _ => is.close(); f }
  }
}
