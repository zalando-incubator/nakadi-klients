package org.zalando.nakadi.client

import java.net.URI
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler
import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.typesafe.scalalogging.LazyLogging

object KlientBuilder{
  def apply(endpoint: URI = null, port: Int = 8080, securedConnection: Boolean = false, tokenProvider: () => String = null, objectMapper: ObjectMapper = null) =
      new KlientBuilder(endpoint, port, securedConnection, tokenProvider, objectMapper)
}

class KlientBuilder private (val endpoint: URI = null, val port: Int, val securedConnection: Boolean, val tokenProvider: () => String = null, val objectMapper: ObjectMapper = null)
  extends LazyLogging
{
  // Why are you wrapping null tests like that? Just 'if (subject == null)' is possible. However, best to have "customs checking"
  // at the Java/Scala border and not to leak nulls to Scala, at all. AKa280116
  //
  // Also, would like non-error path to come first: subject else throw ... (like elsewhere you have)

  private def checkNotNull[T](subject: T): T =
                                   if(Option(subject).isEmpty) throw new NullPointerException else subject


  private def checkState[T](subject: T, predicate: (T) => Boolean, msg: String): T =
                                   if(predicate(subject)) subject else throw new IllegalStateException()


  def withEndpoint(endpoint: URI): KlientBuilder =
                                new KlientBuilder(checkNotNull(endpoint), port, securedConnection, tokenProvider, objectMapper)


  def withTokenProvider(tokenProvider: () => String): KlientBuilder =
                                new KlientBuilder(endpoint, port, securedConnection, checkNotNull(tokenProvider), objectMapper)

  // TODO param check
  def withPort(port: Int): KlientBuilder = new KlientBuilder(endpoint, port, securedConnection, tokenProvider, objectMapper)


  def withSecuredConnection(securedConnection: Boolean = true) = new KlientBuilder(endpoint, port, securedConnection, tokenProvider, objectMapper)


  def withObjectMapper(objectMapper: ObjectMapper): KlientBuilder =  {
    checkNotNull(objectMapper)
    objectMapper.registerModule(new DefaultScalaModule)
    new KlientBuilder(endpoint, port, securedConnection, tokenProvider, objectMapper)
  }


  def defaultObjectMapper: ObjectMapper = {
    new ObjectMapper()
      .registerModule(new DefaultScalaModule)
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES)
      .addHandler(new DeserializationProblemHandler() {
        override def handleUnknownProperty(ctxt: DeserializationContext, jp: JsonParser, deserializer: JsonDeserializer[_], beanOrClass: AnyRef, propertyName: String): Boolean = {
          logger.warn(s"unknown property occurred in JSON representation: [beanOrClass=$beanOrClass, property=$propertyName]")
          true
        }
      })
  }

  // Interesting. I see why you want to call 'build' with paranthesis ('build()'). Normally, if a function is defined
  // in Scala with '()' that indicates side effects (this is just a convention). But if I take those away from the
  // declaration, calling it as 'build()' no longer works. It should not make a difference. Need to check on this. AKa280116

  def build(): Klient = new KlientImpl(
                  checkState(endpoint,      (s: URI) => Option(s).isDefined, "endpoint is not set -> try withEndpoint()"),
                  checkState(port, (s: Int) => port > 0, s"port $port is invalid"),
                  securedConnection,
                  checkState(tokenProvider, (s: () => String) => Option(s).isDefined, "tokenProvider is not set -> try withTokenProvider()"),
                  Option(objectMapper).getOrElse(defaultObjectMapper)
  )


  def buildJavaClient(): Client = new JavaClientImpl(build())


  override def toString = s"KlientBuilder($endpoint, $tokenProvider, $objectMapper)"
}
