package com.gu.etagcaching.fetching

/**
  * @param eTag https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/ETag
  */
case class ETaggedData[T](eTag: String, result: T) {
  def map[S](f: T => S): ETaggedData[S] = copy(result = f(result))
}
