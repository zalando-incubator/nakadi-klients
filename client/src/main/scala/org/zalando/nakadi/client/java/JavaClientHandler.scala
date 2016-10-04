package org.zalando.nakadi.client.java

import java.util.Optional

import scala.collection.immutable.Seq
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.slf4j.LoggerFactory
import org.zalando.nakadi.client.Deserializer
import org.zalando.nakadi.client.Serializer
import org.zalando.nakadi.client.java.model.{ Event => JEvent }
import org.zalando.nakadi.client.scala.model.{ Cursor => ScalaCursor }
import org.zalando.nakadi.client.scala.{ ClientImpl => SClientImpl }
import org.zalando.nakadi.client.java.model.{ EventStreamBatch => JEventStreamBatch }
import org.zalando.nakadi.client.java.{ StreamParameters => JStreamParameters }
import org.zalando.nakadi.client.java.{ Listener => JListener }
import org.zalando.nakadi.client.scala.Connection
import org.zalando.nakadi.client.scala.EmptyScalaEvent
import org.zalando.nakadi.client.scala.EventHandler
import org.zalando.nakadi.client.scala.HttpFactory
import org.zalando.nakadi.client.scala.{ StreamParameters => ScalaStreamParameters }
import org.zalando.nakadi.client.utils.FutureConversions
import org.zalando.nakadi.client.utils.ModelConverter

import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.unmarshalling.Unmarshal
import org.zalando.nakadi.client.handler.SubscriptionHandlerImpl
import org.zalando.nakadi.client.handler.SubscriptionHandler
import org.zalando.nakadi.client.utils.GeneralConversions
import akka.http.scaladsl.model.StatusCodes
import org.zalando.nakadi.client.scala.ScalaEventHandlerImpl
import org.zalando.nakadi.client.scala.JavaEventHandlerImpl
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import akka.http.scaladsl.model.HttpMethod

/**
 * Handler for mapping(Java<->Scala) and handling http calls and listener subscriptions for the Java API.
 */
trait JavaClientHandler {
  def deserialize[T](response: HttpResponse, des: Deserializer[T]): Future[Optional[T]]
  def get[T](endpoint: String, des: Deserializer[T]): java.util.concurrent.Future[Optional[T]]
  def get[T](endpoint: String,
             headers: Seq[HttpHeader],
             des: Deserializer[T]): java.util.concurrent.Future[Optional[T]]
  def post[T](endpoint: String, model: T)(implicit serializer: Serializer[T]): java.util.concurrent.Future[Void]
  def put[T](endpoint: String, model: T)(implicit serializer: Serializer[T]): java.util.concurrent.Future[Void]
  def delete[T](endpoint: String): java.util.concurrent.Future[Void]
  def subscribe[T <: JEvent](
    eventTypeName: String,
    endpoint: String,
    parameters: JStreamParameters,
    listener: JListener[T])(implicit des: Deserializer[JEventStreamBatch[T]]): Optional[ClientError]
  def unsubscribe[T <: JEvent](eventTypeName: String, partition: Optional[String], listener: JListener[T])
  def stop(): Unit

}

class JavaClientHandlerImpl(val connection: Connection, subscriber: SubscriptionHandler) extends JavaClientHandler {
  val logger = LoggerFactory.getLogger(this.getClass)
  import HttpFactory._
  import GeneralConversions._
  private implicit val mat = connection.materializer()

  def deserialize[T](response: HttpResponse, des: Deserializer[T]): Future[Optional[T]] =
    response match {
      case HttpResponse(status, headers, entity, protocol) if (status.isSuccess()) =>
        Try(Unmarshal(entity).to[String].map(body => des.from(body))) match {
          case Success(result) =>
            result.map(Optional.of(_))
          case Failure(error) =>
            throw new RuntimeException(error.getMessage)
        }
      case HttpResponse(StatusCodes.NotFound, headers, entity, protocol) =>
        Future.successful(Optional.empty())
      case HttpResponse(status, headers, entity, protocol) if (status.isFailure()) =>
        throw new RuntimeException(status.reason())
    }

  def get[T](endpoint: String, des: Deserializer[T]): java.util.concurrent.Future[Optional[T]] = {
    FutureConversions.fromFuture2Future(connection.get(endpoint).flatMap(deserialize(_, des)))
  }
  def get[T](endpoint: String,
             headers: Seq[HttpHeader],
             des: Deserializer[T]): java.util.concurrent.Future[Optional[T]] = {
    FutureConversions.fromFuture2Future(
      connection
        .executeCall(withHttpRequest(endpoint, HttpMethods.GET, headers, connection.tokenProvider, Map()))
        .flatMap(deserialize(_, des)))
  }
  def post[T](endpoint: String, model: T)(implicit serializer: Serializer[T]): java.util.concurrent.Future[Void] = {
    executeMethod(endpoint, model, HttpMethods.POST)
  }

  def executeMethod[T](endpoint: String, model: T, httpMethod: HttpMethod)(implicit serializer: Serializer[T]) = {
    val entity = serializer.to(model)
    logger.info(s"Calling ${endpoint} with ${httpMethod} and ${entity}")
    val result = connection
      .executeCall(withHttpRequestAndPayload(endpoint, serialize(model), httpMethod, connection.tokenProvider))
      .flatMap(response(_))
    FutureConversions.fromOption2Void(result)
  }
  def put[T](endpoint: String, model: T)(implicit serializer: Serializer[T]): java.util.concurrent.Future[Void] = {
    executeMethod(endpoint, model, HttpMethods.PUT)
  }
  def delete[T](endpoint: String): java.util.concurrent.Future[Void] = {
    val result = connection.delete(endpoint).flatMap(response(_))
    FutureConversions.fromOption2Void(result)
  }

  private def serialize[T](model: T)(implicit serializer: Serializer[T]): String =
    Try(serializer.to(model)) match {
      case Success(result) => result
      case Failure(error) =>
        throw new RuntimeException("Failed to serialize: " + error.getMessage)
    }

  private def response[T](response: HttpResponse): Future[Option[String]] =
    response match {
      case HttpResponse(status, headers, entity, protocol) if (status.isSuccess()) =>
        logger.debug("Call succeeded: {}",response)
        Try(Unmarshal(entity).to[String]) match {
          case Success(result) => result.map(Option(_))
          case Failure(error) =>
            throw new RuntimeException(error.getMessage)
        }

      case HttpResponse(status, headers, entity, protocol) if (status.isFailure()) =>
        logger.warn("Call failed: {}",response)
        Unmarshal(entity).to[String].map { x =>
          val msg = "http-stats(%s) - %s - problem: %s ".format(status.intValue(), x, status.defaultMessage())
          logger.warn(msg)
          throw new RuntimeException(msg)
        }
    }

  def subscribe[T <: JEvent](eventTypeName: String,
                             endpoint: String,
                             parameters: JStreamParameters,
                             listener: JListener[T])(implicit des: Deserializer[JEventStreamBatch[T]]) = {
    import ModelConverter._
    val params: Option[ScalaStreamParameters] = toScalaStreamParameters(parameters)
    val eventHandler: EventHandler = new JavaEventHandlerImpl(des, listener)
    val finalUrl = withUrl(endpoint, Map()) //params) // FIXME !!!
    val res = subscriber.subscribe(eventTypeName, finalUrl, getCursor(params), eventHandler)
    toJavaClientError(res)
  }

  def unsubscribe[T <: JEvent](eventTypeName: String, partition: Optional[String], listener: JListener[T]) = {
    subscriber.unsubscribe(eventTypeName, toOption(partition), listener.getId)
  }

  def stop(): Unit = {
    mat.shutdown()
    Await.ready(connection.actorSystem().terminate(), Duration.Inf)
  }

  private def getCursor(params: Option[ScalaStreamParameters]): Option[ScalaCursor] =
    params match {
      case Some(
        ScalaStreamParameters(cursor,
          batchLimit,
          streamLimit,
          batchFlushTimeout,
          streamTimeout,
          streamKeepAliveLimit,
          flowId)) =>
        cursor
      case None => None
    }

}
