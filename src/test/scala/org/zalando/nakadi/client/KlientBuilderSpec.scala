package org.zalando.nakadi.client

import java.net.URI

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
  }
}
