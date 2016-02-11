package org.zalando.nakadi.client

import java.net.URI

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import de.zalando.scoop.{ScoopClient, Scoop}
import org.zalando.nakadi.client.actor.KlientSupervisor.{NewScoopAwareSubscription, NewSubscription}
import org.zalando.nakadi.client.actor.ScoopListenerActor
import org.zalando.nakadi.client.actor.ScoopListenerActor.SCOOP_LISTENER_ID_PREFIX
import scala.concurrent.ExecutionContext.Implicits.global

object ScoopAwareNakadiKlient {

  def initActorSystem(scoopOption: Option[Scoop]): Option[ActorSystem] ={
    val scoop = scoopOption.getOrElse(throw new IllegalArgumentException("no Scoop builder given"))
    Some(
      ActorSystem("scoop-nakadi-client", ConfigFactory.defaultApplication()
                                            .withFallback(scoop.buildConfiguration()))
    )
  }
}

class ScoopAwareNakadiKlient(endpoint: URI,
                             port: Int,
                             securedConnection: Boolean,
                             tokenProvider: () => String,
                             scoopOption: Option[Scoop],
                             scoopTopicOption: Option[String])
  extends KlientImpl(endpoint,
                     port,
                     securedConnection,
                     tokenProvider,
                     ScoopAwareNakadiKlient.initActorSystem(scoopOption)) {

  val scoopTopic = scoopTopicOption.getOrElse(throw new IllegalArgumentException("no Scoop topic specified"))
  val scoop = scoopOption.getOrElse(throw new IllegalArgumentException("no Scoop builder given"))

  scoop.startScoopActor(system)



  override def listenForEvents(topic: String,
                               partitionId: String,
                               parameters: ListenParameters,
                               listener: Listener,
                               autoReconnect: Boolean = false): Unit = {
    checkNotNull(topic, "topic must not be null")
    checkNotNull(partitionId, "partitionId must not be null")
    checkNotNull(parameters, "list parameters must not be null")
    checkNotNull(listener, "listener must not be null")
    checkNotNull(autoReconnect, "autoReconnect must not be null")

    checkExists(parameters.batchFlushTimeoutInSeconds, "batchFlushTimeoutInSeconds is not set")
    checkExists(parameters.batchLimit, "batchLimit is not set")
    checkExists(parameters.startOffset, "startOffset is not specified")
    checkExists(parameters.streamLimit, "streamLimit is not specified")

    if(listener.id.startsWith(SCOOP_LISTENER_ID_PREFIX)) // TODO find cleaner solution to avoid infinite recursion with ScoopListenerActor
      super.listenForEvents(topic, partitionId, parameters, listener, autoReconnect)
    else
      supervisor !  NewScoopAwareSubscription(topic,
                                              partitionId,
                                              parameters,
                                              autoReconnect,
                                              listener,
                                              this,
                                              scoop.defaultClient(),
                                              scoopTopic)
  }
}
