package org.zalando.nakadi.client

import java.net.URI
import java.util.concurrent.atomic.AtomicReference

import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, PropertyNamingStrategy, SerializationFeature}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.google.common.collect.Iterators
import com.typesafe.scalalogging.LazyLogging
import io.undertow.util.{Headers, HttpString}
import org.scalatest.concurrent.PatienceConfiguration.{Timeout => ScalaTestTimeout}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import org.zalando.nakadi.client.actor.PartitionReceiver
import org.zalando.nakadi.client.utils.NakadiTestService.Builder
import org.zalando.nakadi.client.utils.{NakadiTestService, Request => NakadiRequest}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.implicitConversions

class MultiTopicKlientSpec extends WordSpec with Matchers with BeforeAndAfterEach with LazyLogging with ScalaFutures {

  import MultiTopicKlientSpec._

  var klient: Klient = null
  var service: NakadiTestService = null

  override def beforeEach() = {
    klient = KlientBuilder()
      .withEndpoint(new URI(HOST)) // TODO if no scheme is specified, the library utilized by the client breaks with a NullpointeException...
      .withPort(PORT)
      .withTokenProvider(() => TOKEN)
      .build()
  }

  override def afterEach(): Unit = {
    klient.stop()
    klient = null
    if (Option(service).isDefined) {
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

    if (request.getRequestMethod.equals(new HttpString("GET"))) {
      headerMap.get(Headers.ACCEPT).getFirst shouldBe MEDIA_TYPE
    } else {
      headerMap.get(Headers.CONTENT_TYPE).getFirst shouldBe MEDIA_TYPE
    }

    headerMap.get(Headers.AUTHORIZATION).getFirst shouldBe s"Bearer $TOKEN"

    request
  }

  "A Klient" must {
    "subscribe for two topics and get events for them" in {

      val topic1 = Topic("test-topic-1")
      val topic2 = Topic("test-topic-2")

      val event1 = Event(eventType = "type-1", orderingKey = topic1.name, metadata = Map(), body = Map())
      val event2 = Event(eventType = "type-2", orderingKey = topic2.name, metadata = Map(), body = Map())
      val event3 = Event(eventType = "type-3", orderingKey = topic2.name, metadata = Map(), body = Map())

      val partition1 = TopicPartition("p1", "0", "1")
      val partition2 = TopicPartition("p2", "0", "1")
      val partition3 = TopicPartition("p3", "0", "1")
      val partitions1 = Map(partition1 -> SimpleStreamEvent(Cursor(partition1.partitionId, partition1.oldestAvailableOffset), List(event1), List()))
      val partitions2 = Map(partition2 -> SimpleStreamEvent(Cursor(partition2.partitionId, partition2.oldestAvailableOffset), List(event2), List()),
        partition3 -> SimpleStreamEvent(Cursor(partition3.partitionId, partition3.oldestAvailableOffset), List(event3), List()))
      val listener1 = new TestListener("listener-1")
      val listener2 = new TestListener("listener-2")

      val builder = new Builder
      service = builder.withHost(HOST)
        .withPort(PORT)
        .withPostEvent(topic1.name)
        .and
        .withSubscirbeToTopic(topic1.name, partitions1)
        .and
        .withPostEvent(topic2.name)
        .and
        .withSubscirbeToTopic(topic2.name, partitions2)
        .build
      service.start()


      Await.ready(
        klient.subscribeToTopic(topic1.name, ListenParameters(Some(partition1.oldestAvailableOffset)), listener1, autoReconnect = true),
        5.seconds
      )

      Await.ready(
        klient.subscribeToTopic(topic2.name, ListenParameters(Some(partition2.oldestAvailableOffset)), listener2, autoReconnect = true),
        5.seconds
      )

      whenReady(klient.postEvent(topic1.name, event1), 10.seconds) {
        case Left(error) => fail(s"an error occurred while posting event to topic ${topic1.name}")
        case Right(_) => {
          logger.debug(s"event ${event1.eventType} post to topic ${topic1.name} request was successful")
          Thread.sleep(PartitionReceiver.NO_LISTENER_RECONNECT_DELAY_IN_S * 1000L + 2000L)
          val receivedEvents = listener1.receivedEvents.get()
          receivedEvents should contain(event1)
        }
      }

      whenReady(klient.postEvent(topic2.name, event2), 10.seconds) {
        case Left(error) => fail(s"an error occurred while posting event to topic ${topic2.name}")
        case Right(_) => {
          logger.debug(s"event ${event2.eventType} post to topic ${topic2.name} request was successful")
          Thread.sleep(PartitionReceiver.NO_LISTENER_RECONNECT_DELAY_IN_S * 1000L + 2000L)
          val receivedEvents = listener2.receivedEvents.get()
          receivedEvents should contain(event2)
          receivedEvents should contain(event3)
        }
      }
    }
  }
}

object MultiTopicKlientSpec extends LazyLogging {
  lazy val objectMapper = new ObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES)
    .registerModule(new DefaultScalaModule)

  val MEDIA_TYPE = "application/json"
  val TOKEN = "<OAUTH Token>"
  val HOST = "localhost"
  val PORT = 8081

  implicit class RichBuilder(builder: Builder) {
    def withPostEvent(topic: String) = {
      val requestMethod = new HttpString("POST")
      val requestPath = s"/topics/$topic/events"
      val responseStatusCode = 201

      builder.withHandler(requestPath)
        .withRequestMethod(requestMethod)
        .withResponseContentType(MEDIA_TYPE)
        .withResponseStatusCode(responseStatusCode)
        .withResponsePayload("")
    }

    def withSubscirbeToTopic(topic: String, partitions: Map[TopicPartition, SimpleStreamEvent]) = {
      val httpMethod = new HttpString("GET")
      val statusCode = 200
      val partitionsRequestPath = s"/topics/$topic/partitions"

      val partitionsListHandler = builder.withHandler(partitionsRequestPath)
        .withRequestMethod(httpMethod)
        .withResponseContentType(MEDIA_TYPE)
        .withResponseStatusCode(statusCode)
        .withResponsePayload(objectMapper.writeValueAsString(partitions.keys.toList))

      partitions.foldLeft(partitionsListHandler)((handler, partition) => {
        val partition1EventsRequestPath = s"/topics/$topic/partitions/${partition._1.partitionId}/events"
        handler.and
          .withHandler(partition1EventsRequestPath)
          .withRequestMethod(httpMethod)
          .withResponseContentType(MEDIA_TYPE)
          .withResponseStatusCode(statusCode)
          .withResponsePayload(objectMapper.writeValueAsString(partition._2))
      })
    }
  }

  private implicit def conv(x: FiniteDuration): ScalaTestTimeout = {
    new ScalaTestTimeout(x)
  }


  private class TestListener(override val id: String) extends Listener {
    var receivedEvents = new AtomicReference[List[Event]](List())
    var onConnectionClosed, onConnectionOpened, onConnectionFailed = 0

    override def onReceive(topic: String, partition: String, cursor: Cursor, event: Event): Unit = {

      var old = List[Event]()
      do {
        old = receivedEvents.get()
      }
      while (!receivedEvents.compareAndSet(old, old ++ List(event))) // Q: what is this doing? AKa280116
      logger.debug(s"Event ${event.eventType} has been received by topic $topic")
    }

    override def onConnectionClosed(topic: String, partition: String, lastCursor: Option[Cursor]): Unit = onConnectionClosed += 1

    override def onConnectionOpened(topic: String, partition: String): Unit = onConnectionOpened += 1

    override def onConnectionFailed(topic: String, partition: String, status: Int, error: String): Unit = onConnectionFailed += 1
  }

}
