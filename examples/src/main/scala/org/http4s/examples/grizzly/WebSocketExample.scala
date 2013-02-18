package org.http4s
package grizzly

/**
 * @author Bryce Anderson
 * Created on 2/16/13 at 11:02 PM
 */

import org.http4s.StatusLine._
import play.api.libs.iteratee.{Done, Iteratee,Concurrent}

import websocket._

/*
Simple WebSocket example which demonstrates how simple it is to build WebSocket based apps with Http4s
 */

object WebSocketExample extends App {
  val webSocketApp = WebSocketApp("/websocket"){
    // We only need to define our input and output streams in the form of an Iteratee and an Enumerator
    var chan: Concurrent.Channel[WebPacket] = null
    val enum = Concurrent.unicast[WebPacket](chan=_)
    val it = Iteratee.foreach[WebPacket]{
      case StringPacket(s) =>
        val str = s"Received string: $s"
        println(str)
        chan.push(StringPacket(str))

      case BytePacket(_) => sys.error("Received BytePacket... That is Unexpected!")

    }.map(_ => println("Connection Closed! This is from the App..."))

    (it,enum)
  }

  val server = SimpleGrizzlyServer()({
    case req => Done(Ok("Hello world"))  // Define a route
  }, webSocketApp)                       // Attach our webSocketApp
  println("Hello world!")
}
