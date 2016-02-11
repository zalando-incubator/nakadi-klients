package org.zalando.nakadi.client.tools

/*
* This is needed to read/write 'Map[String,Any]', where the values may be anything convertible to/from JSON.
*/
import spray.json._
import DefaultJsonProtocol._

object AnyJsonFormat {

  /** * disabled
    * From -> http://stackoverflow.com/questions/25916419/serialize-mapstring-any-with-spray-json
    *
    * implicit object Fmt extends JsonFormat[Any] {

    * def write(x: Any) = x match {
    * case n: Int => JsNumber(n) // writes as integer (no decimal stop)
    * case d: Double => JsNumber(d)
    * case s: String => JsString(s)
    * case true => JsTrue
    * case false => JsFalse
    * }

    * def read(v: JsValue) = v match {
    * case JsNumber(n) if n % 1 == 0 => n.intValue
    * case JsNumber(d) => d.doubleValue
    * case JsString(s) => s
    * case JsTrue => true
    * case JsFalse => false
    * }
    * }
    * **/

  /*
  * From (link expired!) -> https://tech.mendix.com/scala/2014/09/28/scala-nested-maps-to-json/
  */
  implicit object MapJsonFormat extends JsonFormat[Map[String, Any]] {
    def write(m: Map[String, Any]) = {
      JsObject(m.mapValues {
        case s: String => JsString(s)
        case v: Int => JsNumber(v)    // will get output without decimal point
        case v: Long => JsNumber(v)
        case v: Double => JsNumber(v)
        case b: Boolean => JsBoolean(b)
        case mm: Map[String @unchecked,Any @unchecked] => write(mm)
        case x => serializationError(s"Unexpected value within Map[String,Any] values (cannot convert to JSON): $x")
      })
    }

    def read(jsv: JsValue) = jsv match {
      case jso: JsObject => readMap(jso)
      case v => deserializationError("Expected JsObject, but got: " + v)
    }

    // Note: Makes sense having this as a separate function (not embedded in 'read') because our recursion can now
    //      use 'JsObject's directly.
    //
    private
    def readMap(jso: JsObject): Map[String,Any] = jso.fields.mapValues {
      case JsString(s) => s
      case JsNumber(d) if d.intValue == d => d.intValue
      case JsNumber(d) if d.longValue == d => d.longValue
      case JsNumber(d) => d
      case JsBoolean(b) => b
      case v: JsObject => readMap(v)
      case v => deserializationError("Unexpected value within JsObject: " + v)
    }
  }

    /*** remove (not needed)
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
    ***/

}