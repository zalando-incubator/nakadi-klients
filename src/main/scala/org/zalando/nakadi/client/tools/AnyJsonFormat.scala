package org.zalando.nakadi.client.tools

/*
* This is needed to read/write 'Map[String,Any]', where the values may be anything convertible to/from JSON.
*
* From http://stackoverflow.com/questions/25916419/serialize-mapstring-any-with-spray-json
*/
import spray.json._
import DefaultJsonProtocol._

object AnyJsonFormat {

  implicit object Fmt extends JsonFormat[Any] {

    def write(x: Any) = x match {
      case n: Int => JsNumber(n) // writes as integer (no decimal stop)
      case d: Double => JsNumber(d)
      case s: String => JsString(s)
      case true => JsTrue
      case false => JsFalse
    }

    def read(v: JsValue) = v match {
      case JsNumber(n) if n % 1 == 0 => n.intValue
      case JsNumber(d) => d.doubleValue
      case JsString(s) => s
      case JsTrue => true
      case JsFalse => false
    }
  }
}

/** * REMOVE

  * /**
  * Protocols to convert any nested Maps/Lists to JSON with spray.json.
  *
  * Taken from (*) and added support for List[Any] (also nested).
  *
  * (*) see https://tech.mendix.com/scala/2014/09/28/scala-nested-maps-to-json/
  */

  * object NestedMapsListsJsonProtocol {

  * implicit object MapJsonFormat extends JsonFormat[Map[String, Any]] {
  * def write(m: Map[String, Any]) = {
  * JsObject(m.mapValues {
  * case v: String => JsString(v)
  * case v: Int => JsNumber(v)
  * case v: Long => JsNumber(v)
  * case v: Double => JsNumber(v)
  * case v: Boolean => JsBoolean(v)
  * case v: Map[_, _] => write(v.asInstanceOf[Map[String, Any]])
  * case v: List[_] => ListJsonFormat.write(v.asInstanceOf[List[Any]])
  * case v: Any => JsString(v.toString)
  * })
  * }

  * def read(value: JsValue) = value match {
  * case v: JsObject => readMap(v)
  * case v => deserializationError("Expected Map[String, Any] as JsObject, but got " + v)
  * }

  * private def readMap(value: JsObject): Map[String, Any] = value.fields.mapValues {
  * case JsString(v) => v
  * case JsNumber(v) => v
  * case JsBoolean(v) => v
  * case v: JsObject => readMap(v)
  * case v: JsArray => ListJsonFormat.read(v)
  * case v => deserializationError("Unexpected value " + v)
  * }

  * }

  * implicit object ListJsonFormat extends JsonFormat[List[Any]] {
  * def write(l: List[Any]) = {
  * JsArray(l.map({
  * case v: String => JsString(v)
  * case v: Int => JsNumber(v)
  * case v: Long => JsNumber(v)
  * case v: Double => JsNumber(v)
  * case v: Boolean => JsBoolean(v)
  * case v: Map[_, _] => MapJsonFormat.write(v.asInstanceOf[Map[String, Any]])
  * case v: List[Any] => write(v)
  * case v: Any => JsString(v.toString)
  * }).toVector)
  * }

  * def read(value: JsValue) = value match {
  * case v: JsArray => readList(v)
  * case v => deserializationError("Expected List[Any] as JsArray, but got " + v)
  * }

  * private def readList(value: JsArray): List[Any] = value.elements.map({
  * case JsString(v) => v
  * case JsNumber(v) => v
  * case JsBoolean(v) => v
  * case v: JsObject => MapJsonFormat.read(v)
  * case v: JsArray => readList(v)
  * case v => deserializationError("Unexpected value " + v)
  * }).toList
  * }

  * }

  ***/
