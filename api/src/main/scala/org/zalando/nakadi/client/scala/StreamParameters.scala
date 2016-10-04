package org.zalando.nakadi.client.scala

import org.zalando.nakadi.client.scala.model.Cursor

case class StreamParameters(cursor: Option[Cursor] = None,
                            batchLimit: Option[Integer] = None,
                            streamLimit: Option[Integer] = None,
                            batchFlushTimeout: Option[Integer] = None,
                            streamTimeout: Option[Integer] = None,
                            streamKeepAliveLimit: Option[Integer] = None,
                            flowId: Option[String] = None) {
  def toQueryParamsMap(): Map[String, String] = {
    val parameters = List(
      ("batch_limit", batchLimit),
      ("stream_limit", streamLimit),
      ("batch_flush_timeout", batchFlushTimeout),
      ("stream_timeout", streamTimeout), //
      ("stream_keep_alive_limit", streamKeepAliveLimit)
    )

    (for {(key, optional) <- parameters; value <- optional} yield (key -> value.toString)).toMap
  }
}


/**
  * Stream parameters for the interaction with `/subscriptions` endpoint
  *
  * @param commitTimeout  The amount of time allowed between commits. If no commit is performed within this amount of time
  *                       the connection will be closed from Nakadi side. Specified in seconds. Default is 30
  * @param windowSize     The amount of uncommitted events Nakadi will stream before pausing the stream. When in paused
  *                       state and commit comes - the stream will continue. Minimal window size is 1. Default is 1000
  * @param commitMode     The commit mode that will be used for this stream.
  *                      Possible values:
  *                       - `commit_at_will`: client is free to choose how often he wants to commit. Of course the commit_timeout
  *                      property should still be respected. When a batch is committed that also automatically commits all previous
  *                      batches that were sent in a stream for this partition. This value is the default.
  *
  *                      - `commit_each`: this commit mode requires each batch to be committed, and it should be done in correct
  *                      order. Committing a cursor when the previous is not yet committed is not possible.
  *
  *                      - `auto_commit`: everything that Nakadi sends to stream is considered to be committed. No actions are
  *                      required from client side to commit. Be aware that with this commit mode loosing of some events is very
  *                      probable. So you should know what you are doing if you use autocommit.
  */
case class SubscriptionStreamParameters( commitTimeout: Option[Int] = None,
                                         windowSize: Option[Int] = None,
                                         commitMode: Option[String] = None,
                                         batchLimit: Option[Integer] = None,
                                         streamLimit: Option[Integer] = None,
                                         batchFlushTimeout: Option[Integer] = None,
                                         streamTimeout: Option[Integer] = None,
                                         streamKeepAliveLimit: Option[Integer] = None,
                                         flowId: Option[String] = None) {

  def toQueryParamsMap(): Map[String, String] = {
    val parameters = List(
      ("commit_timeout", commitTimeout),
      ("window_size", windowSize),
      ("commit_mode", commitMode),
      ("batch_limit", batchLimit),
      ("stream_limit", streamLimit),
      ("batch_flush_timeout", batchFlushTimeout),
      ("stream_timeout", streamTimeout), //
      ("stream_keep_alive_limit", streamKeepAliveLimit)
    )

    (for {(key, optional) <- parameters; value <- optional} yield (key -> value.toString)).toMap
  }
}