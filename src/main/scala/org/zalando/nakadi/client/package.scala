package org.zalando.nakadi


package object client {

  val URI_METRICS = "/metrics"
  val URI_TOPICS = "/topics"
  val URI_EVENT_POST = "/topics/%s/events"
  val URI_PARTITIONS = "/topics/%s/partitions"
  val URI_PARTITION = "/topics/%s/partitions/%s"
  val URI_EVENTS_ON_PARTITION = "/topics/%s/partitions/%s/events"
  val URI_EVENT_LISTENING = "/topics/%s/partitions/%s/events?%s&batch_limit=%s&batch_flush_timeout=%s&stream_limit=%s"
  val DEFAULT_BATCH_FLUSH_TIMEOUT_IN_SECONDS = 5
  val DEFAULT_BATCH_LIMIT = 1
  val DEFAULT_STREAM_LIMIT = 0
}
