package org.zalando.nakadi.client.scala

import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

import com.fasterxml.jackson.core.`type`.TypeReference
import org.slf4j.LoggerFactory
import org.zalando.nakadi.client.examples.scala.EventCreationExample.MeetingsEvent
import org.zalando.nakadi.client.scala.model.{Cursor, EventStreamBatch, Subscription}

import scala.util.{Failure, Success}


class EventCounterListener(val id: String, val subscriptionId: UUID , val client: Client) extends Listener[MeetingsEvent] {
  val log = LoggerFactory.getLogger(this.getClass)
  private var eventCount: AtomicLong = new AtomicLong(0);
  private var callerCount: AtomicLong = new AtomicLong(0);
  private var slept = false
  def onError(sourceUrl: String, error: Option[ClientError]): Unit = {
    println("Error %s %s".format(sourceUrl, error))
  }

  def onReceive(sourceUrl: String,
                cursor: Cursor,
                events: Seq[MeetingsEvent]): Unit = {
    eventCount.addAndGet(events.size.toLong)
    log.debug(s"Has a total of $eventCount events")
    log.info(s"Processed cursor $cursor")

    log.info(s"committing [cursor=$cursor]")
    client.commitCursor(subscriptionId, List(cursor))
  }

  def onSubscribed(endpoint: String, cursor: Option[Cursor]): Unit = {
    log.debug("########## onSubscribed ############")
    log.debug("Endpoint " + endpoint)
    log.debug("Cursor " + cursor)
    log.debug("#####################################")

  }
}


object SubscriptionIT {

  def main(args: Array[String]): Unit = {

    import org.zalando.nakadi.client.scala.model.ScalaJacksonJsonMarshaller._
    implicit def typeRef: TypeReference[EventStreamBatch[MeetingsEvent]] =
      new TypeReference[EventStreamBatch[MeetingsEvent]] {}

    import scala.concurrent.ExecutionContext.Implicits.global
    val client: Client = ClientFactory.getScalaClient()

    client.initSubscription(Subscription("hecate",
                                          List("de.zalando.logistics.laas.hecate.test.process_status_changed"))) onComplete  {

      case Success(value) => value match {
        case Left(clientError) =>
           println(s">>>>> CLIENT ERROR: $clientError")
        case Right(received) =>
          println(s"subscription was successful: $received")

          received match{
            case Some(sub) =>

              val Some(subscriptionId) = sub.id
              val listener = new EventCounterListener("Test", subscriptionId, client)

              client.subscribe(
                subscriptionId,
                SubscriptionStreamParameters(),
                listener
              )

            case None =>
              println("did not receive anything  :-( ")
          }
      }
      case Failure(e) =>
          println("received error from Future")
          e.printStackTrace()
    }





    Thread.sleep(10000)
  }

}
