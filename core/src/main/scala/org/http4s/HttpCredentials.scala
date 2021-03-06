package org.http4s

import HttpCharsets._
import org.parboiled.common.Base64

sealed abstract class HttpCredentials {
  def value: String
  override def toString = value
}

case class BasicHttpCredentials(username: String, password: String) extends HttpCredentials {
  lazy val value = {
    val userPass = username + ':' + password
    val bytes = userPass.getBytes(`ISO-8859-1`.nioCharset)
    val cookie = Base64.rfc2045.encodeToString(bytes, false)
    "Basic " + cookie
  }
}

object BasicHttpCredentials {
  def apply(credentials: String): BasicHttpCredentials = {
    val bytes = Base64.rfc2045.decodeFast(credentials)
    val userPass = new String(bytes, `ISO-8859-1`.nioCharset)
    userPass.indexOf(':') match {
      case -1 => apply(userPass, "")
      case ix => apply(userPass.substring(0, ix), userPass.substring(ix + 1))
    }
  }
}


case class OAuth2BearerToken(token: String) extends HttpCredentials {
  def value = "Bearer " + token
}


case class GenericHttpCredentials(scheme: String, params: Map[String, String]) extends HttpCredentials {
  lazy val value = if (params.isEmpty) scheme else formatParams

  private def formatParams = {
    val sb = new java.lang.StringBuilder(scheme).append(' ')
    var first = true
    params.foreach {
      case (k, v) =>
        if (first) first = false else sb.append(',')
        if (k.isEmpty) sb.append('"') else sb.append(k).append('=').append('"')
        v.foreach {
          case '"' => sb.append('\\').append('"')
          case '\\' => sb.append('\\').append('\\')
          case c => sb.append(c)
        }
        sb.append('"')
    }
    sb.toString
  }
}

