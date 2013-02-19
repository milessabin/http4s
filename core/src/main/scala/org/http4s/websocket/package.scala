package org.http4s

import play.api.libs.iteratee.{Enumerator, Iteratee}

/**
 * @author Bryce Anderson
 *         Created on 2/17/13 at 9:38 PM
 */
package object websocket {

  type SocketRoute = ()=>(Iteratee[WebMessage,_], Enumerator[WebMessage])

  sealed trait WebMessage

  case class StringMessage(str: String) extends WebMessage
  case class ByteMessage(bytes: Raw) extends WebMessage

}
