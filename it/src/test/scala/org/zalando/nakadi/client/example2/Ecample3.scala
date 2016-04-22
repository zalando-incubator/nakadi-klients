package org.zalando.nakadi.client.example2

import akka.stream.actor.ActorPublisher
import akka.actor.Actor
import akka.stream.actor.RequestStrategy
import akka.actor.ActorLogging
import akka.stream.actor.ActorSubscriber
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.actor.Props
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpResponse
import scala.util.Try
import java.net.URI
import akka.stream.scaladsl.Flow
import org.zalando.nakadi.client.Connection
import akka.http.scaladsl.model.HttpRequest
import scala.concurrent.Future
import org.zalando.nakadi.client.HttpFactory
import akka.http.scaladsl.unmarshalling._
import scala.concurrent.ExecutionContext.Implicits.global
import akka.http.scaladsl.Http
import org.zalando.nakadi.client.model.JacksonJsonMarshaller
import org.zalando.nakadi.client.ClientFactory
import org.zalando.nakadi.client.StreamParameters
import akka.stream.ActorMaterializer
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.HttpMethods
import com.fasterxml.jackson.core.`type`.TypeReference
import org.zalando.nakadi.client.done.EventConsumingActor

case class MoreMessages()

class ProducerActor(httpRequest: HttpRequest) extends Actor with ActorPublisher[HttpRequest] with HttpFactory  with ActorLogging  {

  override def receive: Receive = {
    case request: MoreMessages => 
    log.info("Got a request for more messages")
      onNext(httpRequest)
  }
}
 

case class Url(url: String, depth: Long)

object Test extends App with ClientFactory with HttpFactory with JacksonJsonMarshaller {

  private implicit val actorSystem = ActorSystem("Nakadi-Client-Connections")
  private implicit val http = Http(actorSystem)
  implicit val materializer = ActorMaterializer()

  // Request Parameters
  val eventName = "/event-types/test-client-integration-event-1936085527-148383828851369665/events"
  val params = Some(StreamParameters())
  val headers = RawHeader("Accept", "application/x-json-stream") :: withHeaders(params)
  val request = withHttpRequest(eventName, HttpMethods.GET, headers, OAuth2Token)

  //Model
  case class MyEventExample(orderNumber: String)
  implicit val myEvent = new TypeReference[MyEventExample] {}

  private implicit val system = ActorSystem("Nakadi-Client-Connections")
  //  private implicit val http = Http(actorSystem)
  //    implicit val materializer = ActorMaterializer()

  val producerRef = system.actorOf(Props(classOf[EventConsumingActor], request))
  val consumerRef = system.actorOf(Props(classOf[EventConsumingActor]))
  val publisher = ActorPublisher[HttpRequest](producerRef)
  val subscriber = ActorSubscriber[HttpResponse](consumerRef)

  val flow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] = connection.connection()

  import Transformers._

  Source.fromPublisher(publisher)
    .via(flow).via(validRequestFilter)//.via(toByteString)
    .runWith(Sink.fromSubscriber(subscriber))

  //    .map(url => pipeline(Get(url.url)).map((url, _)))
  //    .mapAsync(4)(identity)
  //    .map(parseUrls)
  //    .mapConcat(identity)
  //    .map(url => Url(url.url, url.depth - 1)) // reduce our depth on each step
  //    .filter(_.depth >= 0) // our recursion exit condition
  //    .filter(_.url.contains("akka")) // let’s say we only want to follow urls that contain ‘akka’
  //    .runWith(Sink.fromSubscriber(subscriber))

  producerRef ! Url("https://en.wikipedia.org/wiki/Akka_(toolkit)", 2)

  def parseUrls: ((String, HttpResponse)) => Future[String] = {
    case (url, resp) =>
      Unmarshal(resp.entity).to[String]
  }
}