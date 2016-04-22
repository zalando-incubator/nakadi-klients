package org.zalando.nakadi.client.example2

import scala.concurrent.ExecutionContext.Implicits.global
import akka.stream.scaladsl._
import akka.stream.scaladsl.Keep._
import scala.concurrent.Future
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http
import org.zalando.nakadi.client.Connection
import java.net.InetSocketAddress
import scala.concurrent.Future
import akka.stream.scaladsl.Tcp
import akka.actor.ActorSystem
import akka.stream.scaladsl.Tcp.ServerBinding
import akka.stream.scaladsl.Tcp.IncomingConnection
import akka.util.ByteString

object Server extends App {
  private implicit val actorSystem = ActorSystem("Nakadi-Client-Connections")
  private implicit val http = Http(actorSystem)
  implicit val materializer = ActorMaterializer()
  val server = new Server()
  val client = new Client()
  server.start()

}

class Server(implicit actorSystem: ActorSystem, m: ActorMaterializer) {

  def start() = {
    val connections: Source[IncomingConnection, Future[ServerBinding]] = Tcp().bind("127.0.0.1", 8888)

    connections runForeach { connection =>
      println(s"New connection from: ${connection.remoteAddress}")

      val echo = Flow[ByteString]
        .via(akka.stream.scaladsl.Framing.delimiter(
          ByteString("\n"),
          maximumFrameLength = 256,
          allowTruncation = true))
        .map(_.utf8String)
        .map(_ + "!!!\n")
        .map { x =>
          println(s"$x")
          ByteString(x)
        }

      connection.handleWith(echo)
    }
  }

}