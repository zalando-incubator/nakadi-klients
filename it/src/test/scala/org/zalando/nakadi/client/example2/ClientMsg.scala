package org.zalando.nakadi.client.example2

import org.zalando.nakadi.client.Connection
import java.net.InetSocketAddress
import scala.concurrent.Future
import akka.stream.scaladsl.Tcp
import akka.actor.ActorSystem
import akka.http.scaladsl.Http.ServerBinding

case class SubscribeEvent(eventType: String)

class EventReceiver(conn: Connection)(implicit val system: ActorSystem) {

  def createSource() = {
    val serverConnection = Tcp().outgoingConnection(conn.host, conn.port)

  }

}

class EventServer(conn: Connection)(implicit val system: ActorSystem) {
//
//  def listen() = {
//    val binding: Future[ServerBinding] =
//      Tcp().bind("127.0.0.1", 8888).to(Sink.ignore).run()
//
//    binding.map { b =>
//      b.unbind() onComplete {
//        case _ => // ...
//      }
//    }
//  }
}