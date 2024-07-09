package com.gu.etagcaching.fetching

sealed trait MissingOrETagged[+T] {
  def map[S](f: T => S): MissingOrETagged[S]
  def toOption: Option[T]
}

case object Missing extends MissingOrETagged[Nothing] {
  override def map[S](f: Nothing => S): MissingOrETagged[S] = Missing
  override def toOption: Option[Nothing] = None
}

/**
  * @param eTag https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/ETag
  */
case class ETaggedData[T](eTag: String, result: T) extends MissingOrETagged[T] {
  override def map[S](f: T => S): ETaggedData[S] = copy(result = f(result))
  override def toOption: Option[T] = Some(result)
}
