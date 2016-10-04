package org.zalando.nakadi.client.actor

import org.zalando.nakadi.client.handler.SubscriptionHandler
import org.zalando.nakadi.client.scala.Connection
import org.zalando.nakadi.client.scala.EventHandler
import org.zalando.nakadi.client.scala.model.Cursor

import SupervisingActor.SubscriptionEntry
import SupervisingActor.SubscriptionKey
import akka.actor.Actor
import akka.actor.ActorInitializationException
import akka.actor.ActorKilledException
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.OneForOneStrategy
import akka.actor.PoisonPill
import akka.actor.Props
import akka.actor.SupervisorStrategy
import akka.actor.SupervisorStrategy.Decider
import akka.actor.SupervisorStrategy.Restart
import akka.actor.SupervisorStrategy._
import akka.actor.Terminated
import akka.actor.actorRef2Scala
import akka.stream.actor.ActorSubscriber
import akka.util.ByteString

object SupervisingActor {
  case class SubscribeMsg(eventTypeName: String, endpoint: String, cursor: Option[Cursor], handler: EventHandler) {
    override def toString(): String =
      s"SubscriptionKey(eventTypeName: $eventTypeName - endpoint: $endpoint - cursor: $cursor - listener: ${handler.id})"
  }
  case class UnsubscribeMsg(eventTypeName: String, partition: Option[String], eventHandlerId: String)
  case class OffsetMsg(cursor: Cursor, subKey: SubscriptionKey)
  case class SubscriptionKey(eventTypeName: String, partition: Option[String]) {
    override def toString(): String = partition match {
      case Some(p) =>
        s"SubscriptionKey(eventTypeName:$eventTypeName - Partition:$p)";
      case None =>
        s"SubscriptionKey(eventTypeName:$eventTypeName)";
    }
  }
  case class SubscriptionEntry(subuscription: SubscribeMsg, actor: ActorRef)
}

class SupervisingActor(val connection: Connection, val subscriptionHandler: SubscriptionHandler)
    extends Actor
    with ActorLogging {
  import SupervisingActor._
  val subscriptions: SubscriptionHolder = new SubscriptionHolderImpl()
  var subscriptionCounter = 0;
  override val supervisorStrategy: SupervisorStrategy = {
    def defaultDecider: Decider = {
      case _: ActorInitializationException ⇒ Stop
      case _: ActorKilledException         ⇒ Stop
      case _: IllegalStateException        ⇒ Stop
      case _: Exception                    ⇒ Stop
      case _: Throwable                    ⇒ Stop
    }
    OneForOneStrategy()(defaultDecider)
  }

  def receive: Receive = {
    case OffsetMsg(cursor, subKey) =>
      log.debug("Received cursor [{}] - subKey [{}]", cursor, subKey)
      subscriptions.addCursor(subKey, Some(cursor))
    case subscription: SubscribeMsg =>
      val before = subscriptions.size
      subscribe(subscription)
      val after = subscriptions.size
      log.debug(s"SubscribeMsg - nr of subscriptions before [$before] - after [$after]")
    case unsubscription: UnsubscribeMsg =>
      val before = subscriptions.size
      unsubscribe(unsubscription)
      val after = subscriptions.size
      log.debug(s"UnsubscribeMsg - nr of subscriptions before [$before] - after [$after]")
    case Terminated(terminatedActor) =>
      log.info(s"Actor [{}] terminated", terminatedActor.path.name)
      subscriptions.entryByActor(terminatedActor) match {
        case Some(
            SubscriptionEntry(SubscribeMsg(eventTypeName, endpoint, Some(Cursor(partition, offset)), handler),
                              actor: ActorRef)) =>
          val cursor         = subscriptions.cursorByActor(terminatedActor)
          val unsubscription = UnsubscribeMsg(eventTypeName, Option(partition), handler.id())
          unsubscribe(unsubscription)
          val newSubscription = SubscribeMsg(eventTypeName, endpoint, cursor, handler)
          subscribe(newSubscription)
        case Some(SubscriptionEntry(SubscribeMsg(eventTypeName, endpoint, None, handler), actor: ActorRef)) =>
          val cursor         = subscriptions.cursorByActor(terminatedActor)
          val unsubscription = UnsubscribeMsg(eventTypeName, None, handler.id())
          unsubscribe(unsubscription)
          val newSubscription = SubscribeMsg(eventTypeName, endpoint, cursor, handler)
          subscribe(newSubscription)
        case None =>
          log.warning("Did not find any SubscriptionKey for [{}] -> might be it was unsubscribed before",
                      terminatedActor.path.name)
        case e =>
          log.error("Received unexpected message! [{}]", e)
      }
  }




  def subscribe(subscribe: SubscribeMsg) = {
    subscriptionCounter += 1
    val SubscribeMsg(eventTypeName, endpoint, optCursor, eventHandler) = subscribe
    log.debug("Subscription nr [{}] - cursor [{}] - eventType [{}] - listener [{}]",
             subscriptionCounter,
             optCursor,
             eventTypeName,
             eventHandler.id())

    val subscriptionKey: SubscriptionKey = optCursor match {
      case Some(Cursor(partition, _)) =>
        SubscriptionKey(eventTypeName, Some(partition))
      case None => SubscriptionKey(eventTypeName, None)
    }

    //Create the Consumer
    val consumingActor = context
      .actorOf(Props(classOf[ConsumingActor], subscriptionKey, eventHandler), "ConsumingActor-" + subscriptionCounter)

    context.watch(consumingActor)

    val subEntry: SubscriptionEntry = SubscriptionEntry(subscribe, consumingActor)

    // Notify listener it is subscribed
    eventHandler.handleOnSubscribed(endpoint, optCursor)

    //Create the pipeline
    subscriptionHandler.createPipeline(optCursor, consumingActor, endpoint, eventHandler)
    subscriptions.add(subscriptionKey, consumingActor, subEntry)
    subscriptions.addCursor(subscriptionKey, optCursor)
  }


  def unsubscribe(unsubscription: UnsubscribeMsg): Unit = {
    val UnsubscribeMsg(eventTypeName, partition, eventHandlerId) = unsubscription
    val key: SubscriptionKey                                     = SubscriptionKey(eventTypeName, partition)
    subscriptions.entry(key) match {
      case Some(SubscriptionEntry(SubscribeMsg(eventTypeName, endpoint, cursor, handler), actor: ActorRef)) =>
        log.info("Unsubscribing Listener : [{}] from actor: [{}]", handler.id(), actor.path.name)
        subscriptions.remove(key)
        actor ! PoisonPill
      case None =>
        log.warning("Listener not found for {}", unsubscription)
    }
  }






}
