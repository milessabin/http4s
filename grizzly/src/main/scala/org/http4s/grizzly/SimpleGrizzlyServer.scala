package org.http4s
package grizzly

import org.glassfish.grizzly.http.server.{NetworkListener, HttpServer}
import org.glassfish.grizzly.threadpool.ThreadPoolConfig
import concurrent.ExecutionContext
import org.glassfish.grizzly.websockets.{WebSocketEngine, WebSocketAddOn}

/**
 * @author Bryce Anderson
 * Created on 2/10/13 at 3:06 PM
 */

object SimpleGrizzlyServer {
  def apply(port: Int = 8080, serverRoot:String = "/*")(route: Route, webSocketApps: GrizzlyWebSocketApp*)(implicit executionContext: ExecutionContext = concurrent.ExecutionContext.fromExecutorService(java.util.concurrent.Executors.newCachedThreadPool())) =
  new SimpleGrizzlyServer(port = port, serverRoot = serverRoot)(Seq(route), webSocketApps:_*)
}

class SimpleGrizzlyServer(port: Int=8080,
                          address: String = "0.0.0.0",
                          serverRoot:String = "/*",
                          serverName:String="simple-grizzly-server",
                          corePoolSize:Int = 10,
                          maxPoolSize:Int = 20)
                         (routes:Seq[Route],
                          webSocketApps: GrizzlyWebSocketApp*)
                         (implicit executionContext: ExecutionContext = ExecutionContext.global)
{
  private[grizzly] val httpServer = new HttpServer
  private[grizzly] val networkListener = new NetworkListener(serverName, address, port)

  private[this] val threadPoolConfig = ThreadPoolConfig
    .defaultConfig()
    .setCorePoolSize(corePoolSize)
    .setMaxPoolSize(maxPoolSize)

  networkListener.getTransport().setWorkerThreadPoolConfig(threadPoolConfig)

  // Add websocket support, if required.
  if (routes.length > 0) {
    networkListener.registerAddOn(new WebSocketAddOn())
     webSocketApps.foreach(WebSocketEngine.getEngine().register)
  }


  httpServer.addListener(networkListener)

  private[this] val http4sGrizzlyServlet = new Http4sGrizzly(routes reduce (_ orElse _))(executionContext)
  httpServer.getServerConfiguration().addHttpHandler(http4sGrizzlyServlet,serverRoot)

  try {
    httpServer.start()
    Thread.currentThread().join()

  } catch  {
    case e: Throwable => println(e)
  }
}
