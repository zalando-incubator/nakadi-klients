package de.zalando.nakadi.client;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.base.MoreObjects;
import com.google.common.collect.*;
import de.zalando.nakadi.client.domain.*;
import de.zalando.nakadi.client.utils.NakadiTestService;
import de.zalando.nakadi.client.utils.Request;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.util.*;
import java.util.concurrent.Future;

import static org.junit.Assert.*;

public class NakadiClientImplTest {

    private NakadiTestService service;
    private Client client;
    private final ObjectMapper objectMapper;

    private static final String TOKEN = "<OAUTH Token>";
    private static final String HOST = "localhost";
    private static final int PORT = 8081;
    private static final String MEDIA_TYPE = "application/json";


    public NakadiClientImplTest() {
        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
    }

    @Before
    public void setup() throws Exception {

        final ClientBuilder builder = new ClientBuilder();
        final URI nakadiHost = new URI(String.format("http://%s:%s", HOST, PORT));
        client = builder.withOAuth2TokenProvider(() -> TOKEN)
                        .withEndpoint(nakadiHost)
                        .build();

    }

    @After
    public void tearDown() throws Exception {
        if (service != null) {
            service.stop();
        }
    }


    private Request performStandardRequestChecks(final String expectedRequestPath, final HttpString expectedRequestMethod){

        final Map<String, Collection<Request>> collectedRequestsMap = service.getCollectedRequests();

        final Collection<Request> requests = collectedRequestsMap.get(expectedRequestPath);
        assertNotNull("request " + expectedRequestPath + " was not recorded", requests);

        final Request request = Iterators.getLast(requests.iterator());

        assertEquals("invalid request path. test must be buggy", expectedRequestPath, request.getRequestPath());
        assertEquals("invalid request method used by request", expectedRequestMethod, request.getRequestMethod());

        final HeaderMap headerMap = request.getRequestHeaders();
        HeaderValues headerValues = headerMap.get(Headers.CONTENT_TYPE);
        final String mediaType = headerValues.getFirst();
        assertEquals("invalid media type in request", mediaType, MEDIA_TYPE);

        headerValues = headerMap.get(Headers.AUTHORIZATION);
        final String authorizationHeaderValue = headerValues.getFirst();
        assertEquals("Authorization header in request is not set or invalid","Bearer " + TOKEN, authorizationHeaderValue);

        return request;
    }

    @Test
    public void testGetMetrics() throws Exception {

        final HashMap<String, Object> expectedResponse = Maps.newHashMap();

        final HashMap<String, Object> getMetricsData = Maps.newHashMap();
        getMetricsData.put("calls_per_second", "0.011");
        getMetricsData.put("count", "1");
        Map<String, Object> statusCodeMetrics = Maps.newHashMap();
        statusCodeMetrics.put("401", "1");
        getMetricsData.put("status_codes",statusCodeMetrics);
        expectedResponse.put("get_metrics", getMetricsData);

        final HashMap<String, Object> postEventData = Maps.newHashMap();
        postEventData.put("calls_per_second", "0.005");
        postEventData.put("count", "5");
        statusCodeMetrics = Maps.newHashMap();
        statusCodeMetrics.put("201", "5");
        postEventData.put("status_codes", statusCodeMetrics);
        expectedResponse.put("post_event", postEventData);

        // ---

        final String expectedResponseAsString = objectMapper.writeValueAsString(expectedResponse);
        final HttpString requestMethod = new HttpString("GET");
        final String requestPath = "/metrics";
        final int responseStatusCode = 200;

        final NakadiTestService.Builder builder = new NakadiTestService.Builder();
        service = builder.withHost(HOST)
                         .withPort(PORT)
                         .withHandler(requestPath)
                         .withRequestMethod(requestMethod)
                         .withResponseContentType(MEDIA_TYPE)
                         .withResponseStatusCode(responseStatusCode)
                         .withResponsePayload(expectedResponseAsString)
                         .build();
        service.start();

        // check if response could be handled correctly

        final Map<String, Object> receivedMetrics = client.getMetrics();
        assertEquals("metrics data deserialiazation is not correct", expectedResponse, receivedMetrics);

        performStandardRequestChecks(requestPath, requestMethod);
    }


    @Test
    public void testGetTopics() throws Exception {

        final ArrayList<Topic> expectedResponse = Lists.newArrayList();
        Topic topic = new Topic();
        topic.setName("test-topic-1");
        expectedResponse.add(topic);

        topic = new Topic();
        topic.setName("test-topic-2");
        expectedResponse.add(topic);

        final String expectedResponseAsString = objectMapper.writeValueAsString(expectedResponse);
        final HttpString requestMethod = new HttpString("GET");
        final String requestPath = "/topics";
        final int responseStatusCode = 200;

        final NakadiTestService.Builder builder = new NakadiTestService.Builder();
        service = builder.withHost(HOST)
                .withPort(PORT)
                .withHandler(requestPath)
                .withRequestMethod(requestMethod)
                .withResponseContentType(MEDIA_TYPE)
                .withResponseStatusCode(responseStatusCode)
                .withResponsePayload(expectedResponseAsString)
                .build();
        service.start();

        final List<Topic> receivedTopics = client.getTopics();
        assertEquals("topics data deserialiazation is not correct", expectedResponse, new ArrayList<Topic>(receivedTopics));

        performStandardRequestChecks(requestPath, requestMethod);
    }


    @Test
    public void testPostEvent() throws Exception {
        final Event event = new Event();
        event.setEventType("http://test.zalando.net/my_type");
        event.setOrderingKey("ARTICLE:123456");

        final HashMap<String, String> bodyMap = Maps.newHashMap();
        bodyMap.put("greeting", "hello");
        bodyMap.put("target", "world");
        event.setBody(bodyMap);

        final HashMap<String, Object> metaDataMap = Maps.newHashMap();
        metaDataMap.put("tenant-id", "234567");
        metaDataMap.put("flow-id", "123456789");
        event.setMetadata(metaDataMap);

        final String topic = "test-topic-1";
        final HttpString requestMethod = new HttpString("POST");
        final String requestPath = "/topics/" + topic + "/events";
        final int responseStatusCode = 201;

        final NakadiTestService.Builder builder = new NakadiTestService.Builder();
        service = builder.withHost(HOST)
                .withPort(PORT)
                .withHandler(requestPath)
                .withRequestMethod(requestMethod)
                .withResponseContentType(MEDIA_TYPE)
                .withResponseStatusCode(responseStatusCode)
                .withResponsePayload("")
                .build();
        service.start();

        client.postEvent(topic, event);

        final Request request = performStandardRequestChecks(requestPath, requestMethod);
        final String requestBody = request.getRequestBody();
        final Event sentEvent = objectMapper.readValue(requestBody, Event.class);

        assertEquals("something went wrong with the event transmission", event, sentEvent);
    }

    @Test
    public void testGetPartitions() throws Exception {
        final ArrayList<TopicPartition> expectedPartitions = Lists.newArrayList();
        TopicPartition partition = new TopicPartition();
        partition.setNewestAvailableOffset("0");
        partition.setOldestAvailableOffset("0");
        partition.setPartitionId("111");
        expectedPartitions.add(partition);

        partition = new TopicPartition();
        partition.setNewestAvailableOffset("1");
        partition.setOldestAvailableOffset("0");
        partition.setPartitionId("222");
        expectedPartitions.add(partition);

        final String expectedResponse = objectMapper.writeValueAsString(expectedPartitions);


        final String topic = "test-topic-1";
        final HttpString requestMethod = new HttpString("GET");
        final String requestPath = "/topics/" + topic + "/partitions";
        final int responseStatusCode = 200;

        final NakadiTestService.Builder builder = new NakadiTestService.Builder();
        service = builder.withHost(HOST)
                .withPort(PORT)
                .withHandler(requestPath)
                .withRequestMethod(requestMethod)
                .withResponseContentType(MEDIA_TYPE)
                .withResponseStatusCode(responseStatusCode)
                .withResponsePayload(expectedResponse)
                .build();
        service.start();

        final List<TopicPartition> receivedPartitions = client.getPartitions(topic);
        assertEquals("partition data deserialization does not work properly", expectedPartitions, receivedPartitions);

        performStandardRequestChecks(requestPath, requestMethod);
    }

    @Test
    public void testGetPartition() throws Exception {

        final TopicPartition expectedPartition = new TopicPartition();
        expectedPartition.setNewestAvailableOffset("0");
        expectedPartition.setOldestAvailableOffset("0");
        expectedPartition.setPartitionId("111");

        final String expectedResponse = objectMapper.writeValueAsString(expectedPartition);


        final String partitionId = "111";
        final String topic = "test-topic-1";
        final HttpString requestMethod = new HttpString("GET");
        final String requestPath = "/topics/" + topic + "/partitions/" + partitionId;
        final int responseStatusCode = 200;

        final NakadiTestService.Builder builder = new NakadiTestService.Builder();
        service = builder.withHost(HOST)
                .withPort(PORT)
                .withHandler(requestPath)
                .withRequestMethod(requestMethod)
                .withResponseContentType(MEDIA_TYPE)
                .withResponseStatusCode(responseStatusCode)
                .withResponsePayload(expectedResponse)
                .build();
        service.start();

        final TopicPartition receivedPartition = client.getPartition(topic, partitionId);
        assertEquals("partition data deserialization does not work properly", expectedPartition, receivedPartition);

        performStandardRequestChecks(requestPath, requestMethod);
    }

    private void checkQueryParameter(final Map<String, Deque<String>> queryParameters,
                                     final String paramaterName,
                                     final String expectedValue) {

        final Deque<String> paramDeque = queryParameters.get(paramaterName);
        assertNotNull(String.format("query parameter [parameterName=%s] is not set", paramaterName), paramDeque);

        final String value = paramDeque.getFirst();
        assertEquals(String.format("query parameter [parameterName=%s] has wrong value", paramaterName), expectedValue, value);
    }

    @Test
    public void testSubscibeToTopic() throws Exception {

        final ArrayList<TopicPartition> partitions = Lists.newArrayList();
        final TopicPartition partition = new TopicPartition();
        partition.setNewestAvailableOffset("4");
        partition.setOldestAvailableOffset("0");
        partition.setPartitionId("p1");
        partitions.add(partition);

        final TopicPartition partition2 = new TopicPartition();
        partition2.setNewestAvailableOffset("1");
        partition2.setOldestAvailableOffset("1");
        partition2.setPartitionId("p2");
        partitions.add(partition2);

        final String partitionsAsString = objectMapper.writeValueAsString(partitions);

        //--

        final Event event = new Event();
        event.setEventType("partition1");
        event.setOrderingKey("ARTICLE:123456");
        final HashMap<String, String> bodyMap = Maps.newHashMap();
        bodyMap.put("greeting", "hello");
        bodyMap.put("target", "world");
        event.setBody(bodyMap);
        final HashMap<String, Object> metaDataMap = Maps.newHashMap();
        metaDataMap.put("tenant-id", "234567");
        metaDataMap.put("flow-id", "123456789");
        event.setMetadata(metaDataMap);

        final Cursor cursor = new Cursor();
        cursor.setPartition("p1");
        cursor.setOffset("0");

        final SimpleStreamEvent streamEvent1 = new SimpleStreamEvent();
        streamEvent1.setEvents(Lists.newArrayList(event));
        streamEvent1.setCursor(cursor);

        final String streamEvent1AsString = objectMapper.writeValueAsString(streamEvent1)  + "\n";

        //--

        final Event event2 = new Event();
        event2.setEventType("partition1");
        event2.setOrderingKey("ARTICLE:123456");
        final HashMap<String, String> bodyMap2 = Maps.newHashMap();
        bodyMap2.put("greeting", "hello");
        bodyMap2.put("target", "world");
        event2.setBody(bodyMap2);
        final HashMap<String, Object> metaDataMap2 = Maps.newHashMap();
        metaDataMap2.put("tenant-id", "234567");
        metaDataMap2.put("flow-id", "123456789");
        event2.setMetadata(metaDataMap2);

        final Cursor cursor2 = new Cursor();
        cursor2.setPartition("p1");
        cursor2.setOffset("0");

        final SimpleStreamEvent streamEvent2 = new SimpleStreamEvent();
        streamEvent1.setEvents(Lists.newArrayList(event2));
        streamEvent1.setCursor(cursor2);

        final String streamEvent2AsString = objectMapper.writeValueAsString(streamEvent2) + "\n";

        //--

        final String topic = "test-topic-1";
        final String partitionsRequestPath = String.format("/topics/%s/partitions", topic);
        final String partition1EventsRequestPath = String.format("/topics/%s/partitions/p1/events", topic);
        final String partition2EventsRequestPath = String.format("/topics/%s/partitions/p2/events", topic);

        final HttpString httpMethod = new HttpString("GET");
        final int statusCode= 200;


        final NakadiTestService.Builder builder = new NakadiTestService.Builder();
        service = builder.withHost(HOST)
                .withPort(PORT)
                .withHandler(partitionsRequestPath)
                .withRequestMethod(httpMethod)
                .withResponseContentType(MEDIA_TYPE)
                .withResponseStatusCode(statusCode)
                .withResponsePayload(partitionsAsString)
                .and()
                .withHandler(partition1EventsRequestPath)
                .withRequestMethod(httpMethod)
                .withResponseContentType(MEDIA_TYPE)
                .withResponseStatusCode(statusCode)
                .withResponsePayload(streamEvent1AsString)
                .and()
                .withHandler(partition2EventsRequestPath)
                .withRequestMethod(httpMethod)
                .withResponseContentType(MEDIA_TYPE)
                .withResponseStatusCode(statusCode)
                .withResponsePayload(streamEvent2AsString)
                .build();
        service.start();

        final ArrayList<Event> receivedEvents = Lists.newArrayList();

        final List<Future> futures = client.subscribeToTopic(topic, (c,e) -> receivedEvents.add(e)  );

        futures.forEach(f -> {
            try {
                f.get();
            } catch (final Exception e) {
                fail(String.format("an errror [error=%s] occurred while while subscribing to [topic=%s]", e.getMessage(), topic));
            }
        });

        //-- check received events

        assertTrue("one event got lost", receivedEvents.contains(event));
        assertTrue("one event got lost", receivedEvents.contains(event2));
        final Map<String, Collection<Request>> collectedRequests = service.getCollectedRequests();
        assertEquals("unexpected number of requests", 3, collectedRequests.size());


        //-- check header and query parameters

        performStandardRequestChecks(partitionsRequestPath, httpMethod);

        Request request = performStandardRequestChecks(partition1EventsRequestPath, httpMethod);
        Map<String, Deque<String>> queryParameters = request.getRequestQueryParameters();
        checkQueryParameter(queryParameters, "start_from", partition.getNewestAvailableOffset());
        checkQueryParameter(queryParameters, "batch_limit", "1");
        checkQueryParameter(queryParameters, "stream_limit", "0");
        checkQueryParameter(queryParameters, "batch_flush_timeout", "5");


        request = performStandardRequestChecks(partition2EventsRequestPath, httpMethod);
        queryParameters = request.getRequestQueryParameters();
        checkQueryParameter(queryParameters, "start_from", partition2.getNewestAvailableOffset());
        checkQueryParameter(queryParameters, "batch_limit", "1");
        checkQueryParameter(queryParameters, "stream_limit", "0");
        checkQueryParameter(queryParameters, "batch_flush_timeout", "5");
    }

    @Test
    public void testEventListener2() throws Exception {
            final ArrayList<TopicPartition> partitions = Lists.newArrayList();
            final TopicPartition partition = new TopicPartition();
            partition.setNewestAvailableOffset("4");
            partition.setOldestAvailableOffset("0");
            partition.setPartitionId("p1");
            partitions.add(partition);

            final TopicPartition partition2 = new TopicPartition();
            partition2.setNewestAvailableOffset("1");
            partition2.setOldestAvailableOffset("1");
            partition2.setPartitionId("p2");
            partitions.add(partition2);

            final String partitionsAsString = objectMapper.writeValueAsString(partitions);

            //--

            final Event event = new Event();
            event.setEventType("partition1");
            event.setOrderingKey("ARTICLE:123456");
            final HashMap<String, String> bodyMap = Maps.newHashMap();
            bodyMap.put("greeting", "hello");
            bodyMap.put("target", "world");
            event.setBody(bodyMap);
            final HashMap<String, Object> metaDataMap = Maps.newHashMap();
            metaDataMap.put("tenant-id", "234567");
            metaDataMap.put("flow-id", "123456789");
            event.setMetadata(metaDataMap);

            final Cursor cursor = new Cursor();
            cursor.setPartition("p1");
            cursor.setOffset("0");

            final SimpleStreamEvent streamEvent1 = new SimpleStreamEvent();
            streamEvent1.setEvents(Lists.newArrayList(event));
            streamEvent1.setCursor(cursor);

            final String streamEvent1AsString = objectMapper.writeValueAsString(streamEvent1) + "\n";


            //--

            final String topic = "test-topic-1";
            final String partitionsRequestPath = String.format("/topics/%s/partitions", topic);
            final String partition1EventsRequestPath = String.format("/topics/%s/partitions/p1/events", topic);
            final String partition2EventsRequestPath = String.format("/topics/%s/partitions/p2/events", topic);

            final HttpString httpMethod = new HttpString("GET");
            final int statusCode= 200;


            final NakadiTestService.Builder builder = new NakadiTestService.Builder();
            service = builder.withHost(HOST)
                    .withPort(PORT)
                    .withHandler(partitionsRequestPath)
                    .withRequestMethod(httpMethod)
                    .withResponseContentType(MEDIA_TYPE)
                    .withResponseStatusCode(statusCode)
                    .withResponsePayload(partitionsAsString)
                    .and()
                    .withHandler(partition1EventsRequestPath)
                    .withRequestMethod(httpMethod)
                    .withResponseContentType(MEDIA_TYPE)
                    .withResponseStatusCode(statusCode)
                    .withResponsePayload(streamEvent1AsString)
                    .and()
                    .withHandler(partition2EventsRequestPath)
                    .withRequestMethod(httpMethod)
                    .withResponseContentType(MEDIA_TYPE)
                    .withResponseStatusCode(statusCode)
                    .withResponsePayload("\n")
                    .build();
                service.start();


            final EventListener2Mock eventListener2Mock = new EventListener2Mock();
            final List<Future> futures = client.subscribeToTopic(topic, eventListener2Mock);

            futures.forEach(f -> {
                try {
                    f.get();
                } catch (final Exception e) {
                    fail(String.format("an errror [error=%s] occurred while while subscribing to [topic=%s]", e.getMessage(), topic));
                }
            });


            final List<Map<String, Object>> receivedCloseCalls = eventListener2Mock.getReceivedOnConnectionClosedCalls();
            assertEquals("not enough close calls communicated", partitions.size(), receivedCloseCalls.size());

            final List<Map<String, Object>> receivedCloseCallsWithErrors = eventListener2Mock.getReceivedOnConnectionClosedCallsWithProblems();
            assertEquals("there should not have been any error", 0, receivedCloseCallsWithErrors.size());
        }


    private static final class EventListener2Mock implements EventListener2 {

        private List<Map<String, Object>> receivedEvents;
        private List<Map<String,Object>> receivedOnConnectionClosedCalls;
        private List<Map<String,Object>> receivedOnConnectionClosedCallsWithProblems;

        public EventListener2Mock(){
            this.receivedEvents = Lists.newArrayList();
            this.receivedOnConnectionClosedCalls = Lists.newArrayList();
            this.receivedOnConnectionClosedCallsWithProblems = Lists.newArrayList();
        }

        public List<Map<String, Object>> getReceivedEvents() {
            return ImmutableList.copyOf(receivedEvents);
        }

        public List<Map<String, Object>> getReceivedOnConnectionClosedCalls() {
            return ImmutableList.copyOf(receivedOnConnectionClosedCalls);
        }

        public List<Map<String, Object>> getReceivedOnConnectionClosedCallsWithProblems() {
            return ImmutableList.copyOf(receivedOnConnectionClosedCallsWithProblems);
        }

        @Override
        public void onConnectionClosed(String topic, String partitionId) {
            final HashMap<String, Object> entry = Maps.newHashMap();
            entry.put("topic", topic);
            entry.put("partitionId", partitionId);
            receivedOnConnectionClosedCalls.add(entry);

        }

        @Override
        public void onConnectionClosed(String topic, String partitionId, Exception cause) {
            final HashMap<String, Object> entry = Maps.newHashMap();
            entry.put("topic", topic);
            entry.put("partitionId", partitionId);
            entry.put("cause", cause);
            receivedOnConnectionClosedCallsWithProblems.add(entry);
        }

        @Override
        public void onReceive(Cursor cursor, Event event) {
            final HashMap<String, Object> entry = Maps.newHashMap();
            entry.put("event", event);
            entry.put("cursor", cursor);
            receivedEvents.add(entry);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("receivedEvents", receivedEvents)
                    .add("receivedOnConnectionClosedCalls", receivedOnConnectionClosedCalls)
                    .add("receivedOnConnectionClosedCallsWithProblems", receivedOnConnectionClosedCallsWithProblems)
                    .toString();
        }
    }

}
