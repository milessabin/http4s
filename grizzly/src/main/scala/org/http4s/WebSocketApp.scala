package org.http4s

import play.api.libs.iteratee.{Input, Iteratee, Enumerator}
import concurrent.ExecutionContext


/**
 * @author Bryce Anderson
 * Created on 2/16/13 at 6:20 PM
 */

trait WebSocketApp {
  def uri: String
  def route: WebSocketApp.WebSocketRoute

}

object WebSocketApp {
  type WebSocketRoute = (Iteratee[String,_], Enumerator[String])

  // Provided by Grizzly backend
  def apply(uri: String)(route: WebSocketApp.WebSocketRoute)
           (implicit ctx: ExecutionContext = ExecutionContext.global) =
    grizzly.GrizzlyWebSocketApp(uri)(route)(ctx)
}
