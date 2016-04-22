package org.zalando.nakadi.client

import scala.Left
import scala.Right
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import org.slf4j.LoggerFactory
import org.zalando.nakadi.client.model._
import com.typesafe.scalalogging.Logger
import Client.ClientError
import akka.actor.Terminated
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.model.headers.RawHeader

private[client] class ClientImpl(connection: Connection, charSet: String = "UTF-8") extends Client with HttpFactory{
  import Client._
  implicit val materializer = connection.materializer

  val logger = Logger(LoggerFactory.getLogger(this.getClass))
  def metrics()(implicit ser: NakadiDeserializer[Metrics]): Future[Either[ClientError, Option[Metrics]]] = {
    logFutureEither(connection.get(URI_METRICS).flatMap(mapToEither(_)))
  }

  def eventTypes()(implicit ser: NakadiDeserializer[Seq[EventType]]): Future[Either[ClientError, Option[Seq[EventType]]]] = {
    logFutureEither(connection.get(URI_EVENT_TYPES).flatMap(mapToEither(_)))
  }

  def newEventType(eventType: EventType)(implicit ser: NakadiSerializer[EventType]): Future[Option[ClientError]] = {
    logFutureOption(connection.post(URI_EVENT_TYPES, eventType).flatMap(mapToOption(_)))
  }

  def eventType(name: String)(implicit ser: NakadiDeserializer[EventType]): Future[Either[ClientError, Option[EventType]]] = {
    logFutureEither(connection.get(URI_EVENT_TYPE_BY_NAME.format(name)).flatMap(in => mapToEither(in)))
  }

  def updateEventType(name: String, eventType: EventType)(implicit ser: NakadiSerializer[EventType]): Future[Option[ClientError]] = {
    val result = connection.put(URI_EVENT_TYPE_BY_NAME.format(name), eventType)
    logFutureOption(result.flatMap(in => mapToOption(in)))
  }

  def deleteEventType(name: String): Future[Option[ClientError]] = {
    logFutureOption(connection.delete(URI_EVENT_TYPE_BY_NAME.format(name)).flatMap(in => mapToOption(in)))
  }

  def newEvents[T](name: String, events: Seq[T])(implicit ser: NakadiSerializer[Seq[T]]): Future[Option[ClientError]] = {
    logFutureOption(connection.post(URI_EVENTS_OF_EVENT_TYPE.format(name), events).flatMap(in => mapToOption(in)))
  }

  def events[T](name: String, params: Option[StreamParameters])(implicit ser: NakadiDeserializer[T]): Future[Either[ClientError, Option[T]]] = {
    val headers = withHeaders(params)
    logFutureEither(connection.get(URI_EVENTS_OF_EVENT_TYPE.format(name)).flatMap(in => mapToEither(in)))
  }

  def partitions(name: String)(implicit ser: NakadiDeserializer[Partition]): Future[Either[ClientError, Option[Partition]]] = {
    logFutureEither(connection.get(URI_PARTITIONS_BY_EVENT_TYPE.format(name)).flatMap(in => mapToEither(in)))
  }

  def partitionById(name: String, id: String)(implicit ser: NakadiDeserializer[Partition]): Future[Either[ClientError, Option[Partition]]] = {
    logFutureEither(connection.get(URI_PARTITION_BY_EVENT_TYPE_AND_ID.format(name)).flatMap(in => mapToEither(in)))
  }

  def validationStrategies()(implicit des: NakadiDeserializer[Seq[EventValidationStrategy.Value]]): Future[Either[ClientError, Option[Seq[EventValidationStrategy.Value]]]] = {
    logFutureEither(connection.get(URI_VALIDATION_STRATEGIES).flatMap(mapToEither(_)))
  }

    def enrichmentStrategies()(implicit des: NakadiDeserializer[Seq[EventEnrichmentStrategy.Value]]): Future[Either[ClientError, Option[Seq[EventEnrichmentStrategy.Value]]]] = {
    logFutureEither(connection.get(URI_ENRICHMENT_STRATEGIES).flatMap(mapToEither(_)))
  }

  def partitionStrategies()(implicit des: NakadiDeserializer[Seq[PartitionStrategy.Value]]): Future[Either[ClientError, Option[Seq[PartitionStrategy.Value]]]] =
    logFutureEither(connection.get(URI_PARTITIONING_STRATEGIES).flatMap(mapToEither(_)))

  def stop(): Future[Terminated] = connection.stop()

  //####################
  //#  HELPER METHODS  #
  //####################

  private[client] def logFutureEither[A, T](future: Future[Either[ClientError, T]]): Future[Either[ClientError, T]] = {
    future recover {
      case e: Throwable =>
        logger.error("A unexpected error occured", e)
        Left(ClientError("Error: " + e.getMessage, None))
    }
  }
  private[client] def logFutureOption(future: Future[Option[ClientError]]): Future[Option[ClientError]] = {
    future recover {
      case e: Throwable =>
        logger.error("A unexpected error occured", e)
        Option(ClientError("Error: " + e.getMessage, None))
    }
  }

  private[client] def mapToEither[T](response: HttpResponse)(implicit deserializer: NakadiDeserializer[T]): Future[Either[ClientError, Option[T]]] = {
    logger.debug("received [response={}]", response)
    response match {
      case HttpResponse(status, headers, entity, protocol) if (status.isSuccess()) =>
        try {
          Unmarshal(entity).to[String].map(body => Right(Some(deserializer.fromJson(body))))
        } catch {
          case e: Throwable =>
            val msg = "Failed to deserialise the content with error: %s".format(e.getMessage)
            logger.error(msg)
            Future.successful(Left(ClientError(msg, Some(status.intValue()))))
        }
      case HttpResponse(StatusCodes.NotFound, headers, entity, protocol) =>
        Future.successful(Right(None))
      case HttpResponse(status, headers, entity, protocol) if (status.isRedirection()) =>
        val msg = "Not implemented: http-status (" + status.intValue() + "}) and reason:" + status.reason()
        logger.info(msg)
        Future.successful(Left(ClientError(msg, Some(status.intValue()))))
      case HttpResponse(status, headers, entity, protocol) if (status.isFailure()) =>
        Unmarshal(entity).to[String].map { body =>
          val msg = "An error occurred, http-status: %s (%s) Message: %s".format(status.intValue(), status.reason(), body)
          logger.warn(msg)
          Left(ClientError(msg, Some(status.intValue())))
        }
    }
  }

  private[client] def mapToOption[T](response: HttpResponse): Future[Option[ClientError]] = {
    response.status match {
      case status if (status.isSuccess()) =>
        logger.debug("Success. http-status: %s", status.intValue().toString())
        Future.successful(None)
      case status if (status.isRedirection()) =>
        val msg = "Redirection - http-status: %s, reason[%s]".format(status.intValue().toString(), status.reason())
        logger.info(msg)
        response.entity.toStrict(10.second).map { body =>
          logger.debug("Redirection - http-status: %s, reason[%s], body:[%s]".format(status.intValue().toString(), status.reason(), body.data.decodeString(charSet)))
        }
        Future.successful(Option(ClientError(msg, Some(status.intValue()))))
      case status if (status.isFailure()) =>
        response.entity.toStrict(10.second).map { body =>
          val msg = "Failure - http-status: %s, reason[%s], body:[%s]".format(status.intValue().toString(), status.reason(), body.data.decodeString(charSet))
          logger.warn(msg)
          Option(ClientError(msg, Some(status.intValue())))
        }
    }
  }

}

