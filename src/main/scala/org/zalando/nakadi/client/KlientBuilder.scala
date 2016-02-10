package org.zalando.nakadi.client

import java.net.URI
import java.util.function.Supplier
import com.typesafe.scalalogging.LazyLogging
import de.zalando.scoop.Scoop

object KlientBuilder{
  def apply(endpoint: URI = null,
            port: Int = 8080,
            securedConnection: Boolean = false,
            tokenProvider: () => String = null,
            scoop: Option[Scoop] = None,
            scoopTopic: Option[Scoop] = None) =
      new KlientBuilder(endpoint, port, securedConnection, tokenProvider)
}

class KlientBuilder private (val endpoint: URI = null,
                             val port: Int,
                             val securedConnection: Boolean,
                             val tokenProvider: () => String = null,
                             val scoop: Option[Scoop] = None,
                             val scoopTopic: Option[String] = None)
  extends LazyLogging
{

  def this() = this(null, 8080, false, null, None, None)

  private def checkNotNull[T](subject: T): T =
                                   if(Option(subject).isEmpty) throw new NullPointerException else subject


  private def checkState[T](subject: T, predicate: (T) => Boolean, msg: String): T =
                                   if(predicate(subject)) subject else throw new IllegalStateException()


  def withEndpoint(endpoint: URI): KlientBuilder =
                                new KlientBuilder(checkNotNull(endpoint), port, securedConnection, tokenProvider, scoop)


  def withTokenProvider(tokenProvider: () => String): KlientBuilder =
                                new KlientBuilder(endpoint, port, securedConnection, checkNotNull(tokenProvider), scoop, scoopTopic)


  def withJavaTokenProvider(tokenProvider: Supplier[String]) = withTokenProvider(() => tokenProvider.get())


  // TODO param check
  def withPort(port: Int): KlientBuilder = new KlientBuilder(endpoint, port, securedConnection, tokenProvider, scoop, scoopTopic)


  def withSecuredConnection(securedConnection: Boolean = true) = new KlientBuilder(endpoint, port, securedConnection, tokenProvider, scoop, scoopTopic)


  def withScoop(scoop: Option[Scoop]) = new KlientBuilder(endpoint, port, securedConnection, tokenProvider, scoop, scoopTopic)

  def withScoopTopic(scoopTopic: Option[String]) = new KlientBuilder(endpoint, port, securedConnection, tokenProvider, scoop, scoopTopic)

  def build(): Klient =
    if(scoop.isDefined && scoopTopic.isDefined)
      new ScoopAwareNakadiKlient(
        checkState(endpoint, (s: URI) => Option(s).isDefined, "endpoint is not set -> try withEndpoint()"),
        checkState(port, (s: Int) => port > 0, s"port $port is invalid"),
        securedConnection,
        checkState(tokenProvider, (s: () => String) => Option(s).isDefined, "tokenProvider is not set -> try withTokenProvider()"),
        scoop,
        scoopTopic)
    else
      new KlientImpl(
                    checkState(endpoint, (s: URI) => Option(s).isDefined, "endpoint is not set -> try withEndpoint()"),
                    checkState(port, (s: Int) => port > 0, s"port $port is invalid"),
                    securedConnection,
                    checkState(tokenProvider, (s: () => String) => Option(s).isDefined, "tokenProvider is not set -> try withTokenProvider()")
      )


  def buildJavaClient(): Client = new JavaClientImpl(build())


  override def toString = s"KlientBuilder($endpoint, $tokenProvider)"
}
