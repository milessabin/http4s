// Resurrected and adapted from the late Akka PromiseStream.
// https://raw.github.com/akka/akka/3ececefb658b2b7a64894aa904380e4635635ef7/akka-actor-tests/src/test/scala/akka/dispatch/PromiseStreamSpec.scala
package org.http4s

import akka.dataflow.flow
import akka.util.Timeout
import org.specs2.mutable.Specification
import concurrent.{Future, Await, Promise}
import scala.concurrent.duration._
import org.specs2.time.NoTimeConversions
import akka.actor.ActorSystem
import cps.repeatC

class PromiseStreamSpec extends Specification with NoTimeConversions {
  implicit val timeout = Timeout(3 seconds)
  implicit val actorSystem = ActorSystem()
  import scala.concurrent.ExecutionContext.Implicits.global

  "A PromiseStream" should {

    "work" in {
      val a, b, c = Promise[Int]()
      val q = PromiseStream[Int]()
      flow { q << (1, 2, 3) }
      flow {
        a << q()
        b << q
        c << q()
      }
      Await.result(a.future, timeout.duration) must_== 1
      Await.result(b.future, timeout.duration) must_== 2
      Await.result(c.future, timeout.duration) must_== 3
    }

    "pend" in {
      val a, b, c = Promise[Int]()
      val q = PromiseStream[Int]()
      flow {
        a << q
        b << q()
        c << q
      }
      flow { q <<< List(1, 2, 3) }
      assert(Await.result(a.future, timeout.duration) === 1)
      assert(Await.result(b.future, timeout.duration) === 2)
      assert(Await.result(c.future, timeout.duration) === 3)
    }

    "pend again" in {
      val a, b, c, d = Promise[Int]()
      val q1, q2 = PromiseStream[Int]()
      val oneTwo = Future(List(1, 2))
      flow {
        a << q2
        b << q2
        q1 << 3 << 4
      }
      flow {
        q2 <<< oneTwo
        c << q1
        d << q1
      }
      assert(Await.result(a.future, timeout.duration) === 1)
      assert(Await.result(b.future, timeout.duration) === 2)
      assert(Await.result(c.future, timeout.duration) === 3)
      assert(Await.result(d.future, timeout.duration) === 4)
    }

    "enque" in {
      val q = PromiseStream[Int]()
      val a = q.dequeue()
      val b = q.dequeue()
      val c, d = Promise[Int]()
      flow {
        c << q
        d << q
      }
      q ++= List(1, 2, 3, 4)

      Await.result(a, timeout.duration) must_== 1
      Await.result(b, timeout.duration) must_== 2
      Await.result(c.future, timeout.duration) must_== 3
      Await.result(d.future, timeout.duration) must_== 4
    }

    "map" in {
      val qs = PromiseStream[String]()
      val qi = qs.map(_.length)
      val a, c = Promise[Int]()
      val b = Promise[String]()
      flow {
        a << qi
        b << qs
        c << qi
      }
      flow {
        qs << ("Hello", "World!", "Test")
      }
      Await.result(a.future, timeout.duration) must_== 5
      Await.result(b.future, timeout.duration) must_== "World!"
      Await.result(c.future, timeout.duration) must_== 4
    }

    "map futures" in {
      val q = PromiseStream[String]()
      flow {
        q << (Future("a"), Future("b"), Future("c"))
      }
      val a, b, c = q.dequeue
      Await.result(a, timeout.duration) must be("a")
      Await.result(b, timeout.duration) must be("b")
      Await.result(c, timeout.duration) must be("c")
    }

    "not fail under concurrent stress" in {
      implicit val timeout = Timeout(60 seconds)
      val q = PromiseStream[Long](timeout.duration.toMillis)

      flow {
        var n = 0L
        repeatC(50000) {
          n += 1
          q << n
        }
      }

      val future = Future sequence {
        List.fill(10) {
          flow {
            var total = 0L
            repeatC(10000) {
              val n = q()
              total += n
            }
            total
          }
        }
      } map (_.sum)

      flow {
        var n = 50000L
        repeatC(50000) {
          n += 1
          q << n
        }
      }

      Await.result(future, timeout.duration) must_== (1L to 100000L).sum
    }
  }
}