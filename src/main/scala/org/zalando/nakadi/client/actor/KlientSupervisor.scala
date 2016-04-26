package org.zalando.nakadi.client.actor

import java.net.URI
import java.util.concurrent.TimeUnit

import akka.actor._
import akka.util.Timeout
import com.fasterxml.jackson.databind.ObjectMapper
import org.zalando.nakadi.client.Klient.KlientException
import org.zalando.nakadi.client.actor.PartitionReceiver._
import org.zalando.nakadi.client.{Conf, Klient, Listener, ListenParameters}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

object KlientSupervisor{
  private val MAX_NR_OF_RETRIES = Conf.supervisor.maxNrOfRetries    // 100
  private val WITHIN_TIME_RANGE = Conf.supervisor.withinTimeRange   // 5 minutes

  private val RESOLVE_ACTOR_TIMEOUT = Conf.supervisor.resolveActorTimeout   // Timeout(1L, TimeUnit.SECONDS)

  case class NewSubscription(topic: String,
                             partitionId: String,
                             parameters: ListenParameters,
                             autoReconnect: Boolean,
                             listener: Listener)


  case class Unsubscription(topic: String, listener: Listener)

  def props(endpoint: URI, port: Int, securedConnection: Boolean, tokenProvider: () => String, objectMapper: ObjectMapper) =
                                           Props(new KlientSupervisor(endpoint, port, securedConnection, tokenProvider, objectMapper))
}


class KlientSupervisor private (val endpoint: URI, val port: Int, val securedConnection: Boolean, val tokenProvider: () => String, val objectMapper: ObjectMapper)
  extends Actor with ActorLogging{

  import akka.actor.SupervisorStrategy._
  import scala.concurrent.duration._
  import scala.language.postfixOps
  import org.zalando.nakadi.client.actor.KlientSupervisor._
  import KlientSupervisor._

  var listenerMap: Map[String, ActorRef] = Map()

  override val supervisorStrategy = AllForOneStrategy(maxNrOfRetries = MAX_NR_OF_RETRIES, withinTimeRange = WITHIN_TIME_RANGE) {
      case e: ArithmeticException      => Resume
      case e: NullPointerException     => Restart
      case e: IllegalArgumentException => Stop
      case e: Exception                => Resume
  }


  override def receive: Receive = {
    case NewSubscription(topic, partitionId, parameters, autoReconnect, listener) =>
      subscribe(topic,
                partitionId,
                parameters,
                autoReconnect,
                listener,
                (l: Listener)=> context.actorOf(ListenerActor.props(l)))

    case Terminated(actor) =>
      listenerMap = listenerMap.filterNot(_._2 == actor)
  }

  private def topicPartitionActorName(topic: String, partitionId:String) = s"$topic-$partitionId"

  def subscribe(topic: String, partitionId: String, parameters: ListenParameters, autoReconnect: Boolean, listener: Listener, createListenerActor: (Listener) => ActorRef): Unit = {
    if(autoReconnect) {
      resolveActor(topicPartitionActorName(topic, partitionId)).onComplete {
        case Success(receiverActor) =>
          receiverActor ! NewListener(listener.id, createListenerActor(listener))
        case Failure(e: ActorNotFound) =>
          subscribeToPartition(topic, partitionId, parameters, autoReconnect, listener, Some(topicPartitionActorName(topic, partitionId)), createListenerActor)
        case Failure(e: Throwable) => throw new KlientException(e.getMessage, e)
      }
    }
    else subscribeToPartition(topic, partitionId, parameters, autoReconnect, listener, None, createListenerActor)
  }


  def asListenerPath(topic: String, listener: Listener): String = s"$topic-${listener.id}"


  def subscribeToPartition(topic: String, partitionId: String, parameters: ListenParameters, autoReconnect: Boolean, listener: Listener,  actorNameOption: Option[String], createListenerActor: (Listener) => ActorRef) = {
    val receiverActor = actorNameOption match {
      case Some(actorName) => context.actorOf(PartitionReceiver.props(endpoint,
                                                                      port,
                                                                      securedConnection,
                                                                      topic,
                                                                      partitionId,
                                                                      parameters,
                                                                      tokenProvider,
                                                                      autoReconnect,
                                                                      objectMapper),
                                                                      actorName)
      case None => context.actorOf(PartitionReceiver.props( endpoint,
                                                            port,
                                                            securedConnection,
                                                            topic,
                                                            partitionId,
                                                            parameters,
                                                            tokenProvider,
                                                            autoReconnect,
                                                            objectMapper))
    }

    val lActor = listenerMap.get(listener.id) match {
      case Some(listenerActor) => listenerActor
      case None => val listenerActor = createListenerActor(listener)
                   context.watch(listenerActor)
                   listenerMap += ((listener.id, listenerActor))
                   listenerActor
    }

    receiverActor ! NewListener(listener.id, lActor)
  }


  def resolveActor(actorSelectionPath: String): Future[ActorRef] = {
    val receiverSelection = context.actorSelection(actorSelectionPath)
    receiverSelection.resolveOne()( RESOLVE_ACTOR_TIMEOUT )
  }
}
