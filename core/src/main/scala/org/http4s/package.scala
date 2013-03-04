package org

import http4s.attributes._
import http4s.attributes.AppScope
import http4s.attributes.RequestScope
import http4s.ext.Http4sString
import http4s.HttpHeaders.RawHeader
import http4s.parser.HttpParser
import play.api.libs.iteratee.{Done, Enumeratee, Iteratee, Enumerator}
import scala.language.implicitConversions
import concurrent.{ExecutionContext, Future}
import java.net.{InetAddress, URI}
import java.io.File
import java.util.UUID
import java.nio.charset.Charset
import akka.util.ByteString
import com.typesafe.config.{ConfigFactory, Config}
import scalaz.{Monad, EitherT}

//import spray.http.HttpHeaders.RawHeader

package object http4s {
  type ChunkIteratee[+A] = Iteratee[HttpChunk, A]
  implicit val chunkIterateeMonad: Monad[ChunkIteratee] = new Monad[ChunkIteratee] {
    def point[A](a : => A) = Done(a)
    def bind[A, B](fa: ChunkIteratee[A])(f: A => ChunkIteratee[B]): ChunkIteratee[B] = fa.flatMap(f)
  }

  type Route = PartialFunction[RequestPrelude, ChunkIteratee[Responder]]

  type ResponderBody = Enumeratee[HttpChunk, HttpChunk]

  type Middleware = (Route => Route)

  type BodyParser[A] = EitherT[ChunkIteratee, Responder, A]

  private[http4s] implicit def string2Http4sString(s: String) = new Http4sString(s)

  trait RouteHandler {
    implicit val appScope = AppScope()
    val attributes = new AttributesView(GlobalState.forScope(appScope))
    def apply(implicit executionContext: ExecutionContext): Route

  }

  protected[http4s] val Http4sConfig: Config = ConfigFactory.load()

  implicit object GlobalState extends attributes.ServerContext

  implicit def attribute2scoped[T](attributeKey: AttributeKey[T]) = new attributes.ScopableAttributeKey(attributeKey)
  implicit def request2scope(req: RequestPrelude) = RequestScope(req.uuid)
  implicit def app2scope(routes: RouteHandler) = routes.appScope
  implicit def attribute2defaultScope[T, S <: Scope](attributeKey: AttributeKey[T])(implicit scope: S) = attributeKey in scope

  val Get = Method.Get
  val Post = Method.Post
  val Put = Method.Put
  val Delete = Method.Delete
  val Trace = Method.Trace
  val Options = Method.Options
  val Patch = Method.Patch
  val Head = Method.Head
  val Connect = Method.Connect

  /*
  type RequestRewriter = PartialFunction[Request, Request]

  def rewriteRequest(f: RequestRewriter): Middleware = {
    route: Route => f.orElse({ case req: Request => req }: RequestRewriter).andThen(route)
  }

  type ResponseTransformer = PartialFunction[Response, Response]

  def transformResponse(f: ResponseTransformer): Middleware = {
    route: Route => route andThen { handler => handler.map(f) }
  }
  */
}