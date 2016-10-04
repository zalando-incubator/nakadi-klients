package org.zalando.nakadi.client.scala

import scala.concurrent.Future
import org.zalando.nakadi.client.Deserializer
import org.zalando.nakadi.client.Serializer
import org.zalando.nakadi.client.scala.model._
import org.zalando.nakadi.client._
import com.fasterxml.jackson.core.`type`.TypeReference
import _root_.java.util.UUID


case class ClientError(msg: String, status: Option[Integer] = None, exception: Option[Throwable] = None)

trait Client {

  /**
    * Retrieve monitoring metrics
    *
    * {{{
    * curl --request GET /metrics
    * }}}
    */
  def getMetrics(): Future[Either[ClientError, Option[Metrics]]]

  /**
    * Returns a list of all registered EventTypes.
    *
    * {{{
    * curl --request GET /event-types
    * }}}
    *
    */
  def getEventTypes(): Future[Either[ClientError, Option[Seq[EventType]]]]

  /**
    * Creates a new EventType.
    *
    * {{{
    * curl --request POST -d @fileWithEventType /event-types
    * }}}
    *
    * @param event - The EventType to create.
    *
    */
  def createEventType(eventType: EventType): Future[Option[ClientError]]

  /**
    * Returns the EventType identified by its name.
    * {{{
    * curl --request GET /event-types/{name}
    * }}}
    * @param eventTypeName - Name of the EventType
    */
  def getEventType(eventTypeName: String): Future[Either[ClientError, Option[EventType]]]

  /**
    * Updates the EventType identified by its name.
    * {{{
    * curl --request PUT -d @fileWithEventType /event-types/{name}
    * }}}
    * @param eventTypeName - Name of the EventType
    * @param event - Event to update
    */
  def updateEventType(eventTypeName: String, eventType: EventType): Future[Option[ClientError]]

  /**
    * Deletes an EventType identified by its name.
    *
    * {{{
    * curl --request DELETE /event-types/{name}
    * }}}
    *
    * @param eventTypeName - Name of the EventType
    */
  def deleteEventType(eventTypeName: String): Future[Option[ClientError]]

  /**
    * Publishes multiple Events for the given EventType.
    * {{{
    * curl --request POST -d @fileWithEvent /event-types/{name}/events
    * }}}
    * @param eventTypeName - Name of the EventType
    * @param event - Event to publish
    */
  def publishEvents[T <: Event](eventTypeName: String,
                                events: Seq[T],
                                ser: Serializer[Seq[T]]): Future[Option[ClientError]]

  /**
    * Publishes multiple Events for the given EventType.
    * {{{
    * curl --request POST -d @fileWithEvent /event-types/{name}/events
    * }}}
    * @param eventTypeName - Name of the EventType
    * @param event - Event to publish
    */
  def publishEvents[T <: Event](eventTypeName: String, events: Seq[T]): Future[Option[ClientError]]

  /**
    * List the partitions tola the given EventType.
    * {{{
    * curl --request GET /event-types/{name}/partitions
    * }}}
    * @param eventTypeName -  Name of the EventType
    */
  def getPartitions(eventTypeName: String): Future[Either[ClientError, Option[Seq[Partition]]]]

  /**
    * Returns all of the enrichment strategies supported by this installation of Nakadi.
    * {{{
    * curl --request GET /registry/enrichment-strategies
    * }}}
    */
  def getEnrichmentStrategies(): Future[Either[ClientError, Option[Seq[EventEnrichmentStrategy.Value]]]]

  /**
    * Returns all of the partitioning strategies supported by this installation of Nakadi.
    * {{{
    * curl --request GET /registry/partitioning-strategies
    * }}}
    */
  def getPartitioningStrategies(): Future[Either[ClientError, Option[Seq[PartitionStrategy.Value]]]]

  /**
    * Shuts down the communication system of the client
    */
  def stop(): Option[ClientError]

  /**
    * Registers the subscription of a listener to start streaming events from a partition in non-blocking fashion.
    *
    * @param eventTypeName - Name of the EventType to listen for.
    * @param parameters - Parameters for the streaming of events.
    * @param listener - Listener to pass the event to when it is received.
    * @param des - Json Marshaller(implicit) to deserialize the event to Json.
    */
  def subscribe[T <: Event](eventTypeName: String, parameters: StreamParameters, listener: Listener[T])(
      implicit des: Deserializer[EventStreamBatch[T]]): Option[ClientError]

  /**
    * Registers the subscription of a listener to start streaming events from a partition in non-blocking fashion.
    *
    * @param eventTypeName - Name of the EventType to listen for.
    * @param parameters - Parameters for the streaming of events.
    * @param listener - Listener to pass the event to when it is received.
    * @param typeRef - TypeReference/Helper for using with the Jackson-Objectmapper to deserializing the event to json.
    */
  def subscribe[T <: Event](eventTypeName: String,
                            parameters: StreamParameters,
                            listener: Listener[T],
                            typeRef: TypeReference[EventStreamBatch[T]]): Option[ClientError]

  /**
    * Removes the subscription of a listener, to stop streaming events from a partition.
    *
    * @param eventTypeName - Name of the EventType.
    * @param partition  The partition assigned to this listener.
    * @param listener - Listener to unsubscribe from the streaming events.
    */
  def unsubscribe[T <: Event](eventTypeName: String,
                              partition: Option[String],
                              listener: Listener[T]): Option[ClientError]


  /********************************
   * High Level API
   ********************************/


   /**
    * GET /subscriptions/{subscription_id}/events
    *
    * Starts a new stream for reading events from this subscription. The data will be automatically rebalanced
    * between streams of one subscription. The minimal consumption unit is a partition, so it is possible to start as
    * many streams as the total number of partitions in event-types of this subscription. The rebalance currently
    * only operates with the number of partitions so the amount of data in event-types/partitions is not considered
    * during autorebalance.
    * The position of the consumption is managed by Nakadi. The client is required to commit the cursors he gets in
    * a stream. This also depends on the `commit_mode` parameter that is set for the stream.
    *
    * @param subscriptionId Id of subscription
    * @param streamParameters stream parameters for subscriptions
    */
  def subscribe[T <: Event](subscriptionId: UUID, streamParameters: SubscriptionStreamParameters,  listener: Listener[T])
                           (implicit des: Deserializer[EventStreamBatch[T]]): Option[ClientError]


  /**
   * Creates a subscription for EventTypes. The subscription is needed to be able to consume events from EventTypes in
   * a high level way when Nakadi stores the offsets and manages the rebalancing of consuming clients. The subscription
   * is identified by its key parameters (owning_application, event_types, consumer_group). If this endpoint is invoked
   * several times with the same key subscription properties in body (order of even_types is not important) -
   * the subscription will be created only once and for all other calls it will just return the subscription
   * that was already created.
   *
   * @param subscription  Subscription is a high level consumption unit. Subscriptions allow applications to easily scale
   *                      the number of clients by managing consumed event offsets and distributing load between
   *                      instances. The key properties that identify subscription are 'owning_application',
   *                      'event_types' and 'consumer_group'. It's not possible to have two different subscriptions with
   *                      these properties being the same.
   *
   * @return either an error which was reported from the Nakadi endpoint in order to initialize a subscription OR
    *        the initial subscription data enriched with data about the newly created susbcription
   */
  def initSubscription(subscription: Subscription, ser: Serializer[Subscription])
                      (implicit des: Deserializer[Subscription]): Future[Either[ClientError, Option[Subscription]]]
  def initSubscription(subscription: Subscription): Future[Either[ClientError, Option[Subscription]]]


  /**
    *  Commit soffsets of the subscription. The behavior of commit is specific to commit_mode parameter of the
    *  client stream (please see details in subscription streaming endpoint specification)
    *[
    * @param subscriptionId  id of the subscription
    * @param cursors  recently received cursors to be commited
    */
  def commitCursor(subscriptionId: UUID, cursors: List[Cursor]): Future[Option[ClientError]]
  def commitCursor(subscriptionId: UUID, cursors: List[Cursor], ser: Serializer[List[Cursor]]): Future[Option[ClientError]]



}
