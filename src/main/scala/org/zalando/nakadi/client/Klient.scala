package org.zalando.nakadi.client

import akka.actor.Terminated

import scala.concurrent.Future


/*
 * Paramters for listening on Nakadi
 *
 * @param startOffset start position in 'queue' from which events are received
 * @param batchLimit  number of events which should be received at once (per poll). Must be > 0
 * @param batchFlushTimeoutInSeconds maximum time in seconds to wait for the flushing of each chunk;
 *                                   if the `batch_limit` is reached before this time is reached the messages are
 *                                   immediately flushed to the client
 * @param streamLimit maximum number of events which can be consumed with this stream (consumption finishes after
 *                    {streamLimit} events). If 0 or undefined, will stream indefinitely. Must be > -1
 */
case class ListenParameters(startOffset: Option[String] = None,
                            batchLimit: Option[Int] = Some(DEFAULT_BATCH_LIMIT),
                            batchFlushTimeoutInSeconds: Option[Int] = Some(DEFAULT_BATCH_FLUSH_TIMEOUT_IN_SECONDS),
                            streamLimit: Option[Int] = Some(DEFAULT_STREAM_LIMIT))

trait Klient {
  /**
   * Gets monitoring metrics.
   * NOTE: metrics format is not defined / fixed
   *
   * @return immutable map of metrics data (value can be another Map again)
   */
  def getMetrics: Future[Either[String, Map[String, Any]]]

  /**
   * Lists all known `Topics` in Event Store.
   *
   * @return immutable list of known topics
   */
  def getTopics: Future[Either[String, List[Topic]]]

  /**
   * Get partition information of a given topic
   *
   * @param topic   target topic
   * @return immutable list of topic's partitions information
   */
  def getPartitions(topic: String): Future[Either[String, List[TopicPartition]]]

  /**
   * Post a single event to the given topic.  Partition selection is done using the defined partition resolution.
   * The partition resolution strategy is defined per topic and is managed by event store (currently resolved from
   * hash over Event.orderingKey).
   * @param topic  target topic
   * @param event  event to be posted
   * @return Option representing the error message or None in case of success
   */
  def postEvent(topic: String, event: Event): Future[Either[String,Unit]]

  /**
   * Get specific partition
   *
   * @param topic  topic where the partition is located
   * @param partitionId  id of the target partition
   * @return Either error message or TopicPartition in case of success
   */
  def getPartition(topic: String, partitionId: String): Future[Either[String, TopicPartition]]

  /**
   * Post event to specific partition.
   * NOTE: not implemented by Nakadi yet
   *
   * @param topic  topic where the partition is located
   * @param partitionId  id of the target partition
   * @param event event to be posted
   * @return Option representing the error message or None in case of success
   */
  def postEventToPartition(topic: String, partitionId: String, event: Event): Future[Either[String,Unit]]

  /**
   * Blocking subscription to events of specified topic and partition.
   * (batchLimit is set to 1, batch flush timeout to 1,  and streamLimit to 0 -> infinite streaming receiving 1 event per poll)
   *
   * @param parameters listen parameters
   * @param listener  listener consuming all received events
   * @return Either error message or connection was closed and reconnect is set to false
   */
  def listenForEvents(topic: String,
                      partitionId: String,
                      parameters: ListenParameters,
                      listener: Listener,
                      autoReconnect: Boolean = false): Unit


  /**
   * Non-blocking subscription to a topic requires a `EventListener` implementation. The event listener must be thread-safe because
   * the listener listens to all partitions of a topic (one thread each).
   *
   * @param parameters listen parameters
   * @param listener  listener consuming all received events
   * @return {Future} instance of listener threads
   */
  def subscribeToTopic(topic: String,
                       parameters: ListenParameters,
                       listener: Listener,
                       autoReconnect: Boolean = true): Future[Unit]



  def unsubscribeTopic(topic: String, listener: Listener): Unit

  /**
   * Shuts down the communication system of the client
   */
  def stop(): Future[Terminated]
}
