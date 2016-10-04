package org.zalando.nakadi.client.scala.model

import org.slf4j.LoggerFactory
import org.zalando.nakadi.client.Deserializer
import org.zalando.nakadi.client.Serializer

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule

object ScalaJacksonJsonMarshaller {
  val logger = LoggerFactory.getLogger(this.getClass)

  // All TypeReferences
  implicit def problemTR: TypeReference[Problem] =
    new TypeReference[Problem] {}
  implicit val metricsTR: TypeReference[Metrics] = new TypeReference[Metrics] {}
  implicit def partitionTR: TypeReference[Partition] =
    new TypeReference[Partition] {}
  implicit def cursorTR: TypeReference[Cursor] =
    new TypeReference[Cursor] {}
  implicit def eventTypeSchemaTR: TypeReference[EventTypeSchema] =
    new TypeReference[EventTypeSchema] {}
  implicit def partitionResolutionStrategyTR: TypeReference[PartitionStrategy.Value] =
    new TypeReference[PartitionStrategy.Value] {}
  implicit def eventEnrichmentStrategyTR: TypeReference[EventEnrichmentStrategy.Value] =
    new TypeReference[EventEnrichmentStrategy.Value] {}
  implicit def eventTypeCategoryTR: TypeReference[EventTypeCategory.Value] =
    new TypeReference[EventTypeCategory.Value] {}
  implicit def dataChangeEventQualifierTR: TypeReference[DataChangeEventQualifier] =
    new TypeReference[DataChangeEventQualifier] {}
  implicit def eventTypeStatisticsTR: TypeReference[EventTypeStatistics] =
    new TypeReference[EventTypeStatistics] {}
  implicit def eventTypeTR: TypeReference[EventType] =
    new TypeReference[EventType] {}
  implicit def eventTR: TypeReference[Event] =
    new TypeReference[Event] {}
  implicit def eventStreamBatchTR: TypeReference[EventStreamBatch[_]] =
    new TypeReference[EventStreamBatch[_]] {}

  implicit def eventMetadataTR: TypeReference[EventMetadata] =
    new TypeReference[EventMetadata] {}
  implicit def businessEventTR: TypeReference[BusinessEvent] =
    new TypeReference[BusinessEvent] {}
  implicit def batchItemResponseTR: TypeReference[BatchItemResponse] =
    new TypeReference[BatchItemResponse] {}
  implicit def dataChangeEventTR: TypeReference[DataChangeEvent[Any]] =
    new TypeReference[DataChangeEvent[Any]] {}

  implicit def subscriptionTR: TypeReference[Subscription] =
    new TypeReference[Subscription] {}

  //Lists
  implicit def listOfPartitionStrategyTR: TypeReference[Seq[PartitionStrategy.Value]] =
    new TypeReference[Seq[PartitionStrategy.Value]] {}
  implicit def listOfEventEnrichmentStrategyTR: TypeReference[Seq[EventEnrichmentStrategy.Value]] =
    new TypeReference[Seq[EventEnrichmentStrategy.Value]] {}
  implicit def listOfEventTypeTR: TypeReference[Seq[EventType]] =
    new TypeReference[Seq[EventType]] {}
  implicit def listOfPartitionTR: TypeReference[Seq[Partition]] =
    new TypeReference[Seq[Partition]] {}
  implicit def listOfStringsTR: TypeReference[Seq[String]] = new TypeReference[Seq[String]] {}

  implicit def optionalDeserializer[T](implicit expectedType: TypeReference[T]): Deserializer[Option[T]] =
    new Deserializer[Option[T]] {
      def from(from: String): Option[T] = {

        defaultObjectMapper.readValue[Option[T]](from, expectedType)
      }
    }

  implicit def serializer[T]: Serializer[T] =
    new Serializer[T] {
      def to(from: T): String =
        defaultObjectMapper.writeValueAsString(from)
    }

  implicit def deserializer[T](implicit expectedType: TypeReference[T]): Deserializer[T] =
    new Deserializer[T] {
      def from(from: String): T =
        defaultObjectMapper.readValue[T](from, expectedType)
    }
  
  
  def deserializer[T, B](expectedType: TypeReference[T], tranformer: T => B): Deserializer[B] =
    new Deserializer[B] {
      def from(from: String): B = {
        tranformer(defaultObjectMapper.readValue[T](from, expectedType))
      }

    }

  lazy val defaultObjectMapper: ObjectMapper = {
    val scalaModule = new DefaultScalaModule
    new ObjectMapper()
      .registerModule(scalaModule)
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true)
      .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
      .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
      .addHandler(new DeserializationProblemHandler() {
        override def handleUnknownProperty(ctxt: DeserializationContext,
                                           jp: JsonParser,
                                           deserializer: JsonDeserializer[_],
                                           beanOrClass: AnyRef,
                                           propertyName: String): Boolean = {
          logger.warn(
            s"unknown property occurred in JSON representation: [beanOrClass=$beanOrClass, property=$propertyName]")
          true
        }
      })
  }

}
