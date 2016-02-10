package test.unit

import org.scalatest._
import spray.json.{JsObject, JsNumber}

import org.zalando.nakadi.client.tools.AnyJsonFormat._
import spray.json._
import DefaultJsonProtocol._

class AnyJsonFormatTest extends FlatSpec with Matchers {

  behavior of "Conversion of 'Map[String,Any]' to/from JSON"

  {
    val a = Map[String,Any](
      "a" -> 42,
      "b" -> 12.34,
      "c" -> "tiger",
      "d" -> true,
      "e" -> false
    )
    val a_jso = JsObject(
      "a" -> JsNumber(42),
      "b" -> JsNumber(12.34),
      "c" -> JsString("tiger"),
      "d" -> JsTrue,
      "e" -> JsFalse
    )

    it should "write out a Map" in {
      val actual = a.toJson

      // Note: The order of the fields may change - if it does, let's change the condition here.
      //
      actual.toString shouldBe a_jso.toString
    }

    it should "read in a Map" in {
      val s = a_jso.prettyPrint
      val actual = s.toJson.convertTo[Map[String, Any]]

      actual should contain theSameElementsAs a
    }
  }

  {
    val m = Map(
      "inner" -> Map(
        "a" -> 42
      )
    )
    val m_jso = JsObject(
      "inner" -> JsObject(
        "a" -> JsNumber(42)
      )
    )

    it should "write Maps recursively" in {
      val actual = m.toJson

      actual.toString shouldBe m_jso.toString
    }

    it should "read Maps recursively" in {
      val actual = m_jso.convertTo[Map[String, Any]]
      val expected = m

      actual should contain theSameElementsAs expected
    }
  }
}
