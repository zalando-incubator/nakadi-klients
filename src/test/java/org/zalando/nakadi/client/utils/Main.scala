package org.zalando.nakadi.client.utils

import java.net.URI

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.zalando.nakadi.client._


object Main {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()


  def main (args: Array[String]) {

    val klient = KlientBuilder()
      .withEndpoint(new URI("eventstore-laas.laas.zalan.do"))
      .withPort(443)
      .withSecuredConnection()
      .withTokenProvider(() => "<my token>").build()

    val listener = new Listener {

      override def id = "test"

      override def onReceive(topic: String, partition: String, cursor: Cursor, event: Event): Unit = println(s">>>>> [event=$event, partition=$partition]")

      override def onConnectionClosed(topic: String, partition: String, lastCursor: Option[Cursor]): Unit = println(s"connection closed [partition=$partition]")

      override def onConnectionOpened(topic: String, partition: String): Unit = println(s"connection opened [partition=$partition]")

      override def onConnectionFailed(topic: String, partition: String, status: Int, error: String): Unit = println(s"connection failed [topic=$topic, partition=$partition, status=$status, error=$error]")
    }

    klient.subscribeToTopic("items", ListenParameters(Some("0")), listener, true)

    Thread.sleep(Long.MaxValue)


  }
}