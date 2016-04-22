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

object Client extends App {
  private implicit val actorSystem = ActorSystem("Nakadi-Client-Connections")
  private implicit val http = Http(actorSystem)
  implicit val materializer = ActorMaterializer()
  val server = new Server()
  val client = new Client()
  client.connect()

}

class Client(implicit system: ActorSystem, m: ActorMaterializer) {
	val connection = Tcp().outgoingConnection("127.0.0.1", 8888)

  def connect() = {
  
  val replParser =
    Flow[String].takeWhile(_ != "q")
      .concat(Source.single("BYE"))
      .map(elem => ByteString(s"$elem\n"))

  val repl = Flow[ByteString]
    .via(Framing.delimiter(
      ByteString("\n"),
      maximumFrameLength = 256,
      allowTruncation = true))
    .map(_.utf8String)
    .map(text => println("Server: " + text))
    .map(_ => scala.io.StdIn.readLine("> "))
    .via(replParser)
    println("Connecting client")
  connection.join(repl).run()
  }
}

