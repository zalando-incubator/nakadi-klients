package org.zalando.nakadi.client.scala

import org.zalando.nakadi.client.scala.model.Cursor

case class StreamParameters(cursor: Option[Cursor] = None,
                            batchLimit: Option[Integer] = None,
                            streamLimit: Option[Integer] = None,
                            batchFlushTimeout: Option[Integer] = None,
                            streamTimeout: Option[Integer] = None,
                            streamKeepAliveLimit: Option[Integer] = None,
                            flowId: Option[String] = None,
                            rate: Option[Int] = None,
                            burstMaxSize: Option[Int] = None)
