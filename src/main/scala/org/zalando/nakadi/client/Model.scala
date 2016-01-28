package org.zalando.nakadi.client

case class Cursor(partition: String, offset: String)
case class Event(eventType: String, orderingKey: String, metadata: Map[String, Any], body: AnyRef)
case class Topic(name: String)
case class TopologyItem (clientId: String, partitions: List[String])
case class TopicPartition(partitionId: String, oldestAvailableOffset: String, newestAvailableOffset: String)
case class SimpleStreamEvent(cursor: Cursor, events: List[Event], topology: List[TopologyItem])