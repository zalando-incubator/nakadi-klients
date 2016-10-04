package org.zalando.nakadi.client.scala

import java.util.UUID

import scala.Left
import scala.Right
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt
import org.slf4j.LoggerFactory
import org.zalando.nakadi.client.Deserializer
import org.zalando.nakadi.client.Serializer
import org.zalando.nakadi.client.handler.SubscriptionHandler
import org.zalando.nakadi.client.scala.model._
import org.zalando.nakadi.client.utils.Uri
import com.fasterxml.jackson.core.`type`.TypeReference
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration

class ClientImpl(connection: Connection, subscriber: SubscriptionHandler, charSet: String = "UTF-8") extends Client {
  import Uri._
  import ScalaJacksonJsonMarshaller._
  import HttpFactory._
  implicit val materializer = connection.materializer()

  private val logger = LoggerFactory.getLogger(this.getClass)

  def getMetrics(): Future[Either[ClientError, Option[Metrics]]] = {
    logFutureEither(connection.get(URI_METRICS).flatMap(mapToEither(_)(deserializer(metricsTR))))
  }

  def getEventTypes(): Future[Either[ClientError, Option[Seq[EventType]]]] = {
    logFutureEither(connection.get(URI_EVENT_TYPES).flatMap(mapToEither(_)(deserializer(listOfEventTypeTR))))
  }

  def createEventType(eventType: EventType): Future[Option[ClientError]] = {
    logFutureOption(connection.post(URI_EVENT_TYPES, eventType).flatMap(mapToOption(_)))
  }

  def getEventType(name: String): Future[Either[ClientError, Option[EventType]]] = {
    logFutureEither(
      connection.get(URI_EVENT_TYPE_BY_NAME.format(name)).flatMap(in => mapToEither(in)(deserializer(eventTypeTR))))
  }

  def updateEventType(name: String, eventType: EventType): Future[Option[ClientError]] = {
    val result = connection.put(URI_EVENT_TYPE_BY_NAME.format(name), eventType)
    logFutureOption(result.flatMap(in => mapToOption(in)))
  }

  def deleteEventType(name: String): Future[Option[ClientError]] = {
    logFutureOption(connection.delete(URI_EVENT_TYPE_BY_NAME.format(name)).flatMap(in => mapToOption(in)))
  }

  def publishEvents[T <: Event](eventTypeName: String,
                                events: Seq[T],
                                ser: Serializer[Seq[T]]): Future[Option[ClientError]] = {
    logFutureOption(
      connection.post(URI_EVENTS_OF_EVENT_TYPE.format(eventTypeName), events)(ser).flatMap(in => mapToOption(in)))
  }
  def publishEvents[T <: Event](eventTypeName: String, events: Seq[T]): Future[Option[ClientError]] = {
    logFutureOption(
      connection.post(URI_EVENTS_OF_EVENT_TYPE.format(eventTypeName), events).flatMap(in => mapToOption(in)))
  }

  def getPartitions(name: String): Future[Either[ClientError, Option[Seq[Partition]]]] = {
    logFutureEither(
      connection
        .get(URI_PARTITIONS_BY_EVENT_TYPE.format(name))
        .flatMap(in => mapToEither(in)(deserializer(listOfPartitionTR))))
  }


  def getPartitioningStrategies(): Future[Either[ClientError, Option[Seq[PartitionStrategy.Value]]]] ={
     val transformer = getTranformer(PartitionStrategy)
    logFutureEither(
      connection.get(URI_PARTITIONING_STRATEGIES).flatMap(mapToEither(_)(deserializer(listOfStringsTR,transformer))))
  }

  
  private def getTranformer(enum:Enumeration)={
    (in:Seq[String]) =>  {
      in.map { x => enum.withName(x) }
    }
  }
  
  def getEnrichmentStrategies(): Future[Either[ClientError, Option[Seq[EventEnrichmentStrategy.EventEnrichmentStrategy]]]] = {
    val tranformer = getTranformer(EventEnrichmentStrategy)
    logFutureEither(
      connection
        .get(URI_ENRICHMENT_STRATEGIES)
        .flatMap(mapToEither(_)(deserializer(listOfStringsTR, tranformer))))
  }

  

  def stop(): Option[ClientError] = {
    materializer.shutdown()
    val result = Await.ready(connection.actorSystem().terminate(), Duration.Inf)
    None
  }

  def subscribe[T <: Event](eventTypeName: String,
                            parameters: StreamParameters,
                            listener: Listener[T],
                            typeRef: TypeReference[EventStreamBatch[T]]): Option[ClientError] = {
    subscribe(eventTypeName, parameters, listener)(deserializer(typeRef))

  }
  def subscribe[T <: Event](eventTypeName: String, params: StreamParameters, listener: Listener[T])(
    implicit des: Deserializer[EventStreamBatch[T]]): Option[ClientError] =
    (eventTypeName, params, listener) match {

      case (_, _, listener) if listener == null =>
        logger.warn("listener is null")
        Some(ClientError("Listener may not be empty(null)!", None))

      case (eventType, _, _) if Option(eventType).isEmpty || eventType == "" =>
        logger.warn("eventType is null")
        Some(ClientError("Eventype may not be empty(null)!", None))

      case (eventType, StreamParameters(cursor, _, _, _, _, _, _), listener) if Option(eventType).isDefined =>
        val url = URI_EVENTS_OF_EVENT_TYPE.format(eventType)
        logger.debug("Subscribing listener {} - cursor {} - parameters {} - eventType {} - url {}",
          listener.id,
          cursor,
          params,
          eventType,
          url)
        val finalUrl = withUrl(url, params.toQueryParamsMap())
        val eventHandler: EventHandler = new ScalaEventHandlerImpl(des, listener)
        subscriber.subscribe(eventTypeName, finalUrl, cursor, eventHandler)
    }

  def unsubscribe[T <: Event](eventTypeName: String,
                              partition: Option[String],
                              listener: Listener[T]): Option[ClientError] = {
    subscriber.unsubscribe(eventTypeName, partition, listener.id)
  }

  //####################
  //# High Level API
  //####################


  def subscribe[T <: Event](subscriptionId: UUID,
                            streamParameters: SubscriptionStreamParameters,
                            listener: Listener[T])
                          (implicit des: Deserializer[EventStreamBatch[T]]): Option[ClientError] = {

    (subscriptionId, streamParameters, listener) match {
      case (subscriptionId, _, _) if subscriptionId == null =>
        logger.warn("subscriptionId is null")
        Some(ClientError("SubscriptionId may not be empty(null)!", None))

      case (_, _, listener) if listener == null =>
        logger.warn("listener is null")
        Some(ClientError("Listener may not be empty(null)!", None))


      case (subscriptionId, SubscriptionStreamParameters(_,_,_, _, _, _, _, _, _), listener) if Option(subscriptionId).isDefined =>
        val url = Uri.URI_SUBSCRIPTION_TO_EVENT_STREAM(subscriptionId.toString)

        logger.debug("Subscribing [listener={}, subscriptionId={}, parameters={}, url={}]",
          listener.id,
          subscriptionId,
          streamParameters,
          url
        )


      val finalUrl = withUrl(url, streamParameters.toQueryParamsMap())
      val eventHandler: EventHandler = new ScalaEventHandlerImpl(des, listener)

      subscriber.subscribe(subscriptionId.toString, finalUrl, None, eventHandler)
    }

  }

  //####################
  //#  HELPER METHODS  #
  //####################

  private def logFutureEither[A, T](future: Future[Either[ClientError, T]]): Future[Either[ClientError, T]] = {
    future recover {
      case e: Throwable =>
        val msg = s"An unexpected error occured: ${e.getMessage}"
        logger.error(msg)
        Left(ClientError(msg, None))
    }
  }


  private def logFutureOption(future: Future[Option[ClientError]]): Future[Option[ClientError]] = {
    future recover {
      case e: Throwable =>
        val msg = s"An unexpected error occured: ${e.getMessage}"
        Option(ClientError(msg, None))
    }
  }


  private def mapToEither[T](response: HttpResponse)(
    implicit deserializer: Deserializer[T]): Future[Either[ClientError, Option[T]]] = {
    logger.debug("received [response={}]", response)
    response match {
      case HttpResponse(status, headers, entity, protocol) if (status.isSuccess()) =>
        try {
          Unmarshal(entity).to[String].map { body =>
            logger.debug(s"Payload: $body")
            Right(Some(deserializer.from(body)))
          }
        } catch {
          case e: Throwable =>
            val msg = "Failed to deserialise the content with error: %s".format(e.getMessage)
            logger.error(msg)
            Future.successful(Left(ClientError(msg, Some(status.intValue()))))
        }
      case HttpResponse(StatusCodes.NotFound, headers, entity, protocol) =>
        logger.info(s"Received: httpStatus - Not found ${StatusCodes.NotFound}")
        Future.successful(Right(None))
      case HttpResponse(status, headers, entity, protocol) if (status.isRedirection()) =>
        val msg = "Not implemented: http-status (" + status.intValue() + "}) and reason:" + status.reason()
        logger.info(msg)
        Future.successful(Left(ClientError(msg, Some(status.intValue()))))
      case HttpResponse(status, headers, entity, protocol) =>
        Unmarshal(entity).to[String].map { body =>
          val msg = "Service return http-status: %s (%s) Message: %s".format(status.intValue(), status.reason(), body)
          logger.warn(msg)
          Left(ClientError(msg, Some(status.intValue())))
        }
    }
  }


  private[client] def mapToOption[T](response: HttpResponse): Future[Option[ClientError]] = {
    response.status match {
      case status if (status.isSuccess()) =>
        logger.info(s"Success. http-status: ${status.intValue()}")
        response.entity.toStrict(10.second).map { body =>
          logger.debug("Success - http-status: %s, body:[%s]".format(status.intValue().toString(),
            body.data.decodeString(charSet)))
        }
        Future.successful(None)
      case status if (status.isRedirection()) =>
        val msg = s"Redirection - http-status: ${status.intValue()}, reason[${status.reason()}]"
        logger.info(msg)
        response.entity.toStrict(10.second).map { body =>
          logger.debug(s"Redirection - http-status: ${status.intValue().toString()}, reason[${
            status
              .reason()
          }], body:[${body.data.decodeString(charSet)}]")
        }
        Future.successful(Option(ClientError(msg, Some(status.intValue()))))
      case status if (status.isFailure()) =>
        logger.warn(s"Failure. http-status: ${status.intValue()}")
        response.entity.toStrict(10.second).map { body =>
          val msg =
            s"Failure - http-status: ${status.intValue()}, reason[${status.reason()}], body:[${body.data.decodeString(charSet)}]"
          logger.warn(msg)
          Option(ClientError(msg, Some(status.intValue())))
        }
    }
  }

  /**
    * Creates a subscription for EventTypes. The subscription is needed to be able to consume events from EventTypes in
    * a high level way when Nakadi stores the offsets and manages the rebalancing of consuming clients. The subscription
    * is identified by its key parameters (owning_application, event_types, consumer_group). If this endpoint is invoked
    * several times with the same key subscription properties in body (order of even_types is not important) -
    * the subscription will be created only once and for all other calls it will just return the subscription
    * that was already created.
    *
    * @param subscription Subscription is a high level consumption unit. Subscriptions allow applications to easily scale
    *                     the number of clients by managing consumed event offsets and distributing load between
    *                     instances. The key properties that identify subscription are 'owning_application',
    *                     'event_types' and 'consumer_group'. It's not possible to have two different subscriptions with
    *                     these properties being the same.
    * @return either an error which was reported from the Nakadi endpoint in order to initialize a subscription OR
    *         the initial subscription data enriched with data about the newly created susbcription
    */
  override def initSubscription(subscription: Subscription, ser: Serializer[Subscription])
                               (implicit des: Deserializer[Subscription]): Future[Either[ClientError, Option[Subscription]]] = {

    if(subscription == null) {
      logger.warn("Subscription is null")
      Future.successful(Left(ClientError("Subscription must not be null!", None)))
    }
    else if (ser == null) {
      logger.warn("Serializer is null")
      Future.successful(Left(ClientError("Serializer must not be null!", None)))
    }
    else if(des == null) {
      logger.warn("Deserializer is null")
      Future.successful(Left(ClientError("Deserializer must not be null!", None)))
    }
    else {
      logFutureEither(
        connection.post(URI_SUBSCRIPTION, subscription)(ser).flatMap(in => mapToEither(in))
      )
    }
  }


  override def initSubscription(subscription: Subscription): Future[Either[ClientError, Option[Subscription]]] =
    initSubscription(subscription, serializer[Subscription])(deserializer(subscriptionTR))


  def commitCursor(subscriptionId: UUID, cursors: List[Cursor], ser: Serializer[List[Cursor]]): Future[Option[ClientError]]= {
    if(subscriptionId == null) {
      logger.warn("Subscription is null")
      Future.successful(Some(ClientError("Subscription must not be null!", None)))
    }
    else if(cursors == null || cursors.isEmpty) {
      logger.warn("list of cursors to be committed is null or empty")
      Future.successful(Some(ClientError("List of cursors to be committed must not be null or empty!", None)))
    }
    else if (ser == null) {
      logger.warn("Serializer is null")
      Future.successful(Some(ClientError("Serializer must not be null!", None)))
    }
    else {
      logFutureOption(
        connection.put(URI_SUBSCRIPTION_CURSOR_COMMIT(subscriptionId.toString), cursors)(ser).flatMap(in => mapToOption(in))
      )
    }
  }


  def commitCursor(subscriptionId: UUID, cursors: List[Cursor]): Future[Option[ClientError]] =
    commitCursor(subscriptionId, cursors, serializer[List[Cursor]])

}
