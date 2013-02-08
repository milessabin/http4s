package org

import play.api.libs.iteratee.Iteratee
import concurrent.{ExecutionContext, Future, Promise}
import scala.util.continuations._
import scala.util.control.NonFatal
import scala.collection.{GenTraversableOnce, IterableLike}
import scala.collection.generic.CanBuildFrom

package object http4s {
  type Route = PartialFunction[Request, Handler]

  type Handler = Iteratee[Chunk, Responder]

  type Chunk = Array[Byte]

  type Middleware = (Route => Route)

  implicit class DataflowFuture[T](val future: Future[T]) extends AnyVal {
    /**
     * For use only within a Future.flow block or another compatible Delimited Continuations reset block.
     *
     * Returns the result of this Future without blocking, by suspending execution and storing it as a
     * continuation until the result is available.
     */
    final def apply()(implicit ec: ExecutionContext): T @cps[Future[Any]] = shift(future flatMap (_: T ⇒ Future[Any]))
  }

  implicit class DataflowPromise[T](val promise: Promise[T]) extends AnyVal {
    /**
     * Completes the Promise with the specified value or throws an exception if already
     * completed. See Promise.success(value) for semantics.
     *
     * @param value The value which denotes the successful value of the Promise
     * @return This Promise's Future
     */
    final def <<(value: T): Future[T] @cps[Future[Any]] = shift {
      cont: (Future[T] ⇒ Future[Any]) ⇒ cont(promise.success(value).future)
    }

    /**
     * Completes this Promise with the value of the specified Future when/if it completes.
     *
     * @param other The Future whose value will be transferred to this Promise upon completion
     * @param ec An ExecutionContext which will be used to execute callbacks registered in this method
     * @return A Future representing the result of this operation
     */
    final def <<(other: Future[T])(implicit ec: ExecutionContext): Future[T] @cps[Future[Any]] = shift {
      cont: (Future[T] ⇒ Future[Any]) ⇒
        val fr = Promise[Any]()
        (promise completeWith other).future onComplete {
          v ⇒ try { fr completeWith cont(promise.future) } catch { case NonFatal(e) ⇒ fr failure e }
        }
        fr.future
    }

    /**
     * Completes this Promise with the value of the specified Promise when/if it completes.
     *
     * @param other The Promise whose value will be transferred to this Promise upon completion
     * @param ec An ExecutionContext which will be used to execute callbacks registered in this method
     * @return A Future representing the result of this operation
     */
    final def <<(other: Promise[T])(implicit ec: ExecutionContext): Future[T] @cps[Future[Any]] = <<(other.future)

    /**
     * For use only within a flow block or another compatible Delimited Continuations reset block.
     *
     * Returns the result of this Promise without blocking, by suspending execution and storing it as a
     * continuation until the result is available.
     */
    final def apply()(implicit ec: ExecutionContext): T @cps[Future[Any]] = shift(promise.future flatMap (_: T ⇒ Future[Any]))

    final def <<(stream: PromiseStreamOut[T])(implicit executor: ExecutionContext): Future[T] @cps[Future[Any]] = shift { cont: (Future[T] ⇒ Future[Any]) ⇒
      val fr = Promise[Any]()
      val f = stream.dequeue(promise)
      f.onComplete { _ ⇒
        try {
          fr completeWith cont(f)
        } catch {
          case NonFatal(e) ⇒
            // TODO Crashes the compiler!
            // executor.reportFailure(new LogEventException(Debug("Future", getClass, e.getMessage), e))
            fr failure e
        }
      }
      fr.future
    }
  }

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