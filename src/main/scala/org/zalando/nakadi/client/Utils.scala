package org.zalando.nakadi.client

import java.net.URI
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import akka.actor.ActorSystem
import akka.http.scaladsl.Http.OutgoingConnection
import akka.http.scaladsl.model.{HttpResponse, HttpRequest}
import akka.http.scaladsl.{Http, HttpsContext}
import akka.stream.scaladsl.Flow

import scala.concurrent.{Future, Await}
import scala.concurrent.duration.Duration


object Utils {
  def convert[T](x: scala.concurrent.Future[T]): java.util.concurrent.Future[T]={
    new java.util.concurrent.Future[T] {
      override def isCancelled: Boolean = throw new UnsupportedOperationException

      override def get(): T = Await.result(x, Duration.Inf)

      override def get(timeout: Long, unit: TimeUnit): T = Await.result(x, Duration.create(timeout, unit))

      override def cancel(mayInterruptIfRunning: Boolean): Boolean = throw new UnsupportedOperationException

      override def isDone: Boolean = x.isCompleted
    }
  }

  def outgoingHttpConnection(endpoint: URI, port: Int, securedConnection: Boolean)(implicit system: ActorSystem): Flow[HttpRequest, HttpResponse, Future[OutgoingConnection]] = {
    val http = Http(system)
    if(securedConnection) {
      http.setDefaultClientHttpsContext(HttpsContext(SSLContext.getDefault))
      http.outgoingConnectionTls(endpoint.toString, port)
    }

    else http.outgoingConnection(endpoint.toString, port)
  }

}
