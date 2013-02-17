package org.http4s.examples.grizzly

/**
 * @author Bryce Anderson
 * Created on 2/16/13 at 11:02 PM
 */

import org.http4s.grizzly._
import org.http4s.StatusLine._
import org.http4s.WebSocketApp
import play.api.libs.iteratee.{Done, Iteratee, Enumerator,Concurrent}


object WebSocket extends App {
  val webSocketApp = WebSocketApp("/websocket"){
    str =>
      var chan: Concurrent.Channel[String] = null
      val enum = Concurrent.unicast[String](chan=_)
      val it: Iteratee[String,Unit] = Iteratee.foreach{ s =>
        val str = s"Received string $s"
        println(str)
        chan.push(str)
      }
      val f = { e: String => println(s"Blah $e"): Unit }

      (it,enum,f)
  }

  val server = new SimpleGrizzlyServer()(Seq({
    case req => Done(Ok("Hello world"))
  }), webSocketApp)
  println("Hello world!")
}
