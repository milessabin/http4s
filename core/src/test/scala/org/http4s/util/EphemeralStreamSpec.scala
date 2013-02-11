package org.http4s.util

import org.specs2.mutable.Specification
import io.Source

class EphemeralStreamSpec extends Specification {
  "An ephemeral stream" should {
    "should not leak memory if the head is held" in {
      val s = EphemeralStream(Stream.continually(new Array[Int](1024 * 1024)))
      s.drop(1024 * 1024).take(1)
      // Congratulations on not running out of heap.
      assert(true)
    }
  }
}
