package org.zalando.nakadi.client.actor

import akka.actor.{PoisonPill, Props, ActorLogging, Actor}
import org.zalando.nakadi.client.actor.KlientSupervisor._
import org.zalando.nakadi.client.actor.PartitionReceiver._
import org.zalando.nakadi.client.{ Cursor, Event, Listener}


object ListenerActor{
  case class ListenerSubscription(topic: String, partitionId: String)

  def props(listener: Listener) = Props(new ListenerActor(listener))
}

class ListenerActor private (val listener: Listener) extends Actor with ActorLogging{
  import ListenerActor._

  context.system.eventStream.subscribe(self, classOf[Unsubscription])

  var topicSubscriptions: Set[String] = Set()

  override def receive: Receive = {
    case (topic: String, partition: String, cursor: Cursor, event: Event) =>
      log.debug(s"received [topic=$topic, partition=$partition, cursor=$cursor, event=$event]")
      listener.onReceive(topic, partition, cursor, event)
    case ListenerSubscription(topic: String, partition: String) =>
      topicSubscriptions += topic
      log.info("[listener={}] subscribed to [topic={}, partition={}]", listener.id, topic, partition)
    case ConnectionOpened(topic, partition) => listener.onConnectionOpened(topic, partition)
    case ConnectionClosed(topic, partition, lastCursor) => listener.onConnectionClosed(topic, partition, lastCursor)
    case ConnectionFailed(topic, partition, status, error) => listener.onConnectionFailed(topic, partition, status, error)
    case Unsubscription(topic, _listener) => if(topicSubscriptions.contains(topic) && _listener.id == listener.id) {
      log.info("[listener={}] is unsubscribing [topic={}]", _listener.id, topic)
      topicSubscriptions -= topic
      if(topicSubscriptions.isEmpty) {
        log.info("[listener={}] is not subscribed to any topic -> shutting down", _listener.id)
        self ! PoisonPill.getInstance
      }
    }
  }

}
