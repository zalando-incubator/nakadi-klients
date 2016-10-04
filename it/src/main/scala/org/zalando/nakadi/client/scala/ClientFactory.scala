package org.zalando.nakadi.client.scala

import org.zalando.nakadi.client.utils.ClientBuilder
import java.util.function.Supplier

import scala.util.Properties

object ClientFactory {
  import scala.language.postfixOps


  val ENV_NAKADI_HOST = "NAKADI_HOST"
  val ENV_NAKADI_PORT = "NAKADI_PORT"
  val ENV_OAUTH2_TOKEN = "OAUTH2_TOKEN"

  /**
   * Possible values are (case-insensitive):
   * - SANDBOX
   * - STAGING
   * - LOCAL
   */
  val ENV_NAKDI_KLIENTS_IT_ENVIRONMENT = "NAKADI_KLIENTS_IT_ENVIRONMENT"


  def OAuth2Token(): Option[() => String] =
    Option(() => Properties.envOrElse(ENV_OAUTH2_TOKEN ,"*"))


  def getJavaClient() = builder().buildJavaClient();


  def getScalaClient() = builder().build()


  private def builder() = stagingFromDev() match {
      case Some(staging) =>
        if(staging == "SANDBOX") useSandbox()
        else if(staging == "STAGING") useStaging()
        else useLocal() // LOCAL is always used as fallback
      case None => {
        useLocal()
      }
  }


  private def stagingFromDev() =
    Properties.envOrNone(ENV_NAKDI_KLIENTS_IT_ENVIRONMENT)
      .map(staging => staging.trim.toUpperCase)


  private def useLocal() = {
    new ClientBuilder() //
      .withHost(Properties.envOrElse(ENV_NAKADI_HOST, "localhost")) //
      .withPort(Properties.envOrElse(ENV_NAKADI_PORT, "8080").toInt) //
      .withSecuredConnection(false) // s
      .withVerifiedSslCertificate(false) // s
  }


  private def useSandbox() = {
    ClientBuilder()
      .withHost(Properties.envOrElse(ENV_NAKADI_HOST, "nakadi-sandbox.aruha-test.zalan.do"))
      .withPort(Properties.envOrElse(ENV_NAKADI_PORT, "443").toInt) //
      .withSecuredConnection(true) //s
      .withVerifiedSslCertificate(false) //s
      .withTokenProvider(ClientFactory.OAuth2Token())
  }


  private def useStaging() = {
    useSandbox().withHost(Properties.envOrElse(ENV_NAKADI_HOST, "nakadi-staging.aruha-test.zalan.do"))
  }
}
