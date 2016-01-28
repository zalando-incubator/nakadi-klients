package org.zalando.nakadi.client


trait Listener {
  def id: String
  def onReceive(topic: String, partition: String, cursor: Cursor, event: Event)
  def onConnectionOpened(topic: String, partition: String)
  def onConnectionFailed(topic: String, partition: String, status: Int, error: String)
  def onConnectionClosed(topic: String, partition: String, lastCursor: Option[Cursor])
}
