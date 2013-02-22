package org.http4s.util

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._

import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions

import Spool.{*::, **::}
import concurrent.{Promise, Await, Future}
import play.api.libs.iteratee.{Iteratee, Enumerator}

class SpoolSpec extends Specification with NoTimeConversions {
  isolated

  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val timeout = 5 seconds

  "Simple resolved Spool" should {
    val s = 1 **:: 2 **:: Spool.empty

    "iterate over all elements" in {
      val xs = new ArrayBuffer[Int]
      s foreach { xs += _ }
      xs.toSeq must be_==(Seq(1,2))
    }

    "buffer to a sequence" in {
      Await.result(s.toSeq, 2 seconds) must be_==(Seq(1, 2))
    }

    "map" in {
      Await.result((s map { _ * 2 } toSeq), 2 seconds) must be_==(Seq(2, 4))
    }

    "deconstruct" in {
      s must beLike {
        case 1 **:: 2 **:: rest if rest.isEmpty => ok
      }
    }

    "append via ++"  in {
      Await.result((s ++ Spool.empty[Int]).toSeq, 2 seconds) must be_==(Seq(1, 2))
      Await.result((Spool.empty[Int] ++ s).toSeq, 2 seconds) must be_==(Seq(1, 2))

      val s2 = s ++ (3 **:: 4 **:: Spool.empty)
      Await.result(s2.toSeq, 2 seconds) must be_==(Seq(1, 2, 3, 4))
    }

    "append via ++ with Future rhs"  in {
      Await.result((s ++ Future.successful(Spool.empty[Int])).flatMap(_.toSeq), 2 seconds) must be_==(Seq(1, 2))
      Await.result((Spool.empty[Int] ++ Future(s)).flatMap(_.toSeq), 2 seconds) must be_==(Seq(1, 2))

      val s2 = s ++ Future(3 **:: 4 **:: Spool.empty)
      Await.result(s2.flatMap(_.toSeq), 2 seconds) must be_==(Seq(1, 2, 3, 4))
    }

    "flatMap" in {
      val f = (x: Int) => Future(x.toString **:: (x * 2).toString **:: Spool.empty)
      val s2 = s flatMap f
      Await.result(s2.flatMap(_.toSeq), 2 seconds) must be_==(Seq("1", "2", "2", "4"))
    }
  }

  "Simple resolved spool with error" should {
    val p = Future.failed[Spool[Int]](new Exception("sad panda"))
    val s = 1 **:: 2 *:: p

    "EOF iteration on error" in {
      val xs = new ArrayBuffer[Option[Int]]
      s foreachElem { xs += _ }
      xs.toSeq must be_==(Seq(Some(1), Some(2), None))
    }
  }

  "Simple delayed Spool" should {
    val p = Promise[Spool[Int]]
    val p1 = Promise[Spool[Int]]
    val p2 = Promise[Spool[Int]]
    val s = 1 *:: p.future

    "iterate as results become available" in {
      val xs = new ArrayBuffer[Int]
      s foreach { xs += _ }
      xs.toSeq must be_==(Seq(1))
      p.success(2 *:: p1.future)
      xs.toSeq must be_==(Seq(1, 2))
      p1.success(Spool.empty)
      xs.toSeq must be_==(Seq(1, 2))
    }

    "EOF iteration on failure" in {
      val xs = new ArrayBuffer[Option[Int]]
      s foreachElem { xs += _ }
      xs.toSeq must be_==(Seq(Some(1)))
      p.failure(new Exception("sad panda"))
      xs.toSeq must be_==(Seq(Some(1), None))
    }

    "return a buffered seq when complete" in {
      val f = s.toSeq
      f.isCompleted must beFalse
      p.success(2 *:: p1.future)
      f.isCompleted must beFalse
      p1.success(Spool.empty)
      f.isCompleted must beTrue
      Await.result(f, 2 seconds) must be_==(Seq(1,2))
    }

/*
    "deconstruct" in {
      s must beLike {
        case fst *:: rest if fst == 1 && !rest.isCompleted => ok
      }
    }

    "collect" in {
      val f = s collect {
        case x if x % 2 == 0 => x * 2
      }

      f.isCompleted must beFalse  // 1 != 2 mod 0
      p.success(2 *:: p1.future)
      f.isCompleted must beTrue
      val s1 = Await.result(f, 2 seconds)
      s1 must beLike {
        case x *:: rest if x == 4 && !rest.isCompleted => ok
      }
      p1.success(3 *:: p2.future)
      s1 must beLike {
        case x *:: rest if x == 4 && !rest.isCompleted => ok
      }
      p2.success(4 **:: Spool.empty)
      val s1s = s1.toSeq
      s1s.isCompleted must beTrue
      Await.result(s1s, 2 seconds) must be_==(Seq(4, 8))
    }
*/
  }

  "Large spools" should {
    "not blow the stack" in {
      val ss = new SpoolSource[Long]
      val s = ss()
      val seq = s.flatMap(_.toSeq).map(_.sum)
      for (i <- 0 to 1000000) {
        ss.offer(i)
      }
      ss.close()
      Await.result(seq, 15 seconds) must be_==(500000500000L)
    }

    "not eat all the heap" in {
      var ss = new SpoolSource[Array[Byte]]
      val app: Future[Spool[Array[Byte]]] => Unit = { case x => x.map(_.map(_.length)) }
      var req = Tuple1(ss())
      app(req._1)
      req = null
      for (i <- 0 to 1000000) {
        try {
          ss.offer(new Array[Byte](1024 * 64))
        }
        catch {
          case e: OutOfMemoryError =>
            ss = null
            System.gc()
            sys.error("Died at "+i)
        }
      }
      ss.close()
      assert(true)
    }
  }
}