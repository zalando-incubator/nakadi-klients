package org.zalando.nakadi.client.example

import java.math.BigInteger
import akka.stream.actor.ActorSubscriber
import akka.stream.actor.MaxInFlightRequestStrategy
import akka.stream.actor.ActorPublisherMessage.Request
import akka.stream.actor.ActorSubscriberMessage.OnError
import akka.stream.actor.ActorSubscriberMessage.OnComplete
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.stream.actor.ActorPublisher
import scala.collection.mutable.{ Queue => MQueue }
import akka.stream.actor.ActorPublisherMessage.Cancel
import scala.collection.mutable.{Queue => MQueue}

class EventTransformer extends ActorSubscriber with ActorPublisher[BigInteger] { // 1
  val dos = BigInteger.valueOf(2L)
  val doubledQueue = MQueue[BigInteger]() // 2

  def receive = {
    case OnNext(biggie: BigInteger) => // 3
      doubledQueue.enqueue(biggie.multiply(dos))
      sendDoubled()
    case OnError(err: Exception) => // 4
      onError(err)
      context.stop(self)
    case OnComplete => // 5
      onComplete()
      context.stop(self)
    case Request(cnt) => // 6
      sendDoubled()
    case Cancel => // 7
      cancel()
      context.stop(self)
    case _ =>
  }

  def sendDoubled() {
    while (isActive && totalDemand > 0 && !doubledQueue.isEmpty) { // 8
      onNext(doubledQueue.dequeue())
    }
  }
 

  val requestStrategy = new MaxInFlightRequestStrategy(50) { // 9
    override def inFlightInternally = doubledQueue.size
  }
}
 


