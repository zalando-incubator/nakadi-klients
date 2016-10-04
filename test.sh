export OAUTH2_TOKEN=$(zign token)
export NAKADI_KLIENTS_IT_ENVIRONMENT=SANDBOX

sbt it/test:"run-main org.zalando.nakadi.client.scala.SubscriptionIT"
