package org.http4s

import scala.language.reflectiveCalls
import org.http4s.iteratee._
import java.io._
import xml.{Elem, XML, NodeSeq}
import org.xml.sax.{SAXException, InputSource}
import javax.xml.parsers.SAXParser
import scala.util.{Success, Try}
import org.http4s.iteratee.Enumeratee.CheckDone
import concurrent.{Future, ExecutionContext}

case class BodyParser[A](it: Iteratee[HttpChunk, Either[Responder, A]]) {
  def respond(f: A => Responder)(implicit executor: ExecutionContext): Iteratee[HttpChunk, Responder] = it.map(_.right.map(f).merge)
  def map[B](f: A => B)(implicit executor: ExecutionContext): BodyParser[B] = BodyParser(it.map[Either[Responder, B]](_.right.map(f)))
  def flatMap[B](f: A => BodyParser[B])(implicit executor: ExecutionContext): BodyParser[B] =
    BodyParser(it.flatMap[Either[Responder, B]](_.fold(
      { responder: Responder => Done(Left(responder)) },
      { a: A => f(a).it }
    )))
  def joinRight[A1 >: A, B](implicit ev: <:<[A1, Either[Responder, B]], executor: ExecutionContext): BodyParser[B] = BodyParser(it.map(_.joinRight))
}

object BodyParser {
  val DefaultMaxEntitySize = Http4sConfig.getInt("org.http4s.default-max-entity-size")

  private def BodyChunkConsumer(implicit executor: ExecutionContext): Iteratee[BodyChunk, BodyChunk] = Iteratee.consume[BodyChunk]()

  implicit def bodyParserToResponderIteratee(bodyParser: BodyParser[Responder])(implicit executor: ExecutionContext): Iteratee[HttpChunk, Responder] =
    bodyParser.respond(identity)

  def text[A](charset: HttpCharset, limit: Int = DefaultMaxEntitySize)(implicit executor: ExecutionContext): BodyParser[String] =
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
          onSaxException: SAXException => Responder = { saxEx => saxEx.printStackTrace(); Status.BadRequest() })(implicit executor: ExecutionContext)
  : BodyParser[Elem] =
    consumeUpTo(BodyChunkConsumer, limit).map { bytes =>
      val in = bytes.iterator.asInputStream
      val source = new InputSource(in)
      source.setEncoding(charset.value)
      Try(XML.loadXML(source, parser)).map(Right(_)).recover {
        case e: SAXException => Left(onSaxException(e))
      }.get
    }.joinRight

  def ignoreBody(implicit executor: ExecutionContext): BodyParser[Unit] = BodyParser(whileBodyChunk &>> Iteratee.ignore[BodyChunk].map(Right(_)))

  def trailer(implicit executor: ExecutionContext): BodyParser[TrailerChunk] = BodyParser(
    Enumeratee.dropWhile[HttpChunk](_.isInstanceOf[BodyChunk]) &>>
      (Iteratee.head[HttpChunk].map {
        case Some(trailer: TrailerChunk) => trailer
        case _ => TrailerChunk()
      }.map(Right(_))))

  def consumeUpTo[A](consumer: Iteratee[BodyChunk, A], limit: Int = DefaultMaxEntitySize)(implicit executor: ExecutionContext): BodyParser[A] = {
    val it = for {
      a <- Traversable.takeUpTo[BodyChunk](limit) &>> consumer
      tooLargeOrA <- Iteratee.eofOrElse(Status.RequestEntityTooLarge())(a)
    } yield tooLargeOrA
    BodyParser(whileBodyChunk &>> it)
  }

  val whileBodyChunk: Enumeratee[HttpChunk, BodyChunk] = new CheckDone[HttpChunk, BodyChunk] {
    def step[A](k: K[BodyChunk, A])(implicit executor: ExecutionContext): K[HttpChunk, Iteratee[BodyChunk, A]] = {
      case in @ Input.El(e: BodyChunk) =>
        new CheckDone[HttpChunk, BodyChunk] {
          def continue[A](k: K[BodyChunk, A])(implicit executor: ExecutionContext) = Cont(step(k))
        } &> k(in.asInstanceOf[Input[BodyChunk]])
      case in @ Input.El(e) =>
        Done(Cont(k), in)
      case in @ Input.Empty =>
        new CheckDone[HttpChunk, BodyChunk] { def continue[A](k: K[BodyChunk, A])(implicit executor: ExecutionContext) = Cont(step(k)) } &> k(in)
      case Input.EOF => Done(Cont(k), Input.EOF)
    }
    def continue[A](k: K[BodyChunk, A])(implicit executor: ExecutionContext) = Cont(step(k))
  }

  // File operations
  def binFile(file: java.io.File)(f: => Responder)(implicit executor: ExecutionContext): Iteratee[HttpChunk,Responder] = {
    val out = new java.io.FileOutputStream(file)
    whileBodyChunk &>> Iteratee.foreach[BodyChunk]{ d => out.write(d.toArray) }.map{ _ => out.close(); f }
  }

  def textFile(req: RequestPrelude, in: java.io.File)(f: => Responder)(implicit executor: ExecutionContext): Iteratee[HttpChunk,Responder] = {
    val is = new java.io.PrintStream(new FileOutputStream(in))
    whileBodyChunk &>> Iteratee.foreach[BodyChunk]{ d => is.print(d.decodeString(req.charset)) }.map{ _ => is.close(); f }
  }
}
