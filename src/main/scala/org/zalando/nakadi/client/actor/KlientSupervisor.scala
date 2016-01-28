package org.zalando.nakadi.client.actor

import java.net.URI
import java.util.concurrent.TimeUnit

import akka.actor._
import akka.util.Timeout
import com.fasterxml.jackson.databind.ObjectMapper
import org.zalando.nakadi.client.KlientImpl.KlientException
import org.zalando.nakadi.client.actor.PartitionReceiver._
import org.zalando.nakadi.client.{Listener, ListenParameters}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}





object KlientSupervisor{

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


  var listenerMap: Map[String, ActorRef] = Map()

  override val supervisorStrategy = AllForOneStrategy(maxNrOfRetries = 100, withinTimeRange = 5 minutes) {
      case e: ArithmeticException      => Resume
      case e: NullPointerException     => Restart
      case e: IllegalArgumentException => Stop
      case e: Exception                => Resume
  }


  override def receive: Receive = {
    case NewSubscription(topic, partitionId, parameters, autoReconnect, listener) =>
        if(autoReconnect) {
          resolveActor("partition-" + partitionId).onComplete(_ match {
            case Success(receiverActor) =>
              receiverActor ! NewListener(listener.id, context.actorOf(ListenerActor.props(listener)))
            case Failure(e: ActorNotFound) =>
              subscribeToPartition(topic, partitionId, parameters, autoReconnect, listener, Some(s"partition-$partitionId"))
            case Failure(e: Throwable) =>
              // Note: It's a bit weird we're throwing a 'KlientException' here, the same as 'KlientImpl' uses.
              //      Are these in fact two different kinds of exceptions? Would anyone care of their types? AKa280116

              throw new KlientException(e.getMessage, e)
          })
        }
        else subscribeToPartition(topic, partitionId, parameters, autoReconnect, listener, None)

    case Terminated(actor) => listenerMap = listenerMap.filterNot(_._2 == actor)
  }


  def asListenerPath(topic: String, listener: Listener): String = s"$topic-${listener.id}"


  def subscribeToPartition(topic: String, partitionId: String, parameters: ListenParameters, autoReconnect: Boolean, listener: Listener, actorNameOption: Option[String]) = {
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
      case None => val listenerActor = context.actorOf(ListenerActor.props(listener))
                   context.watch(listenerActor)
                   listenerMap += ((listener.id, listenerActor))
                   listenerActor
    }

    receiverActor ! NewListener(listener.id, lActor)
  }


  def resolveActor(actorSelectionPath: String): Future[ActorRef] = {
    val receiverSelection = context.actorSelection(actorSelectionPath)
    receiverSelection.resolveOne()(Timeout(1L, TimeUnit.SECONDS))
  }
}
