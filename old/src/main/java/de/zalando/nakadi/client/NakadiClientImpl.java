package de.zalando.nakadi.client;


import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.zalando.nakadi.client.domain.*;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.*;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;

class NakadiClientImpl implements Client {

    private final OAuth2TokenProvider tokenProvider;
    private final HttpHost host;
    private final ObjectMapper objectMapper;

    protected enum HttpMethod{GET, POST, PUT, DELETE}
    protected static final String URI_METRICS = "/metrics";
    protected static final String URI_TOPICS = "/topics";
    protected static final String URI_EVENT_POST = "/topics/%s/events";
    protected static final String URI_PARTITIONS = "/topics/%s/partitions";
    protected static final String URI_PARTITION = "/topics/%s/partitions/%s";
    protected static final String URI_EVENTS_ON_PARTITION = "/topics/%s/partitions/%s/events";
    protected static final String URI_EVENT_LISTENING = "/topics/%s/partitions/%s/events?start_from=%s&batch_limit=%s&batch_flush_timeout=%s&stream_limit=%s";

    protected static final int DEFAULT_BATCH_FLUSH_TIMEOUT_IN_SECONDS = 5;
    protected static final int DEFAULT_BATCH_LIMIT = 1;  // 1 event / batch
    protected static final int DEFAULT_STREAM_LIMIT = 0; // stream forever

    private static final Logger LOGGER = LoggerFactory.getLogger(NakadiClientImpl.class);


    NakadiClientImpl(final URI endpoint, final OAuth2TokenProvider tokenProvider, final ObjectMapper objectMapper) {
        this.tokenProvider = checkNotNull(tokenProvider, "OAuth2TokenProvider must not be null");
        checkNotNull(endpoint, "Nakadi endpoint must not be null");
        this.host = new HttpHost(endpoint.getHost(), endpoint.getPort(), endpoint.getScheme());
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Topic> getTopics() {
        final HttpRequestBase request = setUpRequest(HttpMethod.GET, URI_TOPICS);
        final CloseableHttpResponse response = performRequest(request);

        return deserializeToList(response, Topic.class);
    }


    protected HttpRequestBase setUpRequest(final HttpMethod httpMethod, final String uri) {

        final HttpRequestBase request;
        switch(httpMethod){
            case GET: request = new HttpGet(uri); break;
            case PUT: request = new HttpPut(uri); break;
            case POST: request = new HttpPost(uri); break;
            case DELETE: request = new HttpDelete(uri); break;
            default: throw new ClientException("HTTP method '" + httpMethod + "' is not supported");
        }

        request.setHeader("Authorization", "Bearer " + tokenProvider.getToken());
        request.setHeader("Content-Type", "application/json");
        return request;
    }


    private <T> List<T> deserializeToList(final CloseableHttpResponse response, final Class<T> elementClass) {
        try {
            final CollectionType collectionType = objectMapper.getTypeFactory().constructCollectionType(List.class, elementClass);
            final byte[] entityContent = EntityUtils.toByteArray(response.getEntity());
            final List<T> list = objectMapper.readValue(entityContent, collectionType);

            return ImmutableList.copyOf(list);
        }
        catch (final Exception e) {
            throw new ClientException(e);
        }
    }


    protected CloseableHttpResponse performRequest(final HttpRequestBase request)  {

        final HttpClientContext localContext = HttpClientContext.create();
        final CloseableHttpClient client = HttpClients.createDefault();

        try {
            LOGGER.debug("sending [request={}] to [host={}]", request, host);
            final CloseableHttpResponse response = client.execute(host, request, localContext);
            LOGGER.debug("received [response={}] from [request={}] to [host={}]", response, request, host);

            final int status = response.getStatusLine().getStatusCode();
            if (status < 200 || status >= 300) {
                throw new ClientException(response.toString());
            }

            return response;

        }
        catch(final ClientException e){
            throw e;
        }
        catch (final Exception e) {
            throw new ClientException(e);
        }
    }


    @Override
    public Map<String, Object> getMetrics() {
        final HttpRequestBase request = setUpRequest(HttpMethod.GET, URI_METRICS);
        final CloseableHttpResponse response = performRequest(request);
        final Map<String, Object> metricsMap = deserialize(response, Map.class);
        return ImmutableMap.copyOf(metricsMap);
    }


    private <T> T deserialize(final CloseableHttpResponse response, final Class<T> elementClass) {
        try {
            return objectMapper.readValue(EntityUtils.toByteArray(response.getEntity()), elementClass);
        } catch (IOException e) {
            throw new ClientException(e);
        }
    }


    @Override
    public void postEvent(final String topic, final Event event) {
        checkArgument(! isNullOrEmpty(topic), "topic must not be null or empty");
        checkArgument(event != null, "event must not be null");

        final String uri = String.format(URI_EVENT_POST, topic);

        final HttpPost request = (HttpPost) setUpRequest(HttpMethod.POST, uri);
        try {
            final String eventAsString = objectMapper.writeValueAsString(event);
            request.setEntity(new StringEntity(eventAsString));
            performRequest(request);
        }
        catch(final Exception e) {
            throw new ClientException(e);
        }
    }


    @Override
    public List<TopicPartition> getPartitions(final String topic) {
        checkArgument(! isNullOrEmpty(topic), "topic must not be null or empty");

        final String uri = String.format(URI_PARTITIONS, topic);
        final HttpRequestBase request = setUpRequest(HttpMethod.GET, uri);
        final CloseableHttpResponse response = performRequest(request);

        return deserializeToList(response, TopicPartition.class);
    }


    @Override
    public TopicPartition getPartition(String topic, final String partitionId) {
        checkArgument(! isNullOrEmpty(topic), "topic must not be null or empty");
        checkArgument(! isNullOrEmpty(partitionId), "partition id must not be null or empty");

        final String uri = String.format(URI_PARTITION, topic, partitionId);
        final HttpRequestBase request = setUpRequest(HttpMethod.GET, uri);
        final CloseableHttpResponse response = performRequest(request);

        return deserialize(response, TopicPartition.class);
    }


    @Override
    public void postEventToPartition(final String topic, final String partitionId, final Event event) {
        checkArgument(!isNullOrEmpty(topic), "topic must not be null or empty");
        checkArgument(! isNullOrEmpty(partitionId), "partition id must not be null or empty");
        checkArgument(event != null, "event must not be null");

        final String uri = String.format(URI_EVENTS_ON_PARTITION, topic, partitionId);

        final HttpPost request = (HttpPost) setUpRequest(HttpMethod.POST, uri);
        try {
            final String eventAsString = objectMapper.writeValueAsString(event);
            request.setEntity(new StringEntity(eventAsString));
            performRequest(request);
        }
        catch(final Exception e) {
            throw new ClientException(e);
        }
    }


    @Override
    public void listenForEvents(final String topic,
                                final String partitionId,
                                final String startOffset,
                                final int batchLimit,
                                final int batchFlushTimeoutInSeconds,
                                final int streamLimit,
                                final EventListener listener) {
        checkArgument(!isNullOrEmpty(topic), "topic must not be null or empty");
        checkArgument(!isNullOrEmpty(partitionId), "partition id must not be null or empty");
        checkArgument(!isNullOrEmpty(startOffset), "start offset must not be null or empty");
        checkArgument(batchLimit > 0, "batch limit must be > 0. Given: %s", batchLimit);
        checkArgument(batchFlushTimeoutInSeconds > -1, "batch flush timeout must be > -1. Given: %s", batchFlushTimeoutInSeconds);
        checkArgument(streamLimit > -1, "stream limit must be > -1. Given: %s", streamLimit);

        listenForEvents(topic,
                        partitionId,
                        startOffset,
                        batchLimit,
                        batchFlushTimeoutInSeconds,
                        streamLimit,
                        listener,
                        (SimpleStreamEvent streamEvent) -> notifyEvent(listener, streamEvent));
    }


    protected void listenForEvents(final String topic,
                                final String partitionId,
                                final String startOffset,
                                final int batchLimit,
                                final int batchFlushTimeoutInSeconds,
                                final int streamLimit,
                                final EventListener listener,
                                final Consumer<SimpleStreamEvent> streamEventConsumer) {

        checkArgument(! isNullOrEmpty(topic), "topic must not be null or empty");
        checkArgument(! isNullOrEmpty(partitionId), "partition id must not be null or empty");
        checkArgument(! isNullOrEmpty(startOffset), "start offset must not be null or empty");
        checkArgument(batchLimit > 0, "batch limit must be > 0. Given: %s", batchLimit);
        checkArgument(batchFlushTimeoutInSeconds > -1, "batch flush timeout must be > -1. Given: %s", batchFlushTimeoutInSeconds);
        checkArgument(streamLimit > -1, "stream limit must be > -1. Given: %s", streamLimit);

        final String uri = String.format(URI_EVENT_LISTENING, topic, partitionId, startOffset, batchLimit, batchFlushTimeoutInSeconds, streamLimit);
        final HttpRequestBase request = setUpRequest(HttpMethod.GET, uri);
        final CloseableHttpResponse response = performRequest(request);


        try {
            final InputStream contentInputStream = response.getEntity().getContent();
            StreamReader.read(  contentInputStream,
                                objectMapper,
                                streamEventConsumer);
            LOGGER.info("stream from [response={}] retrieved from [URI={}] was closed", response, uri);
        } catch (final IOException e) {
            if(listener instanceof  EventListener2) ((EventListener2) listener).onConnectionClosed(topic, partitionId, e);
            throw new ClientException(e);
        }

        if(listener instanceof  EventListener2) ((EventListener2) listener).onConnectionClosed(topic, partitionId);
    }


    private void notifyEvent(final EventListener listener,
                             final SimpleStreamEvent streamingEvent){

        LOGGER.debug("received [streamingEvent={}]", streamingEvent);
        final Cursor cursor = streamingEvent.getCursor();
        final List<Event> events = streamingEvent.getEvents();
        if(events == null || events.isEmpty()) {
            LOGGER.debug("received [streamingEvent={}] does not contain any event ", streamingEvent);
        }
        else {
            streamingEvent.getEvents().forEach(event -> {
                try {
                    listener.onReceive(cursor, event);
                } catch (final Exception e) {
                    LOGGER.warn("a problem occurred while passing [cursor={}, event={}] to [listener={}] -> continuing with next events",
                            cursor, event, listener, e);
                }
            });
        }
    }



    @Override
    public void listenForEvents(final String topic,
                                final String partitionId,
                                final String startOffset,
                                final EventListener listener) {
        listenForEvents(topic,
                        partitionId,
                        startOffset,
                        DEFAULT_BATCH_LIMIT,
                        DEFAULT_BATCH_FLUSH_TIMEOUT_IN_SECONDS,
                        DEFAULT_STREAM_LIMIT,
                        listener);
    }


    @Override
    public List<Future> subscribeToTopic(final String topic,
                                         final int batchLimit,
                                         final int batchFlushTimeout,
                                         final int streamLimit,
                                         final EventListener listener){

        checkArgument(!isNullOrEmpty(topic), "topic must not be null or empty");
        checkArgument(batchLimit > 0, "batch limit must be > 0. Given: %s", batchLimit);
        checkArgument(batchFlushTimeout > -1, "batch flush timeout must be > -1. Given: %s", batchFlushTimeout);
        checkArgument(streamLimit > -1, "stream limit must be > -1. Given: %s", streamLimit);

        final List<TopicPartition> partitions = getPartitions(topic);
        final ExecutorService executorService = Executors.newFixedThreadPool(partitions.size());

        return subscribeToPartitions(executorService, partitions, topic, batchLimit, batchFlushTimeout, streamLimit, listener);
    }


    @Override
    public List<Future> subscribeToTopic(final String topic, final EventListener listener) {
        return subscribeToTopic(topic,
                                DEFAULT_BATCH_LIMIT,
                                DEFAULT_BATCH_FLUSH_TIMEOUT_IN_SECONDS,
                                DEFAULT_STREAM_LIMIT,
                                listener);
    }


    @Override
    public List<Future> subscribeToTopic(final ExecutorService threadPool,
                                         final String topic,
                                         final int batchLimit,
                                         final int batchFlushLimit,
                                         final int streamLimit,
                                         final EventListener listener){

        checkArgument(threadPool != null, "thread pool must not be null");
        checkArgument(!isNullOrEmpty(topic), "topic must not be null or empty");
        checkArgument(batchLimit > 0, "batch limit must be > 0. Given: %s", batchLimit);
        checkArgument(batchLimit > -1, "stream limit must be > -1. Given: %s", streamLimit);

        final List<TopicPartition> partitions = getPartitions(topic);
        return subscribeToPartitions(threadPool, partitions, topic, batchLimit,batchFlushLimit, streamLimit, listener);
    }


    @Override
    public List<Future> subscribeToTopic(final ExecutorService threadPool, final String topic, final EventListener listener) {
        return subscribeToTopic(threadPool,
                                topic,
                                DEFAULT_BATCH_LIMIT,
                                DEFAULT_BATCH_FLUSH_TIMEOUT_IN_SECONDS,
                                DEFAULT_STREAM_LIMIT,
                                listener);
    }


    private ImmutableList<Future> subscribeToPartitions(final ExecutorService threadPool,
                                                        final List<TopicPartition> partitions,
                                                        final String topic,
                                                        final int batchLimit,
                                                        final int batchFlushTimeout,
                                                        final int streamLimit,
                                                        final EventListener listener) {
        final ImmutableList.Builder<Future> builder = ImmutableList.builder();
        Future future;
        for (TopicPartition partition : partitions) {
            future = threadPool.submit(() -> listenForEvents(topic,
                                                             partition.getPartitionId(),
                                                             partition.getNewestAvailableOffset(),
                                                             batchLimit,
                                                             batchFlushTimeout,
                                                             streamLimit,
                                                             listener));
            builder.add(future);
        }


        return builder.build();
    }

}
