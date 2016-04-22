package org.zalando.nakadi.client

import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import org.mockito.Matchers.any
import org.mockito.Matchers.anyString
import org.mockito.Mockito.reset
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfter
import org.scalatest.FlatSpec
import org.scalatest.Matchers
import org.scalatest.WordSpec
import org.scalatest.mock.MockitoSugar
import akka.http.scaladsl.model.HttpResponse
import akka.stream.Materializer
import spray.json.JsonFormat
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.HttpProtocol
import akka.http.scaladsl.model.HttpProtocols
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.ContentTypes
import org.zalando.nakadi.client.model.JacksonJsonMarshaller

class ClientTest extends WordSpec with Matchers with JacksonJsonMarshaller with MockitoSugar with BeforeAndAfter {
  private var connection: Connection = mock[Connection]
  private val client: Client = new ClientImpl(connection)
  before {
    reset(connection)
  }
  "ClientImpl " should {

    "return a None when receiving a 404" in {
      val headers = null
      val entity = HttpEntity(ContentTypes.`application/json`, "abc")
      val response = new HttpResponse(StatusCodes.NotFound, headers, entity, HttpProtocols.`HTTP/1.1`)
      val futureResponse = Future.successful(response)
      when(connection.get(URI_PARTITIONING_STRATEGIES)).thenReturn(futureResponse)
      //    	val result=Await.result(client.partitionStrategies(),500.seconds)
      //    	result.isLeft shouldBe true
      //    	val Left(clientError) = result
      //    	clientError.status shouldBe None
    }

    "marshall an object when receiving http a 200 with valid payload" in {
      //TODO : implement
    }

    "catch the marshalling exception when receiving a 200 with invalid payload" in {
      //TODO : implement
    }
    "return a ClientError receiving a redirection (300-399)" in {
      //TODO : implement
    }
  }

}