package org.zalando.nakadi.client

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Random

import org.joda.time.DateTime
import org.zalando.nakadi.client.model.Event
import org.zalando.nakadi.client.model.EventMetadata
import org.zalando.nakadi.client.model.EventMetadata
import org.zalando.nakadi.client.model.EventType
import org.zalando.nakadi.client.model.EventTypeCategory
import org.zalando.nakadi.client.model.EventTypeSchema
import org.zalando.nakadi.client.model.JacksonJsonMarshaller
import org.zalando.nakadi.client.model.PartitionStrategy
import org.zalando.nakadi.client.model.SchemaType

trait ClientFactory {
  val host = "nakadi-sandbox.aruha."
  val OAuth2Token = () => ""
  val port = 443
  val connection = Connection.newConnection(host, port, OAuth2Token, true, false)
  val client = new ClientImpl(connection, "UTF-8")
  

}

case class EventActions(client: Client) extends JacksonJsonMarshaller {

  def create[T](name: String, event: Seq[T])(implicit ser: NakadiSerializer[Seq[T]]) = {
    client.newEvents[T](name, event)
  }
}

case class EventTypesActions(client: Client) extends JacksonJsonMarshaller {

  def create(event: EventType)(implicit ser: NakadiSerializer[EventType]) = {
    executeCall(client.newEventType(event))
  }
  def update(event: EventType)(implicit ser: NakadiSerializer[EventType]) = {
    executeCall(client.updateEventType(event.name, event))
  }
  def get(name: String)(implicit ser: NakadiDeserializer[Option[EventType]]) = {
    executeCall(client.eventType(name))
  }
  def getAll()(implicit ser: NakadiDeserializer[Option[Seq[EventType]]]) = {
    executeCall(client.eventTypes())
  }
  def delete(name: String) = {
    executeCall(client.deleteEventType(name))
  }

  private def executeCall[T](call: => Future[T]): T = {
    Await.result(call, 10.second)
  }
}

trait ModelFactory {
  val x = Random.alphanumeric
  def paritionKeyFields() = List("order_number")
  def schemaDefinition() = """{ "properties": { "order_number": { "type": "string" } } }"""
  def eventTypeSchema() = new EventTypeSchema(SchemaType.JSON, schemaDefinition)

  def createUniqueEventType(): EventType = {
    //Unique events mean unique name
    new EventType("test-client-integration-event-" + Random.nextInt() + "-" + Random.nextInt() + Random.nextInt(), //
      "laas-team", //
      EventTypeCategory.UNDEFINED, None, Nil, //
      Some(PartitionStrategy.RANDOM), Option(eventTypeSchema), //
      None, Option(paritionKeyFields), None)
  }
  def createEventMetadata(): EventMetadata = {
    val length = 5
    val eid = java.util.UUID.randomUUID.toString
    val occurredAt = new DateTime().toString()
    new EventMetadata(eid, None, occurredAt, None, Nil, None, None)
  }
}