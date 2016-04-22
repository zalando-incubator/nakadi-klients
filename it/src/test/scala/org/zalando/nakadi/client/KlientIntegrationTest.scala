package org.zalando.nakadi.client.config

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import org.scalatest.Matchers
import org.scalatest.WordSpec
import org.zalando.nakadi.client.Client._
import org.zalando.nakadi.client.model._
import spray.json._
import com.fasterxml.jackson.core.`type`.TypeReference
import org.zalando.nakadi.client.ClientFactory
import org.zalando.nakadi.client.ModelFactory
import scala.Right
class KlientIntegrationTest extends WordSpec with Matchers with JacksonJsonMarshaller with ModelFactory with ClientFactory {
  "Nakadi Client" should {
    "parse multiple PartitionResolutionStrategy" in {
      val Right(result) = executeCall(client.partitionStrategies())
      result.size should be > 0
    }

    //TODO: Change it when this endpoint is implemented by nakadi
    "parse exsiting validationStrategies" in {
      val result = executeCall(client.validationStrategies())
      result shouldBe Right(None)
    }
    //TODO: Change it when this endpoint is implemented by nakadi
    "parse exsiting enrishment-strategies" in {
      val result = executeCall(client.enrichmentStrategies())
      result shouldBe Right(None)
    }
    //TODO: Change when all events are valid
    "parse existing eventTypes" in {
      val Right(result) = executeCall(client.eventTypes())
      result.size should be > 0
    }
    "create a new eventType" in {
      val eventType = createUniqueEventType()
      executeCall(client.newEventType(eventType)) shouldBe None
    }
    "get EventType" in {
      val eventType = createUniqueEventType()
      executeCall(client.newEventType(eventType)) shouldBe None
      executeCall(client.eventType(eventType.name)) shouldBe Right(Some(eventType))

    }
    "delete EventType" in {
      val eventType = createUniqueEventType()
      executeCall(client.newEventType(eventType)) shouldBe None
      executeCall(client.deleteEventType(eventType.name)) shouldBe None
    }
    "Create the event itself" in {
      import spray.json._
      //Matches the one defined in the schema of

      case class EventExample(orderNumber: String, metadata: Option[EventMetadata]) extends Event
      implicit val eventExample: TypeReference[Seq[EventExample]] = new TypeReference[Seq[EventExample]] {}

      val event = new EventExample("22301982837", Some(createEventMetadata()))
      val eventType = createUniqueEventType()
      executeCall(client.newEventType(eventType)) shouldBe None
      executeCall(client.newEvents[EventExample](eventType.name, List(event))) shouldBe None
    }
  }
  private def assertIsNotImplementedYet[T](input: Either[ClientError, Option[List[T]]]) = {
    input match {
      case Left(error) => error.status shouldBe Some(404)
      case Right(result) =>
        println(" #### " + result)
        fail
    }
  }
  private def executeCall[T](call: => Future[T]): T = {
    Await.result(call, 10.second)
  }
}



