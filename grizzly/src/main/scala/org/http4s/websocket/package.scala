package org.http4s

/**
 * @author Bryce Anderson
 *         Created on 2/17/13 at 9:38 PM
 */
package object websocket {

  sealed trait WebPacket

  case class StringPacket(str: String) extends WebPacket
  case class BytePacket(bytes: Raw) extends WebPacket

}
