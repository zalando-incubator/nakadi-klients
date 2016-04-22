package org.zalando.nakadi.client

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import org.scalatest.Matchers
import org.scalatest.WordSpec
import org.zalando.nakadi.client.model.EventType
import org.zalando.nakadi.client.util.AkkaConfig
import org.zalando.nakadi.client.util.TestJsonEntity
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.Materializer
import spray.json.JsonFormat
import org.zalando.nakadi.client.model.JacksonJsonMarshaller

class DeserializerSerializerTest extends WordSpec with Matchers with JacksonJsonMarshaller with AkkaConfig {
  import TestJsonEntity._
  val file  = getClass().getClassLoader().getResource("Events.txt").getFile
  val input = scala.io.Source.fromFile(file).mkString
  
  "When a json entity is unmarshalled and marshalled it" should {
    val testName = "always result in the same entity"
    s"$testName(event)" in {
//      checkDeserializationProcessSerialization[EventType]("event", singleEvent)
//      checkDeserializationProcessSerialization[List[EventType]]("event", input)
    }
  }

  def checkDeserializationProcessSerialization[T](key: String, input: String)(implicit m: JsonFormat[T] ) {
    import spray.json._
    val json = input.parseJson
    println(">>>>>>> IN " + json.compactPrint)
    val scalaModel = json.convertTo[T]
    val jsonResult = scalaModel.toJson
    
    println(">>>>>>> OUT 1" + jsonResult)
    
    assert(jsonResult == json, s"Failed to marshall $key")
  }

}