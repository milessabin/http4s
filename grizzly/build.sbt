import Dependencies._

name := "http4s-grizzly"

description := "Glassfish Grizzly backend for http4s"

libraryDependencies ++= Seq(
  GrizzlyHttpServer,
  "org.glassfish.grizzly" % "grizzly-websockets" % "2.2.19"
)