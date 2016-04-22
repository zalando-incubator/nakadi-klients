package org.zalando.nakadi.client

import scala.reflect._
import scala.reflect.runtime.universe._
import org.zalando.nakadi.client.model.JacksonJsonMarshaller
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import org.zalando.nakadi.client.model.DataOperation
import org.zalando.nakadi.client.model.BatchItemStep
import org.zalando.nakadi.client.model.BatchItemPublishingStatus
import org.zalando.nakadi.client.model.EventTypeCategory
import org.zalando.nakadi.client.model.SchemaType
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration

case object EnumModule extends SimpleModule() {
  //  addSerializer(ser)
}

object FunninessLevel extends Enumeration {
  type FunninessLevel = Value
  val LOL = Value("LOL")
  val ROFL = Value("ROFL")
  val LMAO = Value("LMAO")
}

class EnumSerializer extends JsonSerializer[FunninessLevel.FunninessLevel] {
  def serialize(id: FunninessLevel.FunninessLevel, json: JsonGenerator, provider: SerializerProvider) = {
    json.writeString(id.toString())
  }
}
class EnumDeserializer extends JsonDeserializer[FunninessLevel.FunninessLevel] {
  def deserialize(jp: JsonParser, ctxt: DeserializationContext): FunninessLevel.FunninessLevel = {
    FunninessLevel.withName(jp.getText())
  }
}

//object Test extends App with JacksonJsonMarshaller {
//
//  val marshaller = new JacksonJsonMarshaller {}
//
//  val mapper = marshaller.defaultObjectMapper
//
//  implicit val personType = new TypeReference[Person] {}
//  
//  case class Person(name: String, @JsonScalaEnumeration(classOf[UserStatusType]) humor: DataOperation.Value)
//  val person = Person("Fernando", DataOperation.CREATE)
//  val json = marshaller.serializer[Person].toJson(person)
//
//  println(" #### -- PERSON >> " + person)
//  println(" #### -- JSON-PERSON >> " + json)
//  val personResult = marshaller.deserializer[Person].fromJson(json)
//  println(" #### -- PERSON >> " + personResult)
//
//}


  
