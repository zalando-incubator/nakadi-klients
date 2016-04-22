package org.zalando.nakadi.client.example

import java.math.BigInteger
import akka.actor.ActorLogging
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{ Cancel, Request }


class EventPublisher extends ActorPublisher[BigInteger] with ActorLogging {

  var prev = BigInteger.ZERO
  var curr = BigInteger.ZERO

  def receive = {
    case Request(cnt) => // 2
      log.debug("[EventPublisher] Received Request ({}) from Subscriber", cnt)
      sendFibs()
    case Cancel => // 3
      log.info("[EventPublisher] Cancel Message Received -- Stopping")
      context.stop(self)
    case _ =>
      log.info("[EventPublisher] Unknown Message")
  }

  def sendFibs() {
    while (isActive && totalDemand > 0) { // 4
      onNext(nextFib())
    }
  }

  def nextFib(): BigInteger = {
    if (curr == BigInteger.ZERO) {
      curr = BigInteger.ONE
    } else {
      val tmp = prev.add(curr)
      prev = curr
      curr = tmp
    }
    curr
  }
 

}