package org.zalando.nakadi.client.utils

object Uri {

  final def URI_METRICS = "/metrics"

  /*Events*/
  final def URI_EVENT_TYPES        = "/event-types"
  final def URI_EVENT_TYPE_BY_NAME = "/event-types/%s"
  final def getEventTypeByName(eventTypeName: String) =
    s"/event-types/$eventTypeName"
  final def URI_EVENTS_OF_EVENT_TYPE() =
    "/event-types/%s/events"
  final def getEventStreamingUri(eventTypeName: String) =
    s"/event-types/$eventTypeName/events"

  /*Partitions*/
  final def URI_PARTITIONS_BY_EVENT_TYPE =
    "/event-types/%s/partitions"
  final def getPartitions(eventTypeName: String) =
    s"/event-types/$eventTypeName/partitions"

  /*Strategies*/
  final def URI_VALIDATION_STRATEGIES =
    "/registry/validation-strategies"
  final def URI_ENRICHMENT_STRATEGIES =
    "/registry/enrichment-strategies"
  final def URI_PARTITIONING_STRATEGIES =
    "/registry/partition-strategies"

  /*High Level API*/
  final def URI_SUBSCRIPTION_TO_EVENT_STREAM(subscriptionId: String) =
    s"/subscriptions/$subscriptionId/events"

  val URI_SUBSCRIPTION = "/subscriptions/"

  final def URI_SUBSCRIPTION_CURSOR_COMMIT(subscriptionId: String) =
    s"/subscriptions/$subscriptionId/cursors"
}
