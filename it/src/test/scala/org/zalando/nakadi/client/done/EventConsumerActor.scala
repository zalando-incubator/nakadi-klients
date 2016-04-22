package org.zalando.nakadi.client.done

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.stream.actor.ActorSubscriber
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.stream.actor.RequestStrategy
import akka.util.ByteString

class EventConsumingActor extends Actor with ActorLogging with ActorSubscriber {
  
   var count = 0
  
  override protected def requestStrategy: RequestStrategy = new RequestStrategy {
    override def requestDemand(remainingRequested: Int): Int = {
      Math.max(remainingRequested, 10)
    }
  }

  override def receive: Receive = {
    case OnNext(input: ByteString) =>
      count += 1
      println(s"[Got an event($count) "+ input.utf8String+"]")
    case OnNext(_) =>
      println("Got something")
  }
}
