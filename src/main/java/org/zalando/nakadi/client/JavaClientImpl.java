package org.zalando.nakadi.client;

import com.google.common.base.MoreObjects;
import scala.Option;
import scala.collection.immutable.List;
import scala.collection.immutable.Map;
import scala.util.Either;
import java.util.concurrent.Future;

import static com.google.common.base.Preconditions.checkNotNull;


class JavaClientImpl implements Client {

    private final Klient klient;

    public JavaClientImpl(final Klient klient){
        this.klient = checkNotNull(klient, "Klient instance must not be null");
    }

    @Override
    public Future<Either<String, Map<String, Object>>> getMetrics() {
        return Utils.convert(klient.getMetrics());
    }

    @Override
    public Future<Either<String, List<Topic>>> getTopics() {
        return Utils.convert((scala.concurrent.Future) klient.getTopics());
    }

    @Override
    public Future<Either<String, List<TopicPartition>>> getPartitions(String topic) {
        return Utils.convert((scala.concurrent.Future) klient.getPartitions(topic));
    }

    @Override
    public Future<Either<String,Void>> postEvent(final String topic, final Event event) {
        return Utils.convert((scala.concurrent.Future) klient.postEvent(topic, event));
    }

    @Override
    public Future<Either<String, TopicPartition>> getPartition(final String topic, final String partitionId) {
        return Utils.convert((scala.concurrent.Future) klient.getPartition(topic, partitionId));
    }

    @Override
    public Future<Either<String,Void>> postEventToPartition(final String topic, final String partitionId, final Event event) {
        return Utils.convert((scala.concurrent.Future) klient.postEventToPartition(topic, partitionId, event));
    }

    @Override
    public void listenForEvents(final String topic,
                                final String partitionId,
                                final ListenParameters parameters,
                                final Listener listener,
                                final boolean autoReconnect) {
        klient.listenForEvents(topic, partitionId, parameters, listener, autoReconnect);
    }

    @Override
    public void subscribeToTopic(final String topic,
                                 final ListenParameters parameters,
                                 final Listener listener,
                                 final boolean autoReconnect) {
        klient.subscribeToTopic(topic, parameters, listener, autoReconnect);
    }

    @Override
    public void stop() {
        klient.stop();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("klient", klient)
                .toString();
    }
}
