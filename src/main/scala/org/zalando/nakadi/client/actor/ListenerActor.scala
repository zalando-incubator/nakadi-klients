package org.zalando.nakadi.client.actor

import akka.actor.{Props, ActorLogging, Actor}
import org.zalando.nakadi.client.{Cursor, Event, Listener}
import org.zalando.nakadi.client.actor.PartitionReceiver._

object ListenerActor{
  def props(listener: Listener) = Props(new ListenerActor(listener))
}

class ListenerActor private (val listener: Listener) extends Actor with ActorLogging{
  override def receive: Receive = {
    case (topic: String, partition: String, cursor: Cursor, event: Event) => {
      log.debug(s"received [topic=$topic, partition=$partition, cursor=$cursor, event=$event]")
      listener.onReceive(topic, partition, cursor, event)
    }
    case ConnectionOpened(topic, partition) => listener.onConnectionOpened(topic, partition)
    case ConnectionClosed(topic, partition, lastCursor) => listener.onConnectionClosed(topic, partition, lastCursor)
    case ConnectionFailed(topic, partition, status, error) => listener.onConnectionFailed(topic, partition, status, error)
  }

}
