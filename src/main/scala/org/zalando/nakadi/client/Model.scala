package org.zalando.nakadi.client

import scala.collection.JavaConversions
import spray.json.DefaultJsonProtocol

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

  implicit private val fmtCursor = jsonFormat2(Cursor)                // needed by 'SimpleStreamEvent' conversion
  implicit val fmtEvent = jsonFormat4(Event)
  implicit val fmtTopic = jsonFormat1(Topic)
  implicit private val fmtTopologyItem = jsonFormat2(TopologyItem)    // needed by 'SimpleStreamEvent' conversion
  implicit val fmtTopicPartition = jsonFormat3(TopicPartition)
  implicit val fmtSimpleStreamEvent = jsonFormat3(SimpleStreamEvent)
}
