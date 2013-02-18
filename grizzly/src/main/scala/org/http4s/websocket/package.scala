package org.http4s

/**
 * @author Bryce Anderson
 *         Created on 2/17/13 at 9:38 PM
 */
package object websocket {

  sealed trait WebMessage

  case class StringMessage(str: String) extends WebMessage
  case class ByteMessage(bytes: Raw) extends WebMessage

}
