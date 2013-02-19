package org.http4s
package grizzly

/**
 * @author Bryce Anderson
 * Created on 2/16/13 at 11:02 PM
 */

import org.http4s.Status._
import play.api.libs.iteratee.{Done, Iteratee,Concurrent}

import websocket._

/*
Simple WebSocket example which demonstrates how simple it is to build WebSocket based apps with Http4s
 */

object WebSocketExample extends App {


  val server = SimpleGrizzlyServer()({

    case req if req.pathInfo == "/websocket" =>
      WebSocket {
        var chan: Concurrent.Channel[WebMessage] = null
        val enum = Concurrent.unicast[WebMessage](chan=_)
        val it = Iteratee.foreach[WebMessage]{
          case StringMessage(s) =>
            val str = s"Received string: $s"
            println(str)
            chan.push(StringMessage(str))

          case ByteMessage(_) => sys.error("Received ByteMessage... That is Unexpected!")

        }.map(_ => println("Connection Closed! This is from the App..."))

        (it,enum)
      }

    case req => Done(Ok("Hello world"))  // Define a route
  })                       // Attach our webSocketApp
  println("Hello world!")
}
