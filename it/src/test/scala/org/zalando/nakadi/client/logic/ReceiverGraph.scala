package org.zalando.nakadi.client.logic

import scala.concurrent.Future

import org.zalando.nakadi.client.Connection

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.stream.ActorMaterializer
import akka.stream.ClosedShape
import akka.stream.FlowShape
import akka.stream.Inlet
import akka.stream.Outlet
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.GraphDSL
import akka.stream.scaladsl.RunnableGraph
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString

class ReceiverGraph[T](eventName: String, connection: Connection, request: HttpRequest, headers: List[HttpHeader]) //
(implicit system: ActorSystem, m: ActorMaterializer) {
  import GraphDSL.Implicits._

  def listen() = {

    val g: RunnableGraph[_] = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>

      val flow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] = connection.connection()

      // Source
      val out: Outlet[HttpResponse] = builder.add(Source.single(request).via(flow)).out
      val transform2ByteString: FlowShape[HttpResponse, ByteString] = builder.add(null)
      val transform2String: FlowShape[ByteString, String] = builder.add(null)
      val deserialize2Object: FlowShape[String, T] = builder.add(null)
      val filterMessagesWithEvents: FlowShape[T, T] = builder.add(null)
      val publishEvent2listener: Inlet[Any] = builder.add(Sink.ignore).in


      // Graph
      out ~> transform2ByteString ~> transform2String ~>
        deserialize2Object ~> filterMessagesWithEvents ~> publishEvent2listener

      ClosedShape //

    })

    g.run()
    
  }
   

}