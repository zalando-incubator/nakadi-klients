package org.zalando.nakadi.client.actor

import java.util.UUID

import akka.actor.Props
import akka.cluster.{Member, Cluster}
import com.typesafe.scalalogging.Logger
import de.zalando.scoop.ScoopCommunication.NewScoopListener
import de.zalando.scoop.{ScoopListener, ScoopClient}
import org.slf4j.LoggerFactory
import org.zalando.nakadi.client._
import scala.concurrent.ExecutionContext.Implicits.global

object ScoopListenerActor{
  val UNREACHABLE_MEMBER_EVENT_TYPE: String = "/scoop-system/unreachable-member"
  val UNREACHABLE_MEMBER_EVENT_BODY_KEY: String = "unreachable_member"
  val SCOOP_LISTENER_ID_PREFIX: String = "scoop-listener"

  private case class MemberUp(member: Member)
  private case class MemberUnreachable(member: Member)
  private case class ClusterInit(cluster: Cluster)
  private case class MemberRemoved(member: Member)

  private case class NakadiEventReceived(topic: String, partition: String, cursor: Cursor, event: Event)

  def props(listener: Listener, klient: Klient, scoopClient: ScoopClient, scoopTopic: String) =
                                                    Props(new ScoopListenerActor(listener, klient, scoopClient, scoopTopic))
}

class ScoopListenerActor(listener: Listener,
                         val klient: Klient,
                         val scoopClient: ScoopClient,
                         val scoopTopic: String) extends ListenerActor(listener)
                                                 with Listener
                                                 with ScoopListener {
  import ScoopListenerActor._

  val logger = Logger(LoggerFactory.getLogger("ScoopListenerActor"))

  var currentCluster: Option[Cluster] = None

  var mayProcess: Boolean = false

  val ID = s"$SCOOP_LISTENER_ID_PREFIX-${System.nanoTime()}"

  val SELECTOR_FIELD = "id" // TODO make attribute configurable

  klient.subscribeToTopic(scoopTopic, ListenParameters(), this)

  override def preStart() = {
    log.info("starting ScoopActorListener for [listener.id={}]", listener.id)
    context.system.eventStream.publish(new NewScoopListener(this))
    super.preStart()
  }

  override def postStop() = {
    logger.info("stopping ScoopActorListener for [listener.id={}]", listener.id)
    klient.unsubscribeTopic(scoopTopic, this)
    super.postStop()
  }

  override def receive: Receive = {
    case MemberUp(member: Member) =>
      handleMemberUp(member)
    case MemberUnreachable(member: Member) =>
      handleMemberUnreachable(member)
    case ClusterInit(cluster: Cluster) =>
      handleClusterInit(cluster)
    case MemberRemoved(member: Member) =>
      handleMemberRemoved(member)
    case NakadiEventReceived(topic: String, partition: String, cursor: Cursor, event: Event) =>
      handleNakadiReceive(topic, partition, cursor, event)
    case _ => super.receive
  }

  def handleMemberUp(member: Member): Unit = if ((! mayProcess) && isItMe(member)) {
    mayProcess = true
    logger.info("I am allowed to process events as my cluster can see me again :-)")
  }

  /**
   * If this instance is the cluster leader, it has to notify all other members about unreachable member
   * because this allows the unreachable member to get known about the fact it is not reachable by the cluster.
   * As a consequence, the unreachable member can stop processing events as it is not considered by the cluster
   * anymore.
   */
  def handleMemberUnreachable(member: Member): Unit = {
    currentCluster match {
      case Some(actualCluster) =>
        if(actualCluster.selfRoles.contains("leader")) { // TODO right role name?
          logger.info("I AM the LEADER and I am notifying other Scoop aware clients about [unreachableMember={}]", member)
          val event = Event(UNREACHABLE_MEMBER_EVENT_TYPE,
            "scoop-system",
            Map("id" -> UUID.randomUUID().toString),
            Map(UNREACHABLE_MEMBER_EVENT_BODY_KEY -> member.address.toString))

          klient.postEvent(scoopTopic, event).map(error =>
            logger.warn("could not notify cluster about unreachable member [error={}]", error))
        }
        else logger.debug("received event about [unreachableMember={}] but I am NOT the LEADER -> ignored", member)

      case None => logger.warn("I have not been initialized with a cluster instance yet " +
        "-> ignoring MemberUnreachable([member={}])", member)
    }
  }

  def handleClusterInit(cluster: Cluster): Unit = {
    currentCluster = Option(cluster)
    mayProcess = currentCluster.isDefined
  }

  def handleNakadiReceive(topic: String, partition: String, cursor: Cursor, event: Event): Unit = event match {
    case Event(UNREACHABLE_MEMBER_EVENT_TYPE, _,  _, body) =>
      logger.info("received event of [eventType={}] -> [event={}]", UNREACHABLE_MEMBER_EVENT_TYPE, event)
      body.asInstanceOf[Map[String, AnyRef]].get(UNREACHABLE_MEMBER_EVENT_BODY_KEY) match {
        case Some(unreachableMember: String) =>
          currentCluster match {
            case Some(actualCluster) =>
              val meAsMemberString = actualCluster.selfAddress.toString
              if(unreachableMember == meAsMemberString) {
                logger .info("I [address={}] am NOT reachable by the rest of my cluster :-( " +
                  "-> disconnecting" + " (you might consider restarting this instance)",
                  meAsMemberString)
                mayProcess = false
              }
            case None => logger.warn("I have not been initialized with a cluster instance yet " +
                                     "-> ignoring [event={}]", event)
          }
        case Some(unknown) => logger.warn("could not [entry={}] from body of [event={}] as String entry is expected " +
                                          "-> ignored", UNREACHABLE_MEMBER_EVENT_BODY_KEY, event)
        case None =>
          logger.warn("[event={}] does not contain [bodyKey={}] -> ignored", event, UNREACHABLE_MEMBER_EVENT_BODY_KEY)
      }
    case event: Event if(mayProcess) =>
      event.metadata.get(SELECTOR_FIELD) match {
        case Some(value: String) =>
          if (scoopClient.isHandledByMe(value)) {
            logger.debug("event [{}={}] is handled by me", SELECTOR_FIELD, value)
            listener.onReceive(topic, partition, cursor, event)
          }
          else logger.debug("event [{}={}] is NOT handled by me", SELECTOR_FIELD, value)
        case Some(unknown) =>
          logger.warn("[{}={}] is NOT a String -> there must be configuration problem -> ignored",
                      SELECTOR_FIELD, unknown.toString)
        case None =>
          logger.warn("[field={}] is not set in [event={}] -> passed to listener", SELECTOR_FIELD)
          listener.onReceive(topic, partition, cursor, event)
      }
  }

  def handleMemberRemoved(member: Member): Unit = if (mayProcess && isItMe(member)) {
    mayProcess = false
    logger.info("I have been removed from the cluster and therefore I refuse to process any events from now on" +
                " -> you should consider restarting the instance")
  }

  def isItMe(member: Member): Boolean = currentCluster match {
    case Some(actualCluster) => actualCluster.selfAddress == member.address
    case None => false
  }


  /*
   * Nakadi Listener implementation
   */
  override def id: String = ID

  /*
   * Needs to be stashed together with other notifications because the ScoopListener
   * notifications and Scoop specific Nakadi events influence the channel logic
   */
  override def onReceive(topic: String, partition: String, cursor: Cursor, event: Event): Unit = {
    log.debug("[listener.id={}, mayProcess={}] received [cursor={}, event={}]", listener.id, mayProcess, cursor, event)
    self ! NakadiEventReceived(topic, partition, cursor, event)
  }


  override def onConnectionOpened(topic: String, partition: String): Unit =
    listener.onConnectionOpened(topic, partition)

  override def onConnectionClosed(topic: String, partition: String, lastCursor: Option[Cursor]): Unit =
    listener.onConnectionClosed(topic, partition, lastCursor)

  override def onConnectionFailed(topic: String, partition: String, status: Int, error: String): Unit =
    listener.onConnectionFailed(topic, partition, status, error)


  /*
   * ScoopListener implementation
   */
  override def onMemberUp(member: Member): Unit = self ! MemberUp(member)

  override def onMemberUnreachable(member: Member): Unit = self ! MemberUnreachable(member)

  override def init(cluster: Cluster): Unit = self ! ClusterInit(cluster)

  override def onRebalanced(i: Int, i1: Int): Unit = ( /* do nothing */ )

  override def onMemberRemoved(member: Member): Unit = self ! MemberRemoved(member)
}
