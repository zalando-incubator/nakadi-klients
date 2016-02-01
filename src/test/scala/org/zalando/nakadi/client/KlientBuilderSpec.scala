package org.zalando.nakadi.client

import java.net.URI

import com.fasterxml.jackson.databind.ObjectMapper
import org.scalatest.{WordSpec, Matchers}

class KlientBuilderSpec extends WordSpec with Matchers {

  "A Klient builder" must {
    "must build a Klient instance, if everything is set properly" in {
      KlientBuilder()
        .withEndpoint(new URI("localhost:8080"))
        .withTokenProvider(() => "my-token")
        .build()
    }

    "must build a Java client instance, if everything is set properly" in {
      KlientBuilder()
        .withEndpoint(new URI("localhost:8080"))
        .withTokenProvider(() => "my-token")
        .buildJavaClient()
    }

    "must throw an exception, if not all mandatory arguments are set" in {
      an [IllegalStateException] must be thrownBy {
        KlientBuilder()
          .withTokenProvider(() => "my-token")
          .build()
      }
      an [IllegalStateException] must be thrownBy {
        KlientBuilder()
          .withEndpoint(new URI("localhost:8080"))
          .build()
      }
    }

    "should use the specified ObjectMapper" in {

      val objectMapper = new ObjectMapper()

      val klient: KlientImpl = KlientBuilder()
        .withEndpoint(new URI("localhost:8080"))
        .withTokenProvider(() => "my-token")
        .withObjectMapper(Some(objectMapper))
        .build().asInstanceOf[KlientImpl]

      klient.objectMapper == objectMapper should be(true)
    }
  }
}
