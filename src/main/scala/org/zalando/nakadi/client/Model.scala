package org.zalando.nakadi.client

import scala.collection.JavaConversions
import spray.json.{JsValue, JsonFormat, DefaultJsonProtocol}

case class Cursor(partition: String, offset: String)

case class Event(eventType: String, orderingKey: String, metadata: Map[String, Any], body: Map[String,Any] /*was: AnyRef*/) {
  def this(eventType: String, orderingKey: String, metadata: java.util.Map[String, Any], body: Map[String,Any] /*was: AnyRef*/) =
    this(eventType, orderingKey, JavaConversions.mapAsScalaMap(metadata).toMap, body)
}

case class Topic(name: String)
case class TopologyItem (clientId: String, partitions: List[String])
case class TopicPartition(partitionId: String, oldestAvailableOffset: String, newestAvailableOffset: String)
case class SimpleStreamEvent(cursor: Cursor, events: List[Event], topology: List[TopologyItem])

/*
* JSON conversions of case classes (spray.json)
*/
object MyJsonProtocol extends DefaultJsonProtocol {
  import tools.AnyJsonFormat._

  /*** remove
  implicit object FmtAnyRef extends JsonFormat[AnyRef] {
    override
    def read(x:JsValue) = throw new RuntimeException( s"Not ready to read JSON->AnyRef: $x")

    override
    def write(x:AnyRef) = throw new RuntimeException( s"Not ready to write AnyRef->JSON: $x")
  } ***/

  implicit private val fmtCursor = jsonFormat2(Cursor)
  implicit val fmtEvent = jsonFormat4(Event)    // needs the above AnyRef conversions
  implicit val fmtTopic = jsonFormat1(Topic)
  implicit private val fmtTopologyItem = jsonFormat2(TopologyItem)    // needed by the 'SimpleStreamEvent' conversion below
  implicit val fmtTopicPartition = jsonFormat3(TopicPartition)
  implicit val fmtSimpleStreamEvent = jsonFormat3(SimpleStreamEvent)
}
