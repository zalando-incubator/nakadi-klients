package org.zalando.nakadi.client.example

import java.math.BigInteger
import akka.actor._
import akka.stream.actor._
import org.zalando.nakadi.client.ClientImpl
import org.zalando.nakadi.client.Connection
import org.zalando.nakadi.client.ClientFactory

object Main extends App {

  val system = ActorSystem("example-stream-system")
  ClientExample.startSimplePubSubExample(system)
  val client = ClientExample.nakadiClient()

}

object ClientExample extends ClientFactory {
  def nakadiClient() = client

  def startSimplePubSubExample(system: ActorSystem) {
    system.log.info("Starting Publisher")
    val publisherActor = system.actorOf(Props[EventPublisher])
    val publisher = ActorPublisher[BigInteger](publisherActor)

    system.log.info("Starting Subscriber")
    val subscriberActor = system.actorOf(Props(new EventSubscriber(500)))
    val subscriber = ActorSubscriber[BigInteger](subscriberActor)

    system.log.info("Subscribing to Publisher")
    publisher.subscribe(subscriber)
  }

}