package com.gu.etagcaching.fetching

import com.gu.etagcaching.Loading

import scala.concurrent.{ExecutionContext, Future}

trait Fetching[K, Response] {
  def fetch(key: K)(implicit ec: ExecutionContext): Future[ETaggedData[Response]]

  def fetchOnlyIfETagChanged(key: K, eTag: String)(implicit ec: ExecutionContext): Future[Option[ETaggedData[Response]]]

  def thenParsing[V](parse: Response => V): Loading[K, V] = Loading.by(this)(parse)
}
