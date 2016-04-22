package org.zalando.nakadi

package object client {
  
  
  val URI_METRICS = "/metrics"
  
  /*Events*/
  val URI_EVENT_TYPES = "/event-types"
  val URI_EVENT_TYPE_BY_NAME = "/event-types/%s"
  val URI_EVENTS_OF_EVENT_TYPE = "/event-types/%s/events"

  /*Partitions*/
  val URI_PARTITIONS_BY_EVENT_TYPE = "/event-types/%s/partitions"
  val URI_PARTITION_BY_EVENT_TYPE_AND_ID = "/event-types/%s/partitions/%s"

  /*Strategies*/
  val URI_VALIDATION_STRATEGIES = "/registry/validation-strategies"
  val URI_ENRICHMENT_STRATEGIES = "/registry/enrichment-strategies"
  val URI_PARTITIONING_STRATEGIES = "/registry/partition-strategies"

}