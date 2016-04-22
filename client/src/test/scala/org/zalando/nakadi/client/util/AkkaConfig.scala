package org.zalando.nakadi.client.util

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.ActorMaterializer

trait AkkaConfig {
  implicit val system = ActorSystem("AkkaConfig")
  implicit val dispatcher = system.dispatcher
  implicit val materializer: Materializer = ActorMaterializer()
}