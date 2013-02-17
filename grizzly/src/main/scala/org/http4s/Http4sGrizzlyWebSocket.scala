package org.http4s

import org.glassfish.grizzly.websockets.{DataFrame, WebSocketListener, ProtocolHandler, DefaultWebSocket}
import org.glassfish.grizzly.http.HttpRequestPacket
import org.glassfish.grizzly.GrizzlyFuture

/**
 * @author Bryce Anderson
 * Created on 2/16/13 at 11:19 PM
 */
abstract class Http4sGrizzlyWebSocket(protocolHandler: ProtocolHandler,
                               request: HttpRequestPacket,
                               listeners: WebSocketListener*)
  extends DefaultWebSocket(protocolHandler, request, listeners:_*) {

  /**
   * <p>
   * This callback will be invoked when a text message has been received.
   * </p>
   *
   * @param text the text received from the remote end-point.
   */
  def onMessage(text: String)

  def onMessage(data: Array[Byte])

}
