package org.http4s
package websocket

import play.api.libs.iteratee.{Iteratee, Enumerator}
import concurrent.ExecutionContext


/**
 * @author Bryce Anderson
 * Created on 2/16/13 at 6:20 PM
 */

/* TODO: In flux.
trait WebSocketApp {
  def uri: String
  def route: WebSocketApp.WebSocketRoute

}

object WebSocketApp {
  type WebSocketRoute = RequestPrelude => (Iteratee[WebMessage,_], Enumerator[WebMessage])

  // Provided by Grizzly backend
  def apply(uri: String)(route: WebSocketApp.WebSocketRoute)
           (implicit ctx: ExecutionContext = ExecutionContext.global) =
    grizzly.GrizzlyWebSocketApp(uri)(route)(ctx)
}
*/