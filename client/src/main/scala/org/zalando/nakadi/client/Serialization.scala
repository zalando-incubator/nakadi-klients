package org.zalando.nakadi.client


/**
 *
 */
trait NakadiDeserializer[T] {
  def fromJson(from: String): T
}

/**
 *
 */
trait NakadiSerializer[T]  {
  def toJson(from: T): String
}

