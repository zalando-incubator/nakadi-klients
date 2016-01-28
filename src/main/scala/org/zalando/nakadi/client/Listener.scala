package org.zalando.nakadi.client


trait Listener {
  def id: String
  def onReceive(topic: String, partition: String, cursor: Cursor, event: Event): Unit
  def onConnectionOpened(topic: String, partition: String): Unit
  def onConnectionFailed(topic: String, partition: String, status: Int, error: String): Unit
  def onConnectionClosed(topic: String, partition: String, lastCursor: Option[Cursor]): Unit
}
