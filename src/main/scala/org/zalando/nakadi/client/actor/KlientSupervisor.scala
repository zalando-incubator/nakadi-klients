package org.zalando.nakadi.client.actor

import java.net.{ConnectException, URI}
import java.util.concurrent.TimeUnit

import akka.actor._
import akka.util.Timeout
import com.fasterxml.jackson.databind.ObjectMapper
import org.zalando.nakadi.client.actor.PartitionReceiver.NewListener
import org.zalando.nakadi.client.{Listener, KlientException, ListenParameters}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}


case class NewSubscription(val topic: String,
                           val partitionId: String,
                           val parameters: ListenParameters,
                           val autoReconnect: Boolean,
                           val listener: Listener)

object KlientSupervisor{
  def props(endpoint: URI, port: Int, securedConnection: Boolean, tokenProvider: () => String, objectMapper: ObjectMapper) =
                                           Props(new KlientSupervisor(endpoint, port, securedConnection, tokenProvider, objectMapper))
}

class KlientSupervisor private (val endpoint: URI, val port: Int, val securedConnection: Boolean, val tokenProvider: () => String, val objectMapper: ObjectMapper) extends Actor{

  import akka.actor.SupervisorStrategy._
  import scala.concurrent.duration._
  import scala.language.postfixOps

  override val supervisorStrategy =
    AllForOneStrategy(maxNrOfRetries = 100, withinTimeRange = 5 minutes) {
      case e: ArithmeticException      => println("xxx ARITHMETIC"); Resume
      case e: NullPointerException     => println("xxx NULLPOINTER"); Restart
      case e: IllegalArgumentException => println("xxx ILLEGAL"); Stop
      case e: Exception                => println("xxx ANY EXC"); e.printStackTrace(); Resume//Escalate
    }


  override def receive: Receive = {
    case NewSubscription(topic, partitionId, parameters, autoReconnect, listener) => {

      val actorSelectionPath = s"/user/partition-$partitionId"
      val receiverSelection = context.actorSelection(actorSelectionPath)

      receiverSelection.resolveOne()(Timeout(1L, TimeUnit.SECONDS)).onComplete{_ match {
        case Success(receiverActor) => registerListenerToActor(listener, receiverActor)
        case Failure(e: ActorNotFound) =>
          val receiverActor = context.actorOf(
            PartitionReceiver.props(endpoint,
              port,
              securedConnection,
              topic,
              partitionId,
              parameters,
              tokenProvider,
              autoReconnect,
              objectMapper),
            s"partition-$partitionId")
          registerListenerToActor(listener, receiverActor)
        case Failure(e: Throwable) => throw new KlientException(e.getMessage, e)
      } }
    }
  }

  private def registerListenerToActor(listener: Listener, receiverActor: ActorRef) = {
    val listenerActor = context.actorOf(ListenerActor.props(listener))
    receiverActor ! NewListener(listenerActor)
  }
}
