// Hack to see what happens if Play runs in global executor.
package play.api.libs.iteratee {

private[iteratee] object internal {
  import scala.concurrent.ExecutionContext
  import java.util.concurrent.Executors
  import java.util.concurrent.ThreadFactory
  import java.util.concurrent.atomic.AtomicInteger

  implicit lazy val defaultExecutionContext: scala.concurrent.ExecutionContext =
    ExecutionContext.global
}
}