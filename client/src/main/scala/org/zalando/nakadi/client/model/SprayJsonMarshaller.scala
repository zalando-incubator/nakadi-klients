package org.zalando.nakadi.client.model

import org.zalando.nakadi.client.utils.ParseHelper
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.unmarshalling.Unmarshaller
import spray.json._
import spray.json.DefaultJsonProtocol
import spray.json.JsValue
import spray.json.JsonFormat
import spray.json.JsonReader
import spray.json.JsonWriter
import spray.json.pimpAny
import spray.json.pimpString
import org.zalando.nakadi.client.NakadiSerializer
import org.zalando.nakadi.client.NakadiDeserializer

//trait SprayJsonMarshaller extends SprayJsonSupport with DefaultJsonProtocol {
//  import ParseHelper._
//  case class Test(subjectDescription: String)
//  implicit val testFormatter = jsonFormat1(Test)
//  //Enums
//  implicit val dataOperationEnumFormatter = jsonEnumFormat(DataOperation)
//  implicit val eventTypeCategoryEnumFormatter = jsonEnumFormat(EventTypeCategory)
//  implicit val batchItemStepjsonEnumFormat = jsonEnumFormat(BatchItemStep)
//  implicit val BatchItemPublishingStatusEnumFormat = jsonEnumFormat(BatchItemPublishingStatus)
//  implicit val SchemaTypeEnumFormat = jsonEnumFormat(SchemaType)
//
//  implicit val problemFormatter = jsonFormat(Problem, "problem_type", "title", "status", "detail", "instance")
//  implicit val metricsFormatter = jsonFormat1(Metrics)
//  implicit val partitionFormatter = jsonFormat(Partition, "partition", "oldest_available_offset", "newest_available_offset")
//  implicit val cursorFormatter = jsonFormat2(Cursor)
//  implicit val partitionResolutionStrategyFormatter = jsonFormat2(PartitionResolutionStrategy)
//  implicit val eventTypeSchemaFormatter = jsonFormat(EventTypeSchema, "type", "schema")
//  implicit val eventTypeStatisticsFormatter = jsonFormat(EventTypeStatistics, "expected_write_rate", "message_size", "read_parallelism", "write_parallelism")
//  implicit val eventValidationStrategyFormatter = jsonFormat2(EventValidationStrategy)
//  implicit val eventTypeFormatter = jsonFormat(EventType, "name", "owning_application", "category", "validation_strategies", "enrichment_strategies", "partition_strategy", "schema", "partition_key_fields", "partitioning_key_fields", "statistics")
//  implicit val eventMetadataFormatter = jsonFormat(EventMetadata, "eid", "event_type", "occurred_at", "received_at", "parent_eids", "flow_id", "partition")
//  implicit val eventEnrichmentStrategyFormatter = jsonFormat2(EventEnrichmentStrategy)
//  implicit def dataChangeEventFormatter[A: JsonFormat] = jsonFormat(DataChangeEvent.apply[A],"data","data_type","data_operation","metadata")
//  
//  implicit object BusinessJsonFormat extends RootJsonFormat[BusinessEvent] {
//    def write(a: BusinessEvent) = a match {
//      case p: BusinessEvent => p.toJson
//    }
//    def read(json: JsValue) = json.asJsObject.fields("label") match {
//      case JsString("business")     => json.convertTo[BusinessEvent]
//      case JsString("change-event") => json.convertTo[DataChangeEvent]
//      case _ =>
//        throw new IllegalStateException("Couldn't parse what seems to be an Event: %s".format(json))
//    }
//  }
//  
//  
//  implicit object EventJsonFormat extends RootJsonFormat[Event] {
//    def write(a: Event) = a match {
//      case p: BusinessEvent => p.toJson
//    }
//    def read(json: JsValue) = json.asJsObject.fields("label") match {
//      case JsString("business")     => json.convertTo[BusinessEvent]
//      case JsString("change-event") => json.convertTo[DataChangeEvent]
//      case _ =>
//        throw new IllegalStateException("Couldn't parse what seems to be an Event: %s".format(json))
//    }
//  }
//  
//  implicit val dataChangeEventFormatter = jsonFormat4(DataChangeEvent.apply)
//   
//  
//  implicit def eventFormatter[T <: Event](implicit writer: JsonWriter[T])= writer.write(obj)
//
//  implicit val eventStreamBatchFormatter = jsonFormat2(EventStreamBatch)
//  implicit val batchItemResponseFormatter = jsonFormat(BatchItemResponse, "eid", "publishing_status", "step", "detail")
//
//  
//
//  implicit def serializer[T](implicit writer: JsonWriter[T]): Serializer[T] = new Serializer[T] {
//    def to(from: T): String = {
//      from.toJson(writer).toString()
//    }
//  }
//  implicit def deserializer[T](implicit reader: JsonReader[T]): Derializer[T] = new Derializer[T] {
//    def to(from: String): T = {
//      from.parseJson.convertTo[T]
//    }
//  }
//
//}