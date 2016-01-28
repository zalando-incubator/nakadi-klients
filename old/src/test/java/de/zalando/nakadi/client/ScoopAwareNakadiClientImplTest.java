package de.zalando.nakadi.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import de.zalando.nakadi.client.domain.Cursor;
import de.zalando.nakadi.client.domain.Event;
import de.zalando.nakadi.client.domain.SimpleStreamEvent;
import de.zalando.nakadi.client.domain.TopicPartition;
import de.zalando.nakadi.client.utils.NakadiTestService;
import de.zalando.nakadi.client.utils.Request;
import de.zalando.scoop.Scoop;
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
import static org.junit.Assert.assertTrue;

public class ScoopAwareNakadiClientImplTest {
    private NakadiTestService service;
    private ScoopAwareNakadiClientImpl client;
    private final ObjectMapper objectMapper;
    private Event event;
    private Event event2;
    private String partitionsAsString;
    private HttpString httpMethod;
    private TopicPartition partition;
    private TopicPartition partition2;
    final String partitionsRequestPath = String.format("/topics/%s/partitions", TOPIC);
    final String partition1EventsRequestPath = String.format("/topics/%s/partitions/p1/events", TOPIC);
    final String partition2EventsRequestPath = String.format("/topics/%s/partitions/p2/events", TOPIC);

    private static final String TOKEN = "<OAUTH Token>";
    private static final String HOST = "localhost";
    private static final int PORT = 8090;
    private static final String MEDIA_TYPE = "application/json";
    private static final String TOPIC = "test";

    public ScoopAwareNakadiClientImplTest() {
        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
    }

    @Before
    public void setup() throws Exception {
        final ArrayList<TopicPartition> partitions = Lists.newArrayList();
        partition = new TopicPartition();
        partition.setNewestAvailableOffset("4");
        partition.setOldestAvailableOffset("0");
        partition.setPartitionId("p1");
        partitions.add(partition);

        partition2 = new TopicPartition();
        partition2.setNewestAvailableOffset("1");
        partition2.setOldestAvailableOffset("1");
        partition2.setPartitionId("p2");
        partitions.add(partition2);

        partitionsAsString = objectMapper.writeValueAsString(partitions);

        //--

        event = new Event();
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

        event2 = new Event();
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
        streamEvent2.setEvents(Lists.newArrayList(event2));
        streamEvent2.setCursor(cursor2);

        final String streamEvent2AsString = objectMapper.writeValueAsString(streamEvent2) + "\n";

        //--



        httpMethod = new HttpString("GET");
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

        Scoop scoop = new Scoop();
        scoop = scoop.withBindHostName("localhost")
                     .withClusterPort(25551)
                     .withPort(25551);

        final ClientBuilder nakadiClientBuilder = new ClientBuilder();
        final URI nakadiHost = new URI(String.format("http://%s:%s", HOST, PORT));
        client = (ScoopAwareNakadiClientImpl) nakadiClientBuilder.withOAuth2TokenProvider(() -> TOKEN)
                                    .withScoop(scoop)
                                    .withScoopTopic(TOPIC)
                                    .withEndpoint(nakadiHost)
                                    .build();
    }

    @After
    public void tearDown() throws Exception {
        if (service != null) {
            service.stop();
        }
        client.terminateActorSystem();
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

        final ArrayList<Event> receivedEvents = Lists.newArrayList();
        final List<Future> futures = client.subscribeToTopic(TOPIC, (c, e) -> receivedEvents.add(e));
        futures.forEach(f -> {
            try {
                f.get();
            } catch (final Exception e) {
                fail(String.format("an error [error=%s] occurred while while subscribing to [topic=%s]", e.getMessage(), TOPIC));
            }
        });

        //-- check received events

        assertTrue("one event got lost", receivedEvents.contains(event));
        assertTrue("one event got lost", receivedEvents.contains(event2));
        final Map<String, Collection<Request>> collectedRequests = service.getCollectedRequests();
        assertEquals("unexpected number of requests", 3, collectedRequests.size());

        //-- check header and query parameters
        performStandardRequestChecks(partitionsRequestPath, httpMethod);
    }


    @Test
    public void testReconnect() throws Exception {
        final ArrayList<Event> receivedEvents = Lists.newArrayList();
        final List<Future> futures = client.subscribeToTopic(TOPIC, (c, e) -> receivedEvents.add(e));

        Thread.sleep(1000L);
        service.stop();
        Thread.sleep(1000L);
        service.start();
        Thread.sleep(1000L);

        futures.forEach(f -> {
            try {
                f.get();
            } catch (final Exception e) {
                e.printStackTrace();
            }
        });


        final Map<String, Collection<Request>> collectedRequests = service.getCollectedRequests();
        final Collection<Request> partition1Requests = collectedRequests.get(partition1EventsRequestPath);
        assertTrue("there was not reconnect to partition 1", partition1Requests.size() > 1);

        final Collection<Request> partition2Requests = collectedRequests.get(partition1EventsRequestPath);
        assertTrue("there was not reconnect to partition 2", partition2Requests.size() > 1);
    }


    @Test
    public void testPartitioning() throws Exception{
        Scoop scoop = new Scoop();
        scoop = scoop.withBindHostName("localhost")
                .withClusterPort(25552)
                .withPort(25552);

        final ClientBuilder nakadiClientBuilder = new ClientBuilder();
        final URI nakadiHost = new URI(String.format("http://%s:%s", HOST, PORT));
        final Client otherClient = nakadiClientBuilder.withOAuth2TokenProvider(() -> TOKEN)
                                                      .withScoop(scoop)
                                                      .withScoopTopic(TOPIC)
                                                      .withEndpoint(nakadiHost)
                                                      .build();

        Thread.sleep(5000L);




    }

}
