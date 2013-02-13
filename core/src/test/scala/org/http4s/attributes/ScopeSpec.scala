package org.http4s
package attributes

import org.specs2.mutable.Specification
import concurrent.Future
import play.api.libs.iteratee.Enumerator


class ScopeSpec extends Specification {

  val h = new RouteHandler {
    def route: _root_.org.http4s.Route = {
      case x => Future.failed(sys.error("not used"))
    }
  }

  val req = Request[Chunk](body = Enumerator.eof)

  "A list of scopes" should {
    "sort from high to low ranking" in {
      val nw = List(ThisApp(h), ThisServer, ThisRequest(req)).sorted
      nw must_== List(ThisRequest(req), ThisApp(h), ThisServer)
    }
  }
}