package org.zalando.nakadi.client.actor

import scala.util.Failure
import scala.util.Success
import scala.util.Try
import org.zalando.nakadi.client.Deserializer
import org.zalando.nakadi.client.scala.ClientError
import org.zalando.nakadi.client.scala.Listener
import org.zalando.nakadi.client.scala.model.Cursor
import org.zalando.nakadi.client.scala.model.Event
import org.zalando.nakadi.client.scala.model.EventStreamBatch
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.actorRef2Scala
import akka.stream.actor.ActorSubscriber
import akka.stream.actor.ActorSubscriberMessage.OnComplete
import akka.stream.actor.ActorSubscriberMessage.OnError
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.stream.actor._
import akka.util.ByteString
import org.zalando.nakadi.client.utils.ModelConverter
import org.zalando.nakadi.client.scala.EventHandler
import org.zalando.nakadi.client.scala.ErrorResult
import org.zalando.nakadi.client.java.model.{Event => JEvent}
import SupervisingActor._
import akka.actor.Terminated

/**
  * This actor serves as Sink for the pipeline.<br>
  * 1. It receives the message and the cursor from the payload.
  * 2. It tries to deserialize the message to EventStreamBatch, containing a cursor and a sequence of Events.
  * 3. Passes the deserialized sequence of events to the listener.
  * 4. Sends the received cursor from the Publisher, to be passed to the pipeline.
  *
  */
class ConsumingActor(subscription: SubscriptionKey, handler: EventHandler)
    extends Actor
    with ActorLogging
    with ActorSubscriber {
  import ModelConverter._

  val subscriptionAsString = subscription.toString()
  var lastCursor: Option[Cursor] = null

  override protected def requestStrategy: RequestStrategy =
    new RequestStrategy {
      override def requestDemand(remainingRequested: Int): Int = {
        Math.max(remainingRequested, 10)
      }
    }

  override def receive: Receive = {
    case OnNext(msg: ByteString) =>
      val message = msg.utf8String
      log.debug("Event - prevCursor [{}] - [{}] - msg [{}]", lastCursor, subscriptionAsString, message)
      handler.handleOnReceive(subscriptionAsString, message) match {
        case Right(cursor) =>
          lastCursor = Some(cursor)
          context.parent ! OffsetMsg(cursor, subscription)
        case Left(error) =>
          log.error(error.error.getMessage)
          context.stop(self)
      }
    case OnError(err: Throwable) =>
      log.error("onError - cursor [{}] - [{}] - error [{}]", lastCursor, subscription, err.getMessage)
      context.stop(self)
    case OnComplete =>
      log.debug("onComplete - connection closed by server - cursor [{}] - [{}]", lastCursor, subscription)
      context.parent ! UnsubscribeMsg(subscription.eventTypeName, subscription.partition, handler.id())
    case a =>
      log.error("Could not handle unknown msg: [{}] - subscription [{}] with listener-id [{}] ",
                a, subscriptionAsString, handler.id())
      context.stop(self)
  }

}
