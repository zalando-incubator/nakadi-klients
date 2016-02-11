package org.zalando.nakadi.client

import java.net.URI
import java.util
import java.util.concurrent.atomic.AtomicReference

import com.google.common.collect.Iterators
import com.typesafe.scalalogging.LazyLogging
import io.undertow.util.{HeaderValues, HttpString, Headers}
import org.scalatest.concurrent.PatienceConfiguration.{Timeout => ScalaTestTimeout}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest._
import org.zalando.nakadi.client.actor.PartitionReceiver
import org.zalando.nakadi.client.utils.NakadiTestService
import org.zalando.nakadi.client.utils.NakadiTestService.Builder
import org.zalando.nakadi.client.utils.{Request => NakadiRequest}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.language.implicitConversions

import spray.json._
import org.zalando.nakadi.client.tools.AnyJsonFormat._
import MyJsonProtocol._

class KlientSpec extends WordSpec with Matchers with BeforeAndAfterEach with LazyLogging with ScalaFutures {
  import KlientSpec._

  var klient: Klient = null
  var service: NakadiTestService = null

  override  def beforeEach() = {
    klient = KlientBuilder()
      .withEndpoint(new URI(HOST)) // TODO if no scheme is specified, the library utilized by the client breaks with a NullpointeException...
      .withPort(PORT)
      .withTokenProvider(() => TOKEN)
      .build()
  }

  override def afterEach(): Unit = {
    klient.stop()
    klient = null

    if(Option(service).isDefined) {
      service.stop()
      service = null
    }
  }

  private def performStandardRequestChecks(expectedRequestPath: String, expectedRequestMethod: HttpString): NakadiRequest = {
    val collectedRequestsMap = service.getCollectedRequests
    val requests = collectedRequestsMap.get(expectedRequestPath)
    requests should not be null

    val request = Iterators.getLast(requests.iterator)
    request.getRequestPath shouldBe expectedRequestPath
    request.getRequestMethod shouldBe expectedRequestMethod

    val headerMap = request.getRequestHeaders

    if(request.getRequestMethod.equals(new HttpString("GET"))){
      headerMap.get(Headers.ACCEPT).getFirst shouldBe MEDIA_TYPE
    } else {
      headerMap.get(Headers.CONTENT_TYPE).getFirst shouldBe MEDIA_TYPE
    }

    headerMap.get(Headers.AUTHORIZATION).getFirst shouldBe s"Bearer $TOKEN"

    request
  }

  "A Klient" must {
    "retrieve Nakadi metrics" in {
      import DefaultJsonProtocol._

      val expectedResponse = Map(
          "post_event" -> Map(
            "calls_per_second" -> 0.005,
            "count" -> 5,
            "status_codes" -> Map(
              "201" -> 5
            )
          ),
          "get_metrics" -> Map(
            "calls_per_second" -> 0.001,
            "count" -> 1,
            "status_codes" -> Map(
              "401" -> 1
            )
          )
      )
      val expectedResponseAsString = expectedResponse.toJson.prettyPrint

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

      whenReady( klient.getMetrics, 5 seconds) {
        case Left(error) => fail(s"could not retrieve metrics: $error")
        case Right(metrics) => logger.debug(s"metrics => $metrics")
                               performStandardRequestChecks(requestPath, requestMethod)
      }
    }

    "retrieve Nakadi topics" in {
      import DefaultJsonProtocol._

      val expectedResponse = List(Topic("test-topic-1"), Topic("test-topic-2"))
      val expectedResponseAsString = expectedResponse.toJson.prettyPrint

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

      whenReady( klient.getTopics, 10 seconds ) {
        case Left(error) => fail(s"could not retrieve topics: $error")
        case Right(topics) =>
          logger.info(s"topics => $topics")
          topics shouldBe expectedResponse
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

      val builder = new Builder
      service = builder.withHost(HOST)
                       .withPort(PORT)
                       .withHandler(requestPath)
                       .withRequestMethod(requestMethod)
                       .withResponseContentType(MEDIA_TYPE)
                       .withResponseStatusCode(responseStatusCode)
                       .withResponsePayload("")
                       .build
      service.start()

      whenReady( klient.postEvent(topic, event), 10 seconds ) {
        case Left(error) => fail(s"an error occurred while posting event to topic $topic")
        case Right(_) => logger.debug("event post request was successful")
      }

      // Not exactly clear to me what's happening here; how the 'objectMapper' is connected...? AKa280116

      val request = performStandardRequestChecks(requestPath, requestMethod)
      val sentEvent = JsonParser(request.getRequestBody).convertTo[Event]
      sentEvent shouldBe event
    }

    "retreive partitions of a topic" in {
      import DefaultJsonProtocol._

      val expectedPartitions = List(TopicPartition("111", "0", "0"), TopicPartition("222", "0", "1"))
      val expectedResponse = expectedPartitions.toJson.prettyPrint

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

      val receivedPartitions = whenReady( klient.getPartitions(topic), 10 seconds) {
        case Left(error: String) => throw new RuntimeException(s"could not retrieve partitions: $error")
        case Right(partitions) => partitions
      }
      receivedPartitions shouldBe expectedPartitions
      performStandardRequestChecks(requestPath, requestMethod)
    }

    "retrieve a particular partition" in {
      val expectedPartition = TopicPartition("111", "0", "0")
      val expectedResponse = expectedPartition.toJson.prettyPrint

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

      val receivedPartition = whenReady(klient.getPartition(topic, partitionId), 10 seconds) {
        case Left(error)  => throw new RuntimeException(s"could not retrieve partition: $error")
        case Right(receivedTopic) => receivedTopic
      }
      receivedPartition shouldBe expectedPartition

      performStandardRequestChecks(requestPath, requestMethod)
    }


    "subscribe to topic" in {
      import DefaultJsonProtocol._

      val partition = TopicPartition("p1", "0", "4")
      val partition2 = TopicPartition("p2", "1", "1")
      val partitions = List(partition, partition2)
      val partitionsAsString = partitions.toJson.toString

      val event = Event("type-1",
                        "ARTICLE:123456",
                        Map("tenant-id" -> "234567", "flow-id" -> "123456789"),
                        Map("greeting" -> "hello", "target" -> "world"))

      val streamEvent1 = SimpleStreamEvent(Cursor("p1", partition.newestAvailableOffset), List(event), List())

      val streamEvent1AsString = streamEvent1.toJson.toString + "\n"

      //--

      val event2 = Event("type-2",
                         "ARTICLE:123456",
                         Map("tenant-id" -> "234567", "flow-id" -> "123456789"),
                         Map("greeting" -> "hello", "target" -> "world"))

      val streamEvent2 = SimpleStreamEvent(Cursor("p2", partition2.newestAvailableOffset), List(event2), List())

      val streamEvent2AsString = streamEvent2.toJson.toString + "\n"

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

      Thread.sleep(PartitionReceiver.NO_LISTENER_RECONNECT_DELAY_IN_S * 1000L + 2000L)

      //-- check received events

      val receivedEvents = listener.receivedEvents.get
      receivedEvents should contain(event)
      receivedEvents should contain(event2)

      listener.onConnectionOpened should be > 0
      listener.onConnectionClosed should be > 0 // no long polling activated by test mock


      val collectedRequests = service.getCollectedRequests
      collectedRequests.size shouldBe 3

      //-- check header and query parameters

      performStandardRequestChecks(partitionsRequestPath, httpMethod)

      val request = performStandardRequestChecks(partition1EventsRequestPath, httpMethod)

      // Q: what is the purpose of this logic?

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
      import DefaultJsonProtocol._

      val partition = TopicPartition("p1", "0", "4")
      val partition2 = TopicPartition("p2", "1", "1")
      val partitions = List(partition, partition2)
      val partitionsAsString = partitions.toJson.prettyPrint

      val event = Event("type-1",
        "ARTICLE:123456",
        Map("tenant-id" -> "234567", "flow-id" -> "123456789"),
        Map("greeting" -> "hello", "target" -> "world"))

      val streamEvent1 = SimpleStreamEvent(Cursor("p1", "0"), List(event), List())

      val streamEvent1AsString = streamEvent1.toJson.toString + "\n"

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

      Thread.sleep(PartitionReceiver.NO_LISTENER_RECONNECT_DELAY_IN_S * 1000L + 2000L)

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

      receivedEvents shouldBe listener.receivedEvents
    }
  }


  def startServiceForEventListening() = {
    import DefaultJsonProtocol._

    val partition = TopicPartition("p1", "0", "4")
    val partition2 = TopicPartition("p2", "1", "1")
    val partitions = List(partition, partition2)
    val partitionsAsString = partitions.toJson.prettyPrint

    val event = Event("type-1",
      "ARTICLE:123456",
      Map("tenant-id" -> "234567", "flow-id" -> "123456789"),
      Map("greeting" -> "hello", "target" -> "world"))

    val streamEvent1 = SimpleStreamEvent(Cursor("p1", "0"), List(event), List())

    val streamEvent1AsString = streamEvent1.toJson.toString + "\n"

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

object KlientSpec extends WordSpecLike /*info*/ with ShouldMatchers {

  /*** tbd. Must get this back AKa110216
  lazy val objectMapper = new ObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES)
    .registerModule(new DefaultScalaModule)
  ***/

  val MEDIA_TYPE = "application/json"
  val TOKEN = "<OAUTH Token>"
  val HOST = "localhost"
  val PORT = 8081

  private
  class TestListener extends Listener {
    var receivedEvents = new AtomicReference[List[Event]](List())
    var onConnectionClosed, onConnectionOpened, onConnectionFailed = 0

    override def id = "test"

    override def onReceive(topic: String, partition: String, cursor: Cursor, event: Event): Unit = {

      var old = List[Event]()
      do {
        old = receivedEvents.get()
      }
      while (!receivedEvents.compareAndSet(old, old ++ List(event)))    // Q: what is this doing? AKa280116
    }

    override def onConnectionClosed(topic: String, partition: String, lastCursor: Option[Cursor]): Unit = onConnectionClosed += 1

    override def onConnectionOpened(topic: String, partition: String): Unit = onConnectionOpened += 1

    override def onConnectionFailed(topic: String, partition: String, status: Int, error: String): Unit = onConnectionFailed += 1
  }

  private
  def checkQueryParameter(queryParameters: java.util.Map[String, util.Deque[String]], paramaterName: String, expectedValue: String) {
    val paramDeque = queryParameters.get(paramaterName)
    paramDeque should not be null
    paramDeque.getFirst shouldBe expectedValue
  }

  private
  implicit def conv(x: FiniteDuration): ScalaTestTimeout = { new ScalaTestTimeout(x) }
}
