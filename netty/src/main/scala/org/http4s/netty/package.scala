package org.http4s

import org.jboss.netty.channel.{ChannelFutureListener, ChannelFuture, Channel}
import concurrent.{CanAwait, ExecutionContext, Promise, Future}
import scala.util.{Try, Failure, Success}
import scala.language.implicitConversions
import org.jboss.netty.handler.codec.http.{ HttpMethod => JHttpMethod, HttpResponseStatus }
import Method._
import concurrent.duration._

package object netty {

  class Cancelled(val channel: Channel) extends Throwable
  class ChannelError(val channel: Channel, val reason: Throwable) extends Throwable

  object NettyPromise {

    def apply(channelPromise: ChannelFuture): Future[Unit] = new scala.concurrent.Future[Unit] {
      parent =>

      def isCompleted: Boolean = channelPromise.isDone

      def onComplete[U](func: (Try[Unit]) ⇒ U)(implicit executor: ExecutionContext): Unit = channelPromise.addListener(new ChannelFutureListener {
        def operationComplete(future: ChannelFuture) {
          val r = if (future.isSuccess()) Success(()) else Failure(future.getCause())
          executor.execute(new Runnable() { def run() { func(r) } })
        }
      })

      def ready(atMost: Duration)(implicit permit: CanAwait): this.type = {
        if (channelPromise.await(atMost.toMillis))
          this
        else throw new scala.concurrent.TimeoutException("netty channel future await timeout after: " + atMost)
      }

      def result(atMost: Duration)(implicit permit: CanAwait) {
        val done = (channelPromise.await(atMost.toMillis))
        (done, channelPromise.isSuccess) match {
          case (false, _) => throw new scala.concurrent.TimeoutException("netty channel future await timeout after: " + atMost)
          case (true, false) => throw channelPromise.getCause
          case (true, true) => ()

        }
      }

      def value: Option[Try[Unit]] = (channelPromise.isDone, channelPromise.isSuccess) match {
        case (true, true) => Some(Success(()))
        case (true, false) => Some(Failure(channelPromise.getCause))
        case _ => None
      }
    }
  }


  private[netty] implicit def channelFuture2Future(cf: ChannelFuture)(implicit executionContext: ExecutionContext): Future[Unit] = {
//    val prom = Promise[Channel]()
//    cf.addListener(new ChannelFutureListener {
//      def operationComplete(future: ChannelFuture) {
//        if (future.isSuccess) {
//          prom.complete(Success(future.getChannel))
//        } else if (future.isCancelled) {
//          prom.complete(Failure(new Cancelled(future.getChannel)))
//        } else {
//          prom.complete(Failure(new ChannelError(future.getChannel, future.getCause)))
//        }
//      }
//    })
//    prom.future
    NettyPromise(cf)
  }

  implicit def jHttpMethod2HttpMethod(orig: JHttpMethod): Method = orig match {
    case JHttpMethod.CONNECT => Connect
    case JHttpMethod.DELETE => Delete
    case JHttpMethod.GET => Get
    case JHttpMethod.HEAD => Head
    case JHttpMethod.OPTIONS => Options
    case JHttpMethod.PATCH => Patch
    case JHttpMethod.POST => Post
    case JHttpMethod.PUT => Put
    case JHttpMethod.TRACE => Trace
  }

  implicit def respStatus2nettyStatus(stat: Status) = new HttpResponseStatus(stat.code, stat.reason.blankOption.getOrElse(""))
  implicit def respStatus2nettyStatus(stat: HttpResponseStatus) = Status(stat.getCode, stat.getReasonPhrase)
  implicit def httpVersion2nettyVersion(ver: HttpVersion) = ver match {
    case HttpVersion(1, 1) => org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1
    case HttpVersion(1, 0) => org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_0
  }
}
