package org.zalando.nakadi.client.example2

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.zalando.nakadi.client.ClientFactory
import org.zalando.nakadi.client.Connection
import org.zalando.nakadi.client.HttpFactory
import org.zalando.nakadi.client.NakadiDeserializer
import org.zalando.nakadi.client.StreamParameters
import org.zalando.nakadi.client.model.JacksonJsonMarshaller
import com.fasterxml.jackson.core.`type`.TypeReference
import akka.NotUsed
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.ClosedShape
import akka.stream.FlowShape
import akka.stream.Outlet
import akka.stream.OverflowStrategy
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorSubscriber
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.stream.actor.RequestStrategy
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.GraphDSL
import akka.stream.scaladsl.RunnableGraph
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.zalando.nakadi.client.done.EventConsumingActor
import org.zalando.nakadi.client.model.Cursor

object HttpClient extends App with ClientFactory with HttpFactory with JacksonJsonMarshaller {
  private implicit val actorSystem = ActorSystem("Nakadi-Client-Connections")
  private implicit val http = Http(actorSystem)
  implicit val materializer = ActorMaterializer()
  val eventName = "/event-types/test-client-integration-event-1936085527-148383828851369665/events"
  val cursor = Cursor(0,30115)
  val params = Some(StreamParameters())
  val headers = RawHeader("Accept", "application/x-json-stream") :: withHeaders(params)
  val request = withHttpRequest(eventName, HttpMethods.GET, headers, OAuth2Token)
  case class MyEventExample(orderNumber: String)
  implicit val myEvent = new TypeReference[MyEventExample] {}
  val receiver = new ReceiverGraph[MyEventExample](eventName, connection, request, headers)
  receiver.listen()
}

class ReceiverGraph[T](eventName: String, connection: Connection, request: HttpRequest, headers: List[HttpHeader]) //
(implicit system: ActorSystem, m: ActorMaterializer, des: NakadiDeserializer[T]) {

  import GraphDSL.Implicits._
  def listen() = {

    val subscriber = ActorSubscriber[ByteString](system.actorOf(Props[EventConsumingActor]))
    val sink2 = Sink.fromSubscriber(subscriber)
    val flow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] = connection.connection()

    import Transformers._
    val req = Source.single(request) //
      .via(flow) //
      .buffer(100, OverflowStrategy.backpressure) //
      .via(validRequestFilter).map(_.entity.dataBytes)
      .runForeach(_.runWith(sink2)).onComplete { _ =>
        println("Shutting down")
        system.terminate()
      }
  }

  def test() = {
    import Transformers._
    val g: RunnableGraph[_] = RunnableGraph.fromGraph {
      GraphDSL.create() { implicit builder =>
        val out: Outlet[HttpResponse] = builder.add(Source.single(request).via(connection.connection())).out

        //flows
        //            val validFilter: FlowShape[HttpResponse, HttpResponse] = builder.add(validRequestFilter)
        val transform2String: FlowShape[HttpResponse, String] = builder.add(toByteString)
        //            val deserializer: FlowShape[String, T] = builder.add(transformToObject)
        //            val printer: Inlet[Any] = builder.add(Sink.foreach[Any](x => println(" >>> " + x))).in
        val in = builder.add(Sink.foreach[String] { x: String => println(s" 1>> $x ") }).in
        out ~> transform2String ~> in

        ClosedShape //        
      }
    }
    g.run()
  }

}

object Transformers {

  def failure(s: String) = Future.failed(new IllegalArgumentException(s"Error $s"))
  def toByteString(implicit m: ActorMaterializer): Flow[HttpResponse, String, NotUsed] = Flow[HttpResponse].mapAsync(1)(Unmarshal(_).to[String])
  val validRequestFilter: Flow[HttpResponse, HttpResponse, NotUsed] = Flow[HttpResponse].filter(_.status.isSuccess())
  def transformToObject[T](implicit des: NakadiDeserializer[T]): Flow[String, T, NotUsed] = Flow[String].map { x =>
    println(" x " + x)
    des.fromJson(x)
  }

}