package org.zalando.nakadi.client.scala

import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicLong

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

import org.slf4j.LoggerFactory
import org.zalando.nakadi.client.java.JavaClientHandler
import org.zalando.nakadi.client.java.JavaClientHandlerImpl
import org.zalando.nakadi.client.java.model.{Event => JEvent}
import org.zalando.nakadi.client.scala.model.Event
import org.zalando.nakadi.client.Serializer

import HttpFactory.TokenProvider
import akka.actor.Actor
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.HostConnectionPool
import akka.http.scaladsl.HttpsConnectionContext
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.stream.ActorMaterializer
import akka.stream.ActorMaterializerSettings
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import akka.http.scaladsl.model.{HttpHeader, HttpMethod, HttpMethods, HttpResponse, MediaRange}
import org.zalando.nakadi.client.handler.SubscriptionHandlerImpl
import akka.stream.Supervision

sealed trait EmptyJavaEvent  extends JEvent
sealed trait EmptyScalaEvent extends Event

trait Connection {
  //Connection details
  import HttpFactory.TokenProvider
  def tokenProvider(): Option[TokenProvider]
  def executeCall(request: HttpRequest): Future[HttpResponse]
  def get(endpoint: String): Future[HttpResponse]
  def delete(endpoint: String): Future[HttpResponse]
  def put[T](endpoint: String, model: T)(implicit serializer: Serializer[T]): Future[HttpResponse]
  def post[T](endpoint: String, model: T)(implicit serializer: Serializer[T]): Future[HttpResponse]
  def requestFlow(): Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]]
  def materializer(decider: Supervision.Decider): ActorMaterializer
  def materializer(): ActorMaterializer
  def actorSystem(): ActorSystem
}

/**
  * Companion object with factory methods.
  */
object Connection { 
  /**
    * Creates a new SSL context for usage with connections based on the HTTPS protocol.
    */
  def newSslContext(secured: Boolean, verified: Boolean): Option[HttpsConnectionContext] =
    (secured, verified) match {
      case (true, true) =>
        Some(new HttpsConnectionContext(SSLContext.getDefault))
      case (true, false) =>
        val permissiveTrustManager: TrustManager = new X509TrustManager() {
          override def checkClientTrusted(x$1: Array[java.security.cert.X509Certificate], x$2: String): Unit = {}
          override def checkServerTrusted(x$1: Array[java.security.cert.X509Certificate], x$2: String): Unit = {}
          override def getAcceptedIssuers(): Array[X509Certificate] =
            Array.empty
        }
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(Array.empty, Array(permissiveTrustManager), new SecureRandom())
        Some(new HttpsConnectionContext(sslContext))
      case _ => None
    }

  def newClient(host: String,
                port: Int,
                tokenProvider: Option[() => String],
                securedConnection: Boolean,
                verifySSlCertificate: Boolean): Client = {
    val connection = new ConnectionImpl(host, port, tokenProvider, securedConnection, verifySSlCertificate)
    new ClientImpl(connection, new SubscriptionHandlerImpl(connection))
  }

  def newClientHandler4Java(host: String,
                            port: Int,
                            tokenProvider: Option[() => String],
                            securedConnection: Boolean,
                            verifySSlCertificate: Boolean): JavaClientHandler = {
    val connection = new ConnectionImpl(host, port, tokenProvider, securedConnection, verifySSlCertificate)
    new JavaClientHandlerImpl(connection, new SubscriptionHandlerImpl(connection))
  }
}

/**
  * Class for handling the basic http calls.
  */
sealed class ConnectionImpl(val host: String,
                            val port: Int,
                            val tokenProvider: Option[() => String],
                            securedConnection: Boolean,
                            verifySSlCertificate: Boolean)
    extends Connection {
  import Connection._
  import HttpFactory._
  var registrationCounter: AtomicLong = new AtomicLong(0);
  type HttpFlow[T]   = Flow[(HttpRequest, T), (Try[HttpResponse], T), HostConnectionPool]
  type StepResult[T] = (T, Option[String])

  implicit val actorSystem = ActorSystem("Nakadi-Client-Connections")

  def materializer(decider: Supervision.Decider): ActorMaterializer = {
    ActorMaterializer(ActorMaterializerSettings(actorSystem).withSupervisionStrategy(decider))
  }
  def materializer() =
    ActorMaterializer(ActorMaterializerSettings(actorSystem))
  private implicit val http              = Http(actorSystem)
  private val actors: Map[String, Actor] = Map()

  val logger = LoggerFactory.getLogger(this.getClass)

  def requestFlow(): Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
    newSslContext(securedConnection, verifySSlCertificate) match {
      case Some(result) =>
        http.outgoingConnectionHttps(host, port, result)
      case None =>
        logger.warn("Disabled HTTPS, switching to HTTP only!")
        http.outgoingConnection(host, port)
    }

  def get(endpoint: String): Future[HttpResponse] = {
    logger.info("Get - URL {}", endpoint)
    executeCall(withHttpRequest(endpoint, HttpMethods.GET, Nil, tokenProvider, Map()))
  }
  def delete(endpoint: String): Future[HttpResponse] = {
    logger.info("Delete: {}", endpoint)
    executeCall(withHttpRequest(endpoint, HttpMethods.DELETE, Nil, tokenProvider, Map()))
  }

  def put[T](endpoint: String, model: T)(implicit serializer: Serializer[T]): Future[HttpResponse] = {
    val entity = serializer.to(model)
    logger.info(s"Put: $endpoint - Data: $entity")
    executeCall(withHttpRequestAndPayload(endpoint, entity, HttpMethods.PUT, tokenProvider))
  }

  def post[T](endpoint: String, model: T)(implicit serializer: Serializer[T]): Future[HttpResponse] = {
    val entity = serializer.to(model)
    logger.info(s"Post: $endpoint - Data: $entity")
    executeCall(withHttpRequestAndPayload(endpoint, entity, HttpMethods.POST, tokenProvider))
  }

  def executeCall(request: HttpRequest): Future[HttpResponse] = {
    logger.debug("executingCall {}", request)
    val response: Future[HttpResponse] = Source.single(request).via(requestFlow).runWith(Sink.head)(materializer())
    logError(response)
    response
  }

  private def logError(future: Future[Any]) {
    future recover {
      case e: Throwable =>
        logger.error("Failed to call endpoint with: ", e.getMessage)
    }
  }

}
