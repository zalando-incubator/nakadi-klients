package de.zalando.nakadi.client;

import de.zalando.nakadi.client.domain.Event;
import de.zalando.nakadi.client.domain.Topic;
import de.zalando.nakadi.client.domain.TopicPartition;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public interface Client {

    /**
     * Gets monitoring metrics.
     * NOTE: metrics format is not defined / fixed
     *
     * @return immutable map of metrics data (value can be another Map again)
     */
    Map<String, Object> getMetrics();

    /**
     * Lists all known `Topics` in Event Store.
     *
     * @return immutable list of known topics
     */
    List<Topic> getTopics();

    /**
     * Post a single event to the given topic.  Partition selection is done using the defined partition resolution.
     * The partition resolution strategy is defined per topic and is managed by event store (currently resolved from
     * hash over Event.orderingKey).
     * @param topic  target topic
     * @param event  event to be posted
     */
    void postEvent(final String topic, final Event event);

    /**
     * Get partition information of a given topic
     *
     * @param topic   target topic
     * @return immutable list of topic's partitions information
     */
    List<TopicPartition> getPartitions(final String topic);

    /**
     * Get specific partition
     *
     * @param topic  topic where the partition is located
     * @param partitionId  id of the target partition
     * @return partition information
     */
    TopicPartition getPartition(final String topic, final String partitionId);

    /**
     * Post event to specific partition.
     * NOTE: not implemented by Nakadi yet
     *
     * @param topic  topic where the partition is located
     * @param partitionId  id of the target partition
     * @param event event to be posted
     */
    void postEventToPartition(final String topic, final String partitionId, final Event event);

    /**
     * Blocking subscription to events of specified topic and partition.
     *
     * @param topic  topic where the partition is located
     * @param partitionId id of the target partition
     * @param startOffset start position in 'queue' from which events are received
     * @param batchLimit  number of events which should be received at once (per poll). Must be > 0
     * @param batchFlushTimeoutInSeconds maximum time in seconds to wait for the flushing of each chunk;
     *                                   if the `batch_limit` is reached before this time is reached the messages are
     *                                   immediately flushed to the client
     * @param streamLimit maximum number of events which can be consumed with this stream (consumption finishes after
     *                    {streamLimit} events). If 0 or undefined, will stream indefinitely. Must be > -1
     * @param listener  listener consuming all received events
     */
    void listenForEvents(   final String topic,
                            final String partitionId,
                            final String startOffset,
                            final int batchLimit,
                            final int batchFlushTimeoutInSeconds,
                            final int streamLimit,
                            final EventListener listener);

    /**
     * Blocking subscription to events of specified topic and partition.
     * (batchLimit is set to 1, batch flush timeout to 1,  and streamLimit to 0 -> infinite streaming receiving 1 event per poll)
     *
     * @param topic  topic where the partition is located
     * @param partitionId id of the target partition
     * @param startOffset start position in 'queue' from which events are received
     * @param listener  listener consuming all received events
     */
    void listenForEvents(final String topic,
                         final String partitionId,
                         final String startOffset,
                         final EventListener listener);

    /**
     * Non-blocking subscription to a topic requires a `EventListener` implementation. The event listener must be thread-safe because
     * the listener listens to all partitions of a topic (one thread each).
     *
     * @param threadPool  thread pool to be utilized for listener threads
     * @param topic  subscription topic
     * @param batchLimit  number of events which should be received at once (per poll). Must be > 0
     * @param batchFlushTimeoutInSeconds maximum time in seconds to wait for the flushing of each chunk;
     *                                   if the `batch_limit` is reached before this time is reached the messages are
     *                                   immediately flushed to the client
     * @param streamLimit  maximum number of events which can be consumed with this stream (consumption finishes after
     *                     {streamLimit} events). If 0 or undefined, will stream indefinitely. Must be > -1
     * @param listener  listener consuming all received events
     * @return {Future} instance of listener threads
     */
    List<Future> subscribeToTopic(final ExecutorService threadPool,
                                  final String topic,
                                  final int batchLimit,
                                  final int batchFlushTimeoutInSeconds,
                                  final int streamLimit,
                                  final EventListener listener);

    /**
     * Non-blocking subscription to a topic requires a `EventListener` implementation.
     * The event listener must be thread-safe because
     * the listener listens to all partitions of a topic (one thread each).
     * (batchLimit is set to 1, batch flush timeout to 1,  and streamLimit to 0 -> infinite streaming receiving 1 event per poll)
     *
     *
     * @param threadPool  thread pool to be utilized for listener threads
     * @param topic  subscription topic
     * @param listener  listener consuming all received events
     * @return {Future} instance of listener threads
     */
    @Deprecated
    List<Future> subscribeToTopic(final ExecutorService threadPool,
                                  final String topic,
                                  final EventListener listener);

    /**
     * Non-blocking subscription to a topic requires a `EventListener` implementation. The event listener must be thread-safe because
     * the listener listens to all partitions of a topic (one thread each).
     *
     * @param topic  subscription topic
     * @param batchLimit  number of events which should be received at once (per poll). Must be > 0
     * @param batchFlushTimeoutInSeconds maximum time in seconds to wait for the flushing of each chunk;
     *                                   if the `batch_limit` is reached before this time is reached the messages are
     *                                   immediately flushed to the client
     * @param streamLimit  maximum number of events which can be consumed with this stream (consumption finishes after
     *                     {streamLimit} events). If 0 or undefined, will stream indefinitely. Must be > -1
     * @param listener  listener consuming all received events
     * @return {Future} instance of listener threads
     */
    List<Future> subscribeToTopic(final String topic,
                                  final int batchLimit,
                                  final int batchFlushTimeoutInSeconds,
                                  final int streamLimit,
                                  final EventListener listener);

    /**
     * Non-blocking subscription to a topic requires a `EventListener` implementation. The event listener must be thread-safe because
     * the listener listens to all partitions of a topic (one thread each).
     * (batchLimit is set to 1, batch flush timeout to 1,  and streamLimit to 0 -> infinite streaming receiving 1 event per poll)
     *
     * @param topic  subscription topic
     * @param listener  listener consuming all received events
     * @return {Future} instance of listener threads
     */
    List<Future> subscribeToTopic(final String topic,
                                  final EventListener listener);
}
