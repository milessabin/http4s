package org.http4s

import scala.language.implicitConversions

trait Writable[-A] {
  def contentType: ContentType
  def toRaw(a: A): Raw
}

object Writable {
  implicit def stringWritable(implicit charset: HttpCharset = HttpCharsets.`UTF-8`) =
    new Writable[String] {
      def contentType: ContentType = ContentType.`text/plain`.withCharset(charset)
      def toRaw(s: String): Raw = s.getBytes(charset.nioCharset)
    }

  implicit def intWritable(implicit charset: HttpCharset = HttpCharsets.`UTF-8`) =
    new Writable[Int] {
      def contentType: ContentType = ContentType.`text/plain`.withCharset(charset)
      def toRaw(i: Int): Raw = i.toString.getBytes(charset.nioCharset)
    }

  implicit def rawWritable =
    new Writable[Raw] {
      def contentType: ContentType = ContentType.`application/octet-stream`
      def toRaw(raw: Raw): Raw = raw
    }

  implicit def chunkWritable =
    new Writable[HttpChunk] {
      def contentType: ContentType = ContentType.`application/octet-stream`
      def toRaw(chunk: HttpChunk): Raw = chunk.bytes
    }

  implicit def traversableWritable[A](implicit writable:Writable[A]) =
    new Writable[TraversableOnce[A]] {
      def contentType: ContentType = writable.contentType
      def toRaw(as: TraversableOnce[A]): Raw = as.foldLeft(Array.empty[Byte]) { (acc, a) => acc ++ writable.toRaw(a) }
    }
}

