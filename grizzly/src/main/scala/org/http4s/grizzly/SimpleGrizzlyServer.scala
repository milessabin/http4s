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
  def apply(port: Int = 8080, serverRoot:String = "/*")(route: Route)
           (implicit executionContext: ExecutionContext = concurrent.ExecutionContext.fromExecutorService(java.util.concurrent.Executors.newCachedThreadPool())) =
  new SimpleGrizzlyServer(port = port, serverRoot = serverRoot)(Seq(route))
}

class SimpleGrizzlyServer(port: Int=8080,
                          address: String = "0.0.0.0",
                          serverRoot:String = "/*",
                          serverName:String="simple-grizzly-server",
                          corePoolSize:Int = 10,
                          maxPoolSize:Int = 20)
                         (routes:Seq[Route])
                         (implicit executionContext: ExecutionContext = ExecutionContext.global)
{
  private[grizzly] val httpServer = new HttpServer
  private[grizzly] val networkListener = new NetworkListener(serverName, address, port)
  private[this] val route = routes reduce (_ orElse _)

  private[this] val threadPoolConfig = ThreadPoolConfig
    .defaultConfig()
    .setCorePoolSize(corePoolSize)
    .setMaxPoolSize(maxPoolSize)

  networkListener.getTransport().setWorkerThreadPoolConfig(threadPoolConfig)

  // Add websocket support
  val grizWebSocketApp = new GrizzlyWebSocketApp(serverRoot.substring(0, serverRoot.length-1), route)

  networkListener.registerAddOn(new WebSocketAddOn())
  WebSocketEngine.getEngine().register(grizWebSocketApp)


  httpServer.addListener(networkListener)

  private[this] val http4sGrizzlyServlet = new Http4sGrizzly(route)(executionContext)
  httpServer.getServerConfiguration().addHttpHandler(http4sGrizzlyServlet,serverRoot)



  try {
    httpServer.start()
    Thread.currentThread().join()

  } catch  {
    case e: Throwable => println(e)
  }
}
