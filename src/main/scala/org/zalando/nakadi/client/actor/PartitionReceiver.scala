package org.zalando.nakadi.client.actor

import java.io.ByteArrayOutputStream
import java.net.URI

import akka.actor._
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{MediaRange, headers, HttpResponse, HttpRequest}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import org.zalando.nakadi.client
import org.zalando.nakadi.client.Utils.outgoingHttpConnection
import org.zalando.nakadi.client.actor.KlientSupervisor._
import org.zalando.nakadi.client.actor.ListenerActor._
import org.zalando.nakadi.client.{Cursor, SimpleStreamEvent, ListenParameters}
import spray.json.JsonParser
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

import org.zalando.nakadi.client.MyJsonProtocol._

object PartitionReceiver{

  val NO_LISTENER_RECONNECT_DELAY_IN_S: Int = 10  // TODO make configurable
  val POLL_PARALLELISM: Int = 100 // TODO make configurable

  /**
   * Triggers new event polling request
   */
  private object Init

  /**
   * Shutdown actor without PoisonPill. The reason is that PoisonPill shuts down
   * the actor immediately without having all messages in the queue processed.
   */
  private object Shutdown

  case class NewListener(listenerId: String, listener: ActorRef)
  case class ConnectionOpened(topic: String, partition: String)
  case class ConnectionFailed(topic: String, partition: String, status: Int, error: String)
  case class ConnectionClosed(topic: String, partition: String, lastCursor: Option[Cursor])


  def props(endpoint: URI,
            port: Int,
            securedConnection: Boolean,
            topic: String,
            partitionId: String,
            parameters: ListenParameters,
            tokenProvider: () => String,
            automaticReconnect: Boolean) =
    Props(new PartitionReceiver(endpoint, port, securedConnection, topic, partitionId, parameters, tokenProvider, automaticReconnect) )
}

class PartitionReceiver private (val endpoint: URI,
                                 val port: Int,
                                 val securedConnection: Boolean,
                                 val topic: String,
                                 val partitionId: String,
                                 val parameters: ListenParameters,
                                 val tokenProvider: () => String,
                                 val automaticReconnect: Boolean)  extends Actor with ActorLogging
{
  import PartitionReceiver._

  var listeners: Map[String, ActorRef] = Map()

  var lastCursor: Option[Cursor] = None
  implicit val materializer = ActorMaterializer()

  val RECEIVE_BUFFER_SIZE: Int = 1024

  context.system.eventStream.subscribe(self, classOf[Unsubscription])

  override def preStart() = self ! Init

  override def receive: Receive = {
    case Init => lastCursor match {
      case None => listen(parameters)
      case Some(cursor) => listen(ListenParameters(Option(cursor.offset),
                                                   parameters.batchLimit,
                                                   parameters.batchFlushTimeoutInSeconds,
                                                   parameters.streamLimit))
    }
    case NewListener(listenerId, listener) =>
      context.watch(listener)
      listener ! ListenerSubscription(topic, partitionId)
      listeners = listeners + ((listenerId, listener))
    case streamEvent: SimpleStreamEvent => streamEvent.events.foreach{event =>
      lastCursor = Some(streamEvent.cursor)
      listeners.values.foreach(listener => listener ! Tuple4(topic, partitionId, streamEvent.cursor, event))
    }
    case Unsubscription(_topic, _listener) => if(_topic == topic) listeners -= _listener.id
    case Terminated(actor) => listeners = listeners.filterNot(_._2 == actor)
    case Shutdown => context.stop(self)
  }


  // TODO check earlier ListenParameters
  def listen(parameters: ListenParameters) = {
    val request = HttpRequest(uri = buildRequestUri(parameters))
                      .withHeaders(headers.Authorization(OAuth2BearerToken(tokenProvider.apply())),
                                   headers.Accept(MediaRange(`application/json`)))

    if(listeners.isEmpty) {
      log.info("no listeners registered -> not establishing connection to Nakadi")
      reconnectIfActivated(NO_LISTENER_RECONNECT_DELAY_IN_S)
    }
    else {
      log.debug("listening via [request={}]", request)
      Source
        .single(request)
        .via(outgoingHttpConnection(endpoint, port, securedConnection)(context.system))
        .runWith(Sink.foreachParallel(POLL_PARALLELISM)(response =>
        response.status match {
          case status if status.isSuccess() =>
            listeners.values.foreach(_ ! ConnectionOpened(topic, partitionId))
            Await.ready(consumeStream(response), Duration.Inf)// TODO is there a better way?
          case status =>
            listeners.values.foreach(_ ! ConnectionFailed(topic, partitionId, response.status.intValue(), response.entity.toString))
            reconnectIfActivated()
        }))
        .onComplete(_ => {
          log.info("connection closed to [topic={}, partition={}]", topic, partitionId)
          listeners.values.foreach(_ ! ConnectionClosed(topic, partitionId, lastCursor))

          if(automaticReconnect) reconnectIfActivated()
          else {
            log.info("stream to [topic={}, partition={}] has been closed and [automaticReconnect={}] -> shutting down",
                     topic, partitionId, automaticReconnect)
            self ! Shutdown
          }
      })
    }
  }

  def reconnectIfActivated(numerOfSeconds: Int = 1) = {
    if (automaticReconnect) {
      log.info("[automaticReconnect={}] -> reconnecting", automaticReconnect)
      import scala.concurrent.duration._
      import scala.language.postfixOps
      context.system.scheduler.scheduleOnce(numerOfSeconds seconds, self, Init)
    }
    else log.info("[automaticReconnect={}] -> no reconnect", automaticReconnect)
  }

  def buildRequestUri(parameters: ListenParameters) =
    String.format(client.URI_EVENT_LISTENING,
      topic,
      partitionId,
      parameters.startOffset match {
        case Some(offset) => s"start_from=$offset"
        case None => ""
      },
      parameters.batchLimit.getOrElse(throw new IllegalStateException("no batchLimit set")).toString,
      parameters.batchFlushTimeoutInSeconds.getOrElse(throw new IllegalStateException("no batchFlushTimeoutInSeconds set")).toString,
      parameters.streamLimit.getOrElse(throw new IllegalStateException("no streamLimit set")).toString)


  def consumeStream(response: HttpResponse) = {
    /*
     * We can not simply rely on EOL for the end of each JSON object as
     * Nakadi puts the in the middle of the response body sometimes.
     * For this reason, we need to apply some very simple JSON parsing logic.
     *
     * See also http://json.org/ for string parsing semantics
     */
    var depth: Int = 0
    var hasOpenString: Boolean = false
    val bout = new ByteArrayOutputStream(RECEIVE_BUFFER_SIZE)

    response.entity.dataBytes.runForeach {
      byteString => {
        byteString.foreach(byteItem => {
          bout.write(byteItem.asInstanceOf[Int])

          if (byteItem == '"') hasOpenString = !hasOpenString
          else if (!hasOpenString && byteItem == '{') depth += 1
          else if (!hasOpenString && byteItem == '}') {
            depth -= 1

            if (depth == 0 && bout.size != 0) {
              val streamEvent = JsonParser(bout.toByteArray).convertTo[SimpleStreamEvent]

              if (Option(streamEvent.events).isDefined && streamEvent.events.nonEmpty){
                log.info("received non-empty [streamEvent={}]", streamEvent)
                self ! streamEvent
              }
              else log.debug(s"received empty [streamingEvent={}] --> ignored", streamEvent)

              bout.reset()
            }
          }
        })
      }
    }
  }

  
  override def toString = s"PartitionReceiver(listeners=$listeners, lastCursor=$lastCursor, endpoint=$endpoint, topic=$topic, partitionId=$partitionId, parameters=$parameters, automaticReconnect=$automaticReconnect)"
}
