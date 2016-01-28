package org.zalando.nakadi.client

import java.net.URI
import java.util
import java.util.concurrent.atomic.AtomicReference

import com.fasterxml.jackson.databind.{PropertyNamingStrategy, SerializationFeature, DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.google.common.collect.Iterators
import com.typesafe.scalalogging.LazyLogging
import io.undertow.util.{HeaderValues, HttpString, Headers}
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import org.zalando.nakadi.client.actor.PartitionReceiver
import org.zalando.nakadi.client.utils.NakadiTestService
import org.zalando.nakadi.client.utils.NakadiTestService.Builder

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps


class TestListener extends  Listener {
  var receivedEvents = new AtomicReference[List[Event]](List[Event]())
  var onConnectionClosed = 0
  var onConnectionOpened = 0
  var onConnectionFailed = 0

  override def id = "test"

  override def onReceive(topic: String, partition: String, cursor: Cursor, event: Event): Unit =  {
    println(s"WAS CALLED [topic=$topic, partition=$partition, event=$event]" )

    var old = List[Event]()
    do {
      old = receivedEvents.get()
    }
    while(! receivedEvents.compareAndSet(old, old ++ List(event)))
  }
  override def onConnectionClosed(topic: String, partition: String, lastCursor: Option[Cursor]): Unit = onConnectionClosed += 1
  override def onConnectionOpened(topic: String, partition: String): Unit = onConnectionOpened += 1
  override def onConnectionFailed(topic: String, partition: String, status: Int, error: String): Unit = onConnectionFailed += 1
}


class KlientSpec extends WordSpec with Matchers with BeforeAndAfterEach with LazyLogging {

  var klient: Klient = null
  var service: NakadiTestService = null
  val objectMapper = new ObjectMapper
  objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
  objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES)
  objectMapper.registerModule(new DefaultScalaModule)

  val MEDIA_TYPE = "application/json"
  val TOKEN = "<OAUTH Token>"
  val HOST = "localhost"
  val PORT = 8081

  override  def beforeEach() = {
    klient = KlientBuilder()
      .withEndpoint(new URI(HOST)) // TODO if no scheme is specified, the library utilized by the client breaks with a NullpointeException...
      .withPort(PORT)
      .withTokenProvider(() => TOKEN)
      .build()
  }

  override def afterEach(): Unit = {
    klient.stop()

    if(Option(service).isDefined) {
      service.stop()
      service = null
    }
  }

  private def performStandardRequestChecks(expectedRequestPath: String, expectedRequestMethod: HttpString) = {
    val collectedRequestsMap = service.getCollectedRequests
    val requests = collectedRequestsMap.get(expectedRequestPath)
    requests should not be null

    val request = Iterators.getLast(requests.iterator)
    request.getRequestPath should be(expectedRequestPath)
    request.getRequestMethod should be(expectedRequestMethod)

    val headerMap = request.getRequestHeaders

    var headerValues: HeaderValues = null

    if(request.getRequestMethod.equals(new HttpString("GET"))){
      headerValues= headerMap.get(Headers.ACCEPT)
      val mediaType= headerValues.getFirst
      mediaType should be(MEDIA_TYPE)
    }
    else {
      headerValues= headerMap.get(Headers.CONTENT_TYPE)
      val mediaType= headerValues.getFirst
      mediaType should be(MEDIA_TYPE)
    }


    headerValues = headerMap.get(Headers.AUTHORIZATION)
    val authorizationHeaderValue = headerValues.getFirst
    authorizationHeaderValue should be(s"Bearer $TOKEN")

    request
  }

  private def checkQueryParameter(queryParameters: java.util.Map[String, util.Deque[String]], paramaterName: String, expectedValue: String) {
    val paramDeque = queryParameters.get(paramaterName)
    paramDeque should not be null
    paramDeque.getFirst should be(expectedValue)
  }

  "A Klient" must {
    "retrieve Nakadi metrics" in {
      val expectedResponse = Map("post_event" -> Map("calls_per_second" -> "0.005",
                                                      "count" -> "5",
                                                      "status_codes" -> Map("201" -> 5)),
                                  "get_metrics" -> Map("calls_per_second" -> "0.001",
                                                       "count" -> "1",
                                                       "status_codes" -> Map("401" -> 1)))

      // ---
      val expectedResponseAsString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(expectedResponse)
      val requestMethod = new HttpString("GET")
      val requestPath = "/metrics"
      val responseStatusCode: Int = 200

      val builder= new Builder
      service = builder.withHost(HOST)
                       .withPort(PORT)
                       .withHandler(requestPath)
                       .withRequestMethod(requestMethod)
                       .withResponseContentType(MEDIA_TYPE)
                       .withResponseStatusCode(responseStatusCode)
                       .withResponsePayload(expectedResponseAsString)
                       .build
      service.start()

      Await.result(
        klient.getMetrics,
        5 seconds
      ) match {
        case Left(error) => fail(s"could not retrieve metrics: $error")
        case Right(metrics) => logger.debug(s"metrics => $metrics")
                               performStandardRequestChecks(requestPath, requestMethod)
      }
    }

    "retrieve Nakadi topics" in {
      val expectedResponse = List(Topic("test-topic-1"), Topic("test-topic-2"))


      val expectedResponseAsString = objectMapper.writeValueAsString(expectedResponse)
      val requestMethod = new HttpString("GET")
      val requestPath = "/topics"
      val responseStatusCode = 200

      val builder = new Builder
      service = builder.withHost(HOST)
                       .withPort(PORT)
                       .withHandler(requestPath)
                       .withRequestMethod(requestMethod)
                       .withResponseContentType(MEDIA_TYPE)
                       .withResponseStatusCode(responseStatusCode)
                       .withResponsePayload(expectedResponseAsString)
                       .build
      service.start()

      Await.result(
        klient.getTopics,
        10 seconds
      ) match {
          case Left(error) => fail(s"could not retrieve topics: $error")
          case Right(topics) =>
            logger.info(s"topics => $topics")
            topics should be(expectedResponse)
            performStandardRequestChecks(requestPath, requestMethod)

        }
    }

    "post events to Nakadi topics" in {
      val event = Event("http://test.zalando.net/my_type",
                        "ARTICLE:123456",
                         Map("tenant-id" -> "234567",
                             "flow-id" -> "123456789" ),
                         Map("greeting" -> "hello",
                             "target" -> "world"))


      val topic = "test-topic-1"
      val requestMethod = new HttpString("POST")
      val requestPath = s"/topics/$topic/events"
      val responseStatusCode = 201

      val builder = new NakadiTestService.Builder
      service = builder.withHost(HOST)
                       .withPort(PORT)
                       .withHandler(requestPath)
                       .withRequestMethod(requestMethod)
                       .withResponseContentType(MEDIA_TYPE)
                       .withResponseStatusCode(responseStatusCode)
                       .withResponsePayload("")
                       .build
      service.start()

      Await.result(
        klient.postEvent(topic, event),
        10 seconds
      ) match {
        case Some(error) => fail(s"an error occurred while posting event to topic $topic")
        case None => logger.debug("event post request was successful")
      }

      val request = performStandardRequestChecks(requestPath, requestMethod)
      val sentEvent = objectMapper.readValue(request.getRequestBody, classOf[Event])
      sentEvent should be(event)
    }

    "retreive partitions of a topic" in {
      val expectedPartitions = List(TopicPartition("111", "0", "0"), TopicPartition("222", "0", "1"))
      val expectedResponse = objectMapper.writeValueAsString(expectedPartitions)


      val topic = "test-topic-1"
      val requestMethod = new HttpString("GET")
      val requestPath = s"/topics/$topic/partitions"
      val responseStatusCode = 200

      val builder = new Builder()
      service = builder.withHost(HOST)
                       .withPort(PORT)
                       .withHandler(requestPath)
                       .withRequestMethod(requestMethod)
                       .withResponseContentType(MEDIA_TYPE)
                       .withResponseStatusCode(responseStatusCode)
                       .withResponsePayload(expectedResponse)
                       .build
      service.start()

      val receivedPartitions = Await.result(klient.getPartitions(topic), 10 seconds) match {
        case Left(error: String) => throw new RuntimeException(s"could not retrieve partitions: $error")
        case Right(partitions) => partitions
      }
      receivedPartitions should be(expectedPartitions)
      performStandardRequestChecks(requestPath, requestMethod)
    }

    "retrieve a particular partition" in {
      val expectedPartition = TopicPartition("111", "0", "0")
      val expectedResponse = objectMapper.writeValueAsString(expectedPartition)


      val partitionId = "111"
      val topic = "test-topic-1"
      val requestMethod = new HttpString("GET")
      val requestPath = s"/topics/$topic/partitions/" + partitionId
      val responseStatusCode: Int = 200

      val builder = new Builder()
      service = builder.withHost(HOST)
                       .withPort(PORT)
                       .withHandler(requestPath)
                       .withRequestMethod(requestMethod)
                       .withResponseContentType(MEDIA_TYPE)
                       .withResponseStatusCode(responseStatusCode)
                       .withResponsePayload(expectedResponse)
                       .build()
      service.start()

      val receivedPartition = Await.result(klient.getPartition(topic, partitionId), 10 seconds) match {
        case Left(error)  => throw new RuntimeException(s"could not retrieve partition: $error")
        case Right(receivedTopic) => receivedTopic
      }
      receivedPartition should be(expectedPartition)

      performStandardRequestChecks(requestPath, requestMethod)
    }


    "subscribe to topic" in {
      val partition = TopicPartition("p1", "0", "4")
      val partition2 = TopicPartition("p2", "1", "1")
      val partitions = List(partition, partition2)
      val partitionsAsString = objectMapper.writeValueAsString(partitions)

      val event = Event("type-1",
                        "ARTICLE:123456",
                        Map("tenant-id" -> "234567", "flow-id" -> "123456789"),
                        Map("greeting" -> "hello", "target" -> "world"))

      val streamEvent1 = SimpleStreamEvent(Cursor("p1", partition.newestAvailableOffset), List(event), List())

      val streamEvent1AsString = objectMapper.writeValueAsString(streamEvent1) + "\n"

      //--

      val event2 = Event("type-2",
                         "ARTICLE:123456",
                         Map("tenant-id" -> "234567", "flow-id" -> "123456789"),
                         Map("greeting" -> "hello", "target" -> "world"))

      val streamEvent2 = SimpleStreamEvent(Cursor("p2", partition2.newestAvailableOffset), List(event2), List())

      val streamEvent2AsString = objectMapper.writeValueAsString(streamEvent2) + "\n"

      //--

      val topic = "test-topic-1"
      val partitionsRequestPath = s"/topics/$topic/partitions"
      val partition1EventsRequestPath = s"/topics/$topic/partitions/p1/events"
      val partition2EventsRequestPath = s"/topics/$topic/partitions/p2/events"

      val httpMethod = new HttpString("GET")
      val statusCode = 200

      val builder = new Builder()
      service = builder.withHost(HOST)
                       .withPort(PORT)
                       .withHandler(partitionsRequestPath)
                       .withRequestMethod(httpMethod)
                       .withResponseContentType(MEDIA_TYPE)
                       .withResponseStatusCode(statusCode)
                       .withResponsePayload(partitionsAsString)
                       .and
                       .withHandler(partition1EventsRequestPath)
                       .withRequestMethod(httpMethod)
                       .withResponseContentType(MEDIA_TYPE)
                       .withResponseStatusCode(statusCode)
                       .withResponsePayload(streamEvent1AsString)
                       .and
                       .withHandler(partition2EventsRequestPath)
                       .withRequestMethod(httpMethod)
                       .withResponseContentType(MEDIA_TYPE)
                       .withResponseStatusCode(statusCode)
                       .withResponsePayload(streamEvent2AsString)
                       .build
      service.start()

      val listener = new TestListener
      Await.ready(
        klient.subscribeToTopic(topic, ListenParameters(Some("0")), listener, autoReconnect = true),
        5 seconds)

      Thread.sleep(PartitionReceiver.POLL_PARALLELISM * 1000L + 2000L)

      //-- check received events

      val receivedEvents = listener.receivedEvents.get
      receivedEvents should contain(event)
      receivedEvents should contain(event2)

      listener.onConnectionOpened should be > 0
      listener.onConnectionClosed should be > 0 // no long polling actitvated by test mock


      val collectedRequests = service.getCollectedRequests
      collectedRequests should have size 3

      //-- check header and query parameters

      performStandardRequestChecks(partitionsRequestPath, httpMethod)

      val request = performStandardRequestChecks(partition1EventsRequestPath, httpMethod)

      if(request.getRequestPath.contains(partition1EventsRequestPath)) {
        var queryParameters = request.getRequestQueryParameters
        checkQueryParameter(queryParameters, "start_from", partition.newestAvailableOffset)
        checkQueryParameter(queryParameters, "batch_limit", "1")
        checkQueryParameter(queryParameters, "stream_limit", "0")
        checkQueryParameter(queryParameters, "batch_flush_timeout", "5")

        val request2 = performStandardRequestChecks(partition2EventsRequestPath, httpMethod)
        queryParameters = request2.getRequestQueryParameters
        checkQueryParameter(queryParameters, "start_from", partition2.newestAvailableOffset)
        checkQueryParameter(queryParameters, "batch_limit", "1")
        checkQueryParameter(queryParameters, "stream_limit", "0")
        checkQueryParameter(queryParameters, "batch_flush_timeout", "5")
      }
      else {
        var queryParameters = request.getRequestQueryParameters
        checkQueryParameter(queryParameters, "start_from", partition2.newestAvailableOffset)
        checkQueryParameter(queryParameters, "batch_limit", "1")
        checkQueryParameter(queryParameters, "stream_limit", "0")
        checkQueryParameter(queryParameters, "batch_flush_timeout", "5")

        val request2 = performStandardRequestChecks(partition2EventsRequestPath, httpMethod)
        queryParameters = request2.getRequestQueryParameters
        checkQueryParameter(queryParameters, "start_from", partition.newestAvailableOffset)
        checkQueryParameter(queryParameters, "batch_limit", "1")
        checkQueryParameter(queryParameters, "stream_limit", "0")
        checkQueryParameter(queryParameters, "batch_flush_timeout", "5")
      }
    }

    "reconnect, if autoReconnect = true and stream was closed by Nakadi" in {
      val partition = TopicPartition("p1", "0", "4")
      val partition2 = TopicPartition("p2", "1", "1")
      val partitions = List(partition, partition2)
      val partitionsAsString = objectMapper.writeValueAsString(partitions)

      val event = Event("type-1",
        "ARTICLE:123456",
        Map("tenant-id" -> "234567", "flow-id" -> "123456789"),
        Map("greeting" -> "hello", "target" -> "world"))

      val streamEvent1 = SimpleStreamEvent(Cursor("p1", "0"), List(event), List())

      val streamEvent1AsString = objectMapper.writeValueAsString(streamEvent1) + "\n"

      val topic = "test-topic-1"
      val partitionsRequestPath = s"/topics/$topic/partitions"
      val partition1EventsRequestPath = s"/topics/$topic/partitions/p1/events"

      val httpMethod = new HttpString("GET")
      val statusCode = 200

      startServiceForEventListening()

      val listener = new TestListener

      Await.ready(
        klient.subscribeToTopic(topic, ListenParameters(Some("0")), listener, autoReconnect = true),
        5 seconds)

      Thread.sleep(PartitionReceiver.POLL_PARALLELISM * 1000L + 2000L)

      service.stop()
      service = null
      Thread.sleep(1000L)

      startServiceForEventListening()

      Thread.sleep(2000L)

      listener.onConnectionOpened should be > 1
      listener.onConnectionClosed should be > 1
      listener.onConnectionFailed should be > 0
    }

    "unsubscribe listener" in {
      val topic = "test-topic-1"

      startServiceForEventListening()

      val listener = new TestListener

      Await.ready(
        klient.subscribeToTopic(topic, ListenParameters(Some("0")), listener, autoReconnect = true),
        5 seconds)

      Await.ready(
        klient.subscribeToTopic(topic, ListenParameters(Some("0")), listener, autoReconnect = true),
        5 seconds)

      Thread.sleep(1000L)

      val receivedEvents = listener.receivedEvents

      klient.unsubscribeTopic(topic, listener)

      service.stop()

      Thread.sleep(1000L)

      startServiceForEventListening()

      Thread.sleep(1000L)

      receivedEvents should be(listener.receivedEvents)
    }
  }


  def startServiceForEventListening() = {

    val partition = TopicPartition("p1", "0", "4")
    val partition2 = TopicPartition("p2", "1", "1")
    val partitions = List(partition, partition2)
    val partitionsAsString = objectMapper.writeValueAsString(partitions)

    val event = Event("type-1",
      "ARTICLE:123456",
      Map("tenant-id" -> "234567", "flow-id" -> "123456789"),
      Map("greeting" -> "hello", "target" -> "world"))

    val streamEvent1 = SimpleStreamEvent(Cursor("p1", "0"), List(event), List())

    val streamEvent1AsString = objectMapper.writeValueAsString(streamEvent1) + "\n"

    val topic = "test-topic-1"
    val partitionsRequestPath = s"/topics/$topic/partitions"
    val partition1EventsRequestPath = s"/topics/$topic/partitions/p1/events"

    val httpMethod = new HttpString("GET")
    val statusCode = 200

    val builder = new Builder()
    service = builder.withHost(HOST)
      .withPort(PORT)
      .withHandler(partitionsRequestPath)
      .withRequestMethod(httpMethod)
      .withResponseContentType(MEDIA_TYPE)
      .withResponseStatusCode(statusCode)
      .withResponsePayload(partitionsAsString)
      .and
      .withHandler(partition1EventsRequestPath)
      .withRequestMethod(httpMethod)
      .withResponseContentType(MEDIA_TYPE)
      .withResponseStatusCode(statusCode)
      .withResponsePayload(streamEvent1AsString)
      .build
    service.start()
  }
}
