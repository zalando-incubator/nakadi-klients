package org.zalando.nakadi.client

import java.net.URI

import akka.actor._
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.model.HttpMethods.POST
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory
import org.zalando.nakadi.client.Utils.outgoingHttpConnection
import org.zalando.nakadi.client.actor.KlientSupervisor._
import org.zalando.nakadi.client.actor._
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global


protected class KlientImpl(val endpoint: URI,
                           val port: Int,
                           val securedConnection: Boolean,
                           val tokenProvider: () => String,
                           val objectMapper: ObjectMapper,
                           val klientSystem: Option[ActorSystem] = None) extends Klient{
  checkNotNull(endpoint, "endpoint must not be null")
  checkNotNull(tokenProvider, "tokenProvider must not be null")
  checkNotNull(objectMapper, "objectMapper must not be null")

  implicit val system = klientSystem.getOrElse(ActorSystem("nakadi-client"))

  val supervisor = system.actorOf(KlientSupervisor.props(endpoint, port, securedConnection, tokenProvider, objectMapper),
                                  "klient-supervisor")

  implicit val materializer = ActorMaterializer()

  val logger = Logger(LoggerFactory.getLogger("KlientImpl"))


  def checkNotNull(subject: Any, message: String) = if(Option(subject).isEmpty) throw new IllegalArgumentException(message)
  def checkExists(subject: Option[Any], message: String) = if(subject.isEmpty) throw new IllegalArgumentException(message)


  /**
   * Gets monitoring metrics.
   * NOTE: metrics format is not defined / fixed
   *
   * @return immutable map of metrics data (value can be another Map again)
   */
  override def getMetrics: Future[Either[String, Map[String, Any]]] =
                                            performDefaultGetRequest(URI_METRICS, new TypeReference[Map[String, Any]]{})


  /**
   * Get partition information of a given topic
   *
   * @param topic   target topic
   * @return immutable list of topic's partitions information
   */
  override def getPartitions(topic: String): Future[Either[String, List[TopicPartition]]] = {
    checkNotNull(topic, "topic must not be null")
    performDefaultGetRequest(String.format(URI_PARTITIONS,topic), new TypeReference[List[TopicPartition]]{})
  }


  private def performDefaultGetRequest[T](uriPart: String, expectedType: TypeReference[T]): Future[Either[String, T]] = {
    val request = HttpRequest(uri = uriPart)
                  .withHeaders(headers.Authorization(OAuth2BearerToken(tokenProvider.apply())),
                               headers.Accept(MediaRange(`application/json`)))

    logger.debug("sending [request={}]", request)

    Source
      .single(request)
      .via(outgoingHttpConnection(endpoint, port, securedConnection))
      .runWith(Sink.head)
      .map(evaluateResponse(_, expectedType))
  }



  private def evaluateResponse[T](response: HttpResponse, expectedType: TypeReference[T]) :Either[String,T] = {
    logger.debug("received [response={}]", response)

    if(response.status.intValue() < 200 || response.status.intValue() > 299)
      Left(response.status + " - " + response.entity)
    else
    // TODO better way?
      Await.result(
        Unmarshaller.byteArrayUnmarshaller(response.entity).map(bytes => {
          Right(objectMapper.readValue[T](bytes, expectedType))
        }), Duration.Inf)

  }


  /**
   * Get specific partition
   *
   * @param topic  topic where the partition is located
   * @param partitionId  id of the target partition
   * @return Either error message or TopicPartition in case of success
   */
  override def getPartition(topic: String, partitionId: String): Future[Either[String, TopicPartition]] = {
    checkNotNull(topic, "topic must not be null")
    checkNotNull(partitionId, "partitionId must not be null")
    performDefaultGetRequest(String.format(URI_PARTITION, topic, partitionId), new TypeReference[TopicPartition]{})
  }


  /**
   * Lists all known `Topics` in Event Store.
   *
   * @return immutable list of known topics
   */
  def getTopics: Future[Either[String, List[Topic]]] = performDefaultGetRequest(URI_TOPICS, new TypeReference[List[Topic]]{})


  /**
   * Blocking subscription to events of specified topic and partition.
   * (batchLimit is set to 1, batch flush timeout to 1,  and streamLimit to 0 -> infinite streaming receiving 1 event per poll)
   *
   * @param parameters listen parameters
   * @param listener  listener consuming all received events
   * @return Either error message or connection was closed and reconnect is set to false
   */
  override def listenForEvents(topic: String, partitionId: String, parameters: ListenParameters, listener: Listener, autoReconnect: Boolean = false): Unit = {

    checkNotNull(topic, "topic must not be null")
    checkNotNull(partitionId, "partitionId must not be null")
    checkNotNull(parameters, "list parameters must not be null")
    checkNotNull(listener, "listener must not be null")
    checkNotNull(autoReconnect, "autoReconnect must not be null")

    checkExists(parameters.batchFlushTimeoutInSeconds, "batchFlushTimeoutInSeconds is not set")
    checkExists(parameters.batchLimit, "batchLimit is not set")
    checkExists(parameters.startOffset, "startOffset is not specified")
    checkExists(parameters.streamLimit, "streamLimit is not specified")

    supervisor ! NewSubscription(topic, partitionId, parameters, autoReconnect, listener)
  }


  /**
   * Non-blocking subscription to a topic requires a `EventListener` implementation. The event listener must be thread-safe because
   * the listener listens to all partitions of a topic (one thread each).
   *
   * @param parameters listen parameters
   * @param listener  listener consuming all received events
   */
  // TODO earlier parameter check
  override def subscribeToTopic(topic: String, parameters: ListenParameters, listener: Listener, autoReconnect: Boolean): Future[Unit] = {
    getPartitions(topic).map{_ match {
      case Left(errorMessage) =>
          throw new KlientException(s"a problem ocurred while subscribing to [topic=$topic, errorMessage=$errorMessage]")
      case Right(partitions: List[TopicPartition]) =>
          partitions.foreach(p => listenForEvents(topic,
                                                  p.partitionId,
                                                  ListenParameters(
                                                    Option(p.newestAvailableOffset),
                                                    parameters.batchLimit,
                                                    parameters.batchFlushTimeoutInSeconds,
                                                    parameters.streamLimit),
                                                  listener,
                                                  autoReconnect))
    } }
  }


  def unsubscribeTopic(topic: String, listener: Listener): Unit = system.eventStream.publish(Unsubscription(topic, listener))


   /**
   * Post a single event to the given topic.  Partition selection is done using the defined partition resolution.
   * The partition resolution strategy is defined per topic and is managed by event store (currently resolved from
   * hash over Event.orderingKey).
   * @param topic  target topic
   * @param event  event to be posted
   * @return Option representing the error message or None in case of success
   */
  override def postEvent(topic: String, event: Event): Future[Either[String,Unit]] = {
     checkNotNull(topic, "topic must not be null")
     performEventPost(String.format(URI_EVENT_POST, topic), event)
  }


  private def performEventPost(uriPart: String, event: Event): Future[Either[String,Unit]] = {
    checkNotNull(event, "event must not be null")

    val request = HttpRequest(uri = uriPart, method = POST)
                  .withHeaders(headers.Authorization(OAuth2BearerToken(tokenProvider.apply())))
                  .withEntity(ContentType(`application/json`), objectMapper.writeValueAsBytes(event))

    logger.debug("sending [request={}]", request)

    Source
      .single(request)
      .via(outgoingHttpConnection(endpoint, port, securedConnection))
      .runWith(Sink.head)
      .map(response => if(response.status.intValue() < 200 || response.status.intValue() > 299)
            Left(response.entity.dataBytes.toString) else Right(()))
    // TODO check entity handling
  }


  /**
   * Post event to specific partition.
   * NOTE: not implemented by Nakadi yet
   *
   * @param topic  topic where the partition is located
   * @param partitionId  id of the target partition
   * @param event event to be posted
   * @return Option representing the error message or None in case of success
   */
  override def postEventToPartition(topic: String, partitionId: String, event: Event): Future[Either[String,Unit]] = {
    checkNotNull(topic, "topic must not be null")
    performEventPost(String.format(URI_EVENTS_ON_PARTITION, topic, partitionId), event)
  }


  /**
   * Shuts down the communication system of the client
   */
  override def stop(): Future[Terminated] = system.terminate()
}
