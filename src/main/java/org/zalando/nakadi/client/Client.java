package org.zalando.nakadi.client;

import scala.Option;
import scala.collection.immutable.List;
import scala.collection.immutable.Map;
import scala.util.Either;

import java.util.concurrent.Future;

public interface Client {
    /**
     * Gets monitoring metrics.
     * NOTE: metrics format is not defined / fixed
     *
     * @return immutable map of metrics data (value can be another Map again)
     */
    Future<Either<String, Map<String, Object>>> getMetrics();

    /**
     * Lists all known `Topics` in Event Store.
     *
     * @return immutable list of known topics
     */
    Future<Either<String, List<Topic>>> getTopics();

    /**
     * Get partition information of a given topic
     *
     * @param topic   target topic
     * @return immutable list of topic's partitions information
     */
    Future<Either<String, List<TopicPartition>>> getPartitions(String topic);

    /**
     * Post a single event to the given topic.  Partition selection is done using the defined partition resolution.
     * The partition resolution strategy is defined per topic and is managed by event store (currently resolved from
     * hash over Event.orderingKey).
     * @param topic  target topic
     * @param event  event to be posted
     * @return Void in case of success
     */
    Future<Either<String,Void>> postEvent(final String topic, final Event event);

    /**
     * Get specific partition
     *
     * @param topic  topic where the partition is located
     * @param partitionId  id of the target partition
     * @return Either error message or TopicPartition in case of success
     */
    Future<Either<String, TopicPartition>> getPartition(String topic, String partitionId);

    /**
     * Post event to specific partition.
     * NOTE: not implemented by Nakadi yet
     *
     * @param topic  topic where the partition is located
     * @param partitionId  id of the target partition
     * @param event event to be posted
     * @return Void in case of success
     */
    Future<Either<String,Void>> postEventToPartition(String topic, String partitionId, Event event);

    /**
     * Blocking subscription to events of specified topic and partition.
     * (batchLimit is set to 1, batch flush timeout to 1,  and streamLimit to 0 -> infinite streaming receiving 1 event per poll)
     *
     * @param parameters listen parameters
     * @param listener  listener consuming all received events
     * @return Either error message or connection was closed and reconnect is set to false
     */
    void listenForEvents(String topic,
                         String partitionId,
                         ListenParameters parameters,
                         Listener listener,
                         boolean autoReconnect);


    /**
     * Non-blocking subscription to a topic requires a `EventListener` implementation. The event listener must be thread-safe because
     * the listener listens to all partitions of a topic (one thread each).
     *
     * @param parameters listen parameters
     * @param listener  listener consuming all received events
     * @return {Future} instance of listener threads
     */
    void subscribeToTopic(String topic,
                          ListenParameters parameters,
                          Listener listener,
                          boolean autoReconnect);

    /**
     * Shuts down the communication system of the client
     */
    void stop();
}
