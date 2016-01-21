package org.zalando.nakadi.client

import java.net.URI
import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorNotFound, ActorSystem}
import akka.util.Timeout
import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.zalando.nakadi.client.actor.{NewListener, ListenerActor, PartitionReceiver}
import play.api.libs.ws.ning.NingWSClient
import play.api.libs.ws._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}


protected class KlientImpl(val endpoint: URI, val tokenProvider: () => String, val objectMapper: ObjectMapper) extends Klient{
  checkNotNull(endpoint, "endpoint must not be null")
  checkNotNull(tokenProvider, "tokenProvider must not be null")
  checkNotNull(objectMapper, "objectMapper must not be null")

  val wsClient = NingWSClient()
  val system = ActorSystem("nakadi-client")

  def checkNotNull(subject: Any, message: String) = if(Option(subject).isEmpty) throw new IllegalArgumentException(message)
  def checkExists(subject: Option[Any], message: String) = if(! subject.isDefined) throw new IllegalArgumentException(message)


  /**
   * Gets monitoring metrics.
   * NOTE: metrics format is not defined / fixed
   *
   * @return immutable map of metrics data (value can be another Map again)
   */
  override def getMetrics: Future[Either[String, Map[String, Any]]] =
                                            performDefaultGetRequest(URI_METRICS, new TypeReference[Map[String, Any]]{})

  private def createDefaultRequest(url:String):  WSRequest =
    wsClient.url(url)
      .withHeaders (("Authorization", "Bearer " + tokenProvider.apply()) ,
      ("Content-Type", "application/json"))

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


  private def performDefaultGetRequest[T](uriPart: String, expectedType: TypeReference[T]): Future[Either[String, T]] =
    createDefaultRequest(endpoint.toString + uriPart)
            .get()
            .map(evaluateResponse(_, expectedType))


  private def evaluateResponse[T](response: WSResponse, expectedType: TypeReference[T]) :Either[String,T] = {
    if(response.status < 200 || response.status > 299)
      Left(response.status + " - " + response.body)
    else
      Right(objectMapper.readValue[T](response.bodyAsBytes, expectedType))
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
  def getTopics(): Future[Either[String, List[Topic]]] = performDefaultGetRequest(URI_TOPICS, new TypeReference[List[Topic]]{})


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


    val actorSelectionPath = s"/user/partition-$partitionId"
    val receiverSelection = system.actorSelection(actorSelectionPath)

    receiverSelection.resolveOne()(Timeout(1L, TimeUnit.SECONDS)).onComplete{_ match {
      case Success(receiverActor) => registerListenerToActor(listener, receiverActor)
      case Failure(e: ActorNotFound) => {
        val receiverActor = system.actorOf(
            PartitionReceiver.props(topic, partitionId, parameters, tokenProvider, autoReconnect, objectMapper), actorSelectionPath)
        registerListenerToActor(listener, receiverActor)
      }
      case Failure(e: Throwable) => throw new KlientException(e.getMessage, e)
    } }
  }


  private def registerListenerToActor(listener: Listener, receiverActor: ActorRef) = {
    val listenerActor = system.actorOf(ListenerActor.props(listener))
    receiverActor ! NewListener(listenerActor)
  }


  /**
   * Non-blocking subscription to a topic requires a `EventListener` implementation. The event listener must be thread-safe because
   * the listener listens to all partitions of a topic (one thread each).
   *
   * @param parameters listen parameters
   * @param listener  listener consuming all received events
   */
  override def subscribeToTopic(topic: String, parameters: ListenParameters, listener: Listener, autoReconnect: Boolean): Unit = {
    getPartitions(topic).map{_ match {
      case Left(errorMessage) =>
          throw new KlientException(s"a problem ocurred while subscribing to [topic=$topic, errorMessage=$errorMessage]")
      case Right(topics: List[TopicPartition]) =>
          topics.foreach(p => listenForEvents(topic, p.partitionId, parameters, listener, autoReconnect))
    } }
  }


   /**
   * Post a single event to the given topic.  Partition selection is done using the defined partition resolution.
   * The partition resolution strategy is defined per topic and is managed by event store (currently resolved from
   * hash over Event.orderingKey).
   * @param topic  target topic
   * @param event  event to be posted
   * @return Option representing the error message or None in case of success
   */
  override def postEvent(topic: String, event: Event): Future[Option[String]] ={
     checkNotNull(topic, "topic must not be null")
     performEventPost(String.format(URI_EVENT_POST, topic), event)
  }


  private def performEventPost(uriPart: String, event: Event): Future[Option[String]] = {
    checkNotNull(event, "event must not be null")

    createDefaultRequest(endpoint + uriPart)
      .post( objectMapper.writeValueAsString(event))
      .map(response => if(response.status < 200 || response.status > 299) Some(response.statusText) else None)
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
  override def postEventToPartition(topic: String, partitionId: String, event: Event): Future[Option[String]] = {
    checkNotNull(topic, "topic must not be null")
    performEventPost(String.format(URI_EVENTS_ON_PARTITION, topic, partitionId), event)
  }


  /**
   * Shuts down the communication system of the client
   */
  override def stop(): Unit = system.shutdown()
}
