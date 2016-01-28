package org.zalando.nakadi.client.actor

import java.io.ByteArrayOutputStream
import java.net.URI

import akka.actor._
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{MediaRange, headers, HttpResponse, HttpRequest}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.fasterxml.jackson.databind.ObjectMapper
import org.zalando.nakadi.client
import org.zalando.nakadi.client.Utils.outgoingHttpConnection
import org.zalando.nakadi.client.{Cursor, SimpleStreamEvent, ListenParameters}
import scala.concurrent.ExecutionContext.Implicits.global

object PartitionReceiver{
  def props(endpoint: URI,
            port: Int,
            securedConnection: Boolean,
            topic: String,
            partitionId: String,
            parameters: ListenParameters,
            tokenProvider: () => String,
            automaticReconnect: Boolean,
            objectMapper: ObjectMapper) =
    Props(new PartitionReceiver(endpoint, port, securedConnection, topic, partitionId, parameters, tokenProvider, automaticReconnect, objectMapper) )

  // Actor messages
  //
  private object Init
  case class NewListener(listenerId: String, listener: ActorRef)
  case class ConnectionOpened(topic: String, partition: String)
  case class ConnectionFailed(topic: String, partition: String, status: Int, error: String)
  case class ConnectionClosed(topic: String, partition: String, lastCursor: Option[Cursor])
}

class PartitionReceiver private (val endpoint: URI,
                         val port: Int,
                         val securedConnection: Boolean,
                         val topic: String,
                         val partitionId: String,
                         val parameters: ListenParameters,
                         val tokenProvider: () => String,
                         val automaticReconnect: Boolean,
                         val objectMapper: ObjectMapper)  extends Actor with ActorLogging
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
      case Some(cursor) => listen(ListenParameters(Option(cursor.offset),   // Note: Is 'cursor.offset' supposed to be null? If not, 'Some(cursor.offset)' is likely more appropriate? AKa270116
                                                   parameters.batchLimit,
                                                   parameters.batchFlushTimeoutInSeconds,
                                                   parameters.streamLimit))
    }
    case NewListener(listenerId, listener) => listeners = listeners + ((listenerId, listener))
    case streamEvent: SimpleStreamEvent => streamEvent.events.foreach{event =>
      lastCursor = Some(streamEvent.cursor)
      listeners.values.foreach(listener => listener ! Tuple4(topic, partitionId, streamEvent.cursor, event))
    }
    case Unsubscription(_topic, _listener) => if(_topic == topic) listeners -= _listener.id
  }


  // TODO check earlier ListenParameters
  private
  def listen(parameters: ListenParameters) = {
    val request = HttpRequest(uri = buildRequestUri(parameters))
                      .withHeaders(headers.Authorization(OAuth2BearerToken(tokenProvider.apply())),
                                   headers.Accept(MediaRange(`application/json`)))

    if(listeners.isEmpty) {
      log.info("no listeners registered -> not establishing connection to Nakadi")
      reconnectIfActivated(30) // TODO make configurable
    }
    else {
      log.debug("listening via [request={}]", request)
      Source
        .single(request)
        .via(outgoingHttpConnection(endpoint, port, securedConnection)(context.system))
        .runWith(Sink.foreach(response =>
        response.status match {
          case status if status.isSuccess() =>
            listeners.values.foreach(_ ! ConnectionOpened(topic, partitionId))
            consumeStream(response)
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
            self ! PoisonPill.getInstance
          }
      })
    }
  }

  def reconnectIfActivated(numerOfSeconds: Int = 1) = {
    if (automaticReconnect) {
      log.info("[automaticReconnect={}] -> reconnecting", automaticReconnect)
      import scala.concurrent.duration._
      import scala.language.postfixOps
      // TODO make configurable
      context.system.scheduler.scheduleOnce(numerOfSeconds seconds, self, Init)
    }
    else log.info("[automaticReconnect={}] -> no reconnect", automaticReconnect)
  }

  private
  def buildRequestUri(parameters: ListenParameters) =
    String.format(client.URI_EVENT_LISTENING,
      topic,
      partitionId,
      parameters.startOffset.getOrElse(throw new IllegalStateException("no startOffset set")),
      parameters.batchLimit.getOrElse(throw new IllegalStateException("no batchLimit set")).toString,
      parameters.batchFlushTimeoutInSeconds.getOrElse(throw new IllegalStateException("no batchFlushTimeoutInSeconds set")).toString,
      parameters.streamLimit.getOrElse(throw new IllegalStateException("no streamLimit set")).toString)


  private
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
              val streamEvent = objectMapper.readValue(bout.toByteArray, classOf[SimpleStreamEvent])

              // Q: Can 'streamEvent.events' be null? If not, the below doesn't make sense? AKa270116

              if (Option(streamEvent.events).isDefined && streamEvent.events.nonEmpty){
                log.debug("received non-empty [streamEvent={}]", streamEvent)
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
