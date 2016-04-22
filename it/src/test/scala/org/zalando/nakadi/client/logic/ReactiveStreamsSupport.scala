package org.zalando.nakadi.client.logic

import akka.actor.ActorSystem
import akka.util.Timeout
import scala.concurrent.duration._
import akka.stream.ActorMaterializer

trait ReactiveStreamsSupport extends Logging {
  implicit def system: ActorSystem

  implicit val dispatcher = system.dispatcher

  implicit val timeout = Timeout(5.seconds)

  implicit val materializer = ActorMaterializer()
}
