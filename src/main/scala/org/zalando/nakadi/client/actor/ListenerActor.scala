package org.zalando.nakadi.client.actor

import akka.actor.{PoisonPill, Props, ActorLogging, Actor}
import org.zalando.nakadi.client.{Cursor, Event, Listener}
import org.zalando.nakadi.client.actor.PartitionReceiver._

object ListenerActor{
  def props(topic: String, listener: Listener) = Props(new ListenerActor(topic, listener))
}

class ListenerActor private (val topic: String, val listener: Listener) extends Actor with ActorLogging{

  context.system.eventStream.subscribe(self, classOf[Unsubscription])


  override def receive: Receive = {
    case (topic: String, partition: String, cursor: Cursor, event: Event) => {
      log.debug(s"received [topic=$topic, partition=$partition, cursor=$cursor, event=$event]")
      listener.onReceive(topic, partition, cursor, event)
    }
    case ConnectionOpened(topic, partition) => listener.onConnectionOpened(topic, partition)
    case ConnectionClosed(topic, partition, lastCursor) => listener.onConnectionClosed(topic, partition, lastCursor)
    case ConnectionFailed(topic, partition, status, error) => listener.onConnectionFailed(topic, partition, status, error)
    case Unsubscription(topic, listener) => if(topic == this.topic && listener.id == this.listener.id) self ! PoisonPill.getInstance
  }

}
