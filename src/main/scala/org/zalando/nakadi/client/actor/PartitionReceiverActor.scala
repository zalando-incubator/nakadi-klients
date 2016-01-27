package org.zalando.nakadi.client.actor

import java.io.{ByteArrayOutputStream}
import java.net.URI

import akka.actor.{Props, ActorRef, ActorLogging, Actor}
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
  case class NewListener(listener: ActorRef)
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

  var listeners: List[ActorRef] = List()
  var lastCursor: Option[Cursor] = None

  implicit val materializer = ActorMaterializer()

  override def preStart() = self ! Init
  
  override def receive: Receive = {
    case Init => lastCursor match {
      case None => listen(parameters)
      case Some(cursor) => listen(ListenParameters(Option(cursor.offset),   // Note: Is 'cursor.offset' supposed to be null? If not, 'Some(cursor.offset)' is likely more appropriate? AKa270116
                                                   parameters.batchLimit,
                                                   parameters.batchFlushTimeoutInSeconds,
                                                   parameters.streamLimit))
    }
    case NewListener(listener) => listeners = listeners ++ List(listener)
    case streamEvent: SimpleStreamEvent => streamEvent.events.foreach{event =>
        lastCursor = Some(streamEvent.cursor)
        listeners.foreach(listener => listener ! Tuple4(topic, partitionId, streamEvent.cursor, event))
    }
  }


  // TODO check earlier ListenParameters
  private
  def listen(parameters: ListenParameters) = {
    val request = HttpRequest(uri = requestUri(parameters))
                      .withHeaders(headers.Authorization(OAuth2BearerToken(tokenProvider.apply())),
                                   headers.Accept(MediaRange(`application/json`)))

    log.debug("listening via [request={}]", request)

    Source
      .single(request)
      .via(outgoingHttpConnection(endpoint, port, securedConnection)(context.system))
      .runWith(Sink.foreach(response =>
      response.status match {
        case status if status.isSuccess => {
          listeners.foreach(_ ! ConnectionOpened(topic, partitionId))
          consumeStream(response)
        }
        case status =>
          listeners.foreach(_ ! ConnectionFailed(topic, partitionId, response.status.intValue(), response.entity.toString))

          if (automaticReconnect) {
            log.info("initiating reconnect to [topic={}, partition={}]...", topic, partitionId)
            self ! Init
          }
      })
      )
      .onComplete(_ => {
      log.info("connection closed to [topic={}, partition={}]", topic, partitionId)
      listeners.foreach(_ ! ConnectionClosed(topic, partitionId, lastCursor))
      if (automaticReconnect) {
        log.info(s"[automaticReconnect=$automaticReconnect] -> reconnecting")
        self ! Init
      }
    })
  }


  private
  def requestUri(parameters: ListenParameters) =
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
    var stack: Int = 0
    var hasOpenString: Boolean = false
    val bout = new ByteArrayOutputStream(1024)

    response.entity.dataBytes.runForeach {
      byteString => {
        byteString.foreach(byteItem => {
          bout.write(byteItem.asInstanceOf[Int])

          if (byteItem == '"') hasOpenString = !hasOpenString
          else if (!hasOpenString && byteItem == '{') stack += 1
          else if (!hasOpenString && byteItem == '}') {
            stack -= 1

            if (stack == 0 && bout.size != 0) {
              val streamEvent = objectMapper.readValue(bout.toByteArray, classOf[SimpleStreamEvent])

              if (Option(streamEvent.events).isDefined && !streamEvent.events.isEmpty){
                log.debug("received non-empty [streamEvent={}]", streamEvent)
                self ! streamEvent
              }
              else
                log.debug(s"received empty [streamingEvent={}] --> ignored", streamEvent)

              bout.reset()
            }
          }
        })
      }
    }
  }

  
  override def toString = s"PartitionReceiver(listeners=$listeners, lastCursor=$lastCursor, endpoint=$endpoint, topic=$topic, partitionId=$partitionId, parameters=$parameters, automaticReconnect=$automaticReconnect)"
}
