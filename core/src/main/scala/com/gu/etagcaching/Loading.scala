package com.gu.etagcaching

import com.gu.etagcaching.Loading.{OnUpdate, Update}
import com.gu.etagcaching.fetching.{ETaggedData, Fetching, MissingOrETagged}

import scala.concurrent.{ExecutionContext, Future}

/**
 * `Loading` represents the two sequential steps of getting something useful from a remote resource, specifically:
 *
 *  - Fetching
 *  - Parsing
 *
 * Our [[Fetching]] interface requires supporting conditional-fetching based on `ETag`s, which means that
 * we can short-cut the Parsing step if the resource hasn't changed.
 *
 * @tparam K The 'key' or resource identifier type - for instance, a URL or S3 Object Id.
 * @tparam V The 'value' for the key - a parsed representation of whatever was in the resource data.
 */
trait Loading[K, V] {
  def fetchAndParse(k: K): Future[MissingOrETagged[V]]

  /**
   * When we have ''old'' `ETaggedData`, we can send the `ETag` with the Fetch request, and the server will return
   * a blank HTTP 304 `Not Modified` response if the content hasn't changed - which means we DO NOT need to parse
   * any new data, and can just reuse our old data, saving us CPU time and network bandwidth.
   */
  def fetchThenParseIfNecessary(k: K, oldV: ETaggedData[V]): Future[MissingOrETagged[V]]

  /**
   * Add a handler for doing side-effectful logging of updates.
   */
  def onUpdate(handler: Update[K,V] => Unit)(implicit ec: ExecutionContext): Loading[K, V] = OnUpdate(this)(handler)
}

object Loading {
  def by[K, Response, V](fetching: Fetching[K, Response])(parse: Response => V)(implicit parsingEC: ExecutionContext): Loading[K, V] = new Loading[K, V] {
    def fetchAndParse(key: K): Future[MissingOrETagged[V]] =
      fetching.fetch(key).map(_.map(parse))

    def fetchThenParseIfNecessary(key: K, oldV: ETaggedData[V]): Future[MissingOrETagged[V]] =
      fetching.fetchOnlyIfETagChanged(key, oldV.eTag).map {
        case None => oldV // we got HTTP 304 'NOT MODIFIED': there's no new data - old data is still valid
        case Some(freshResponse) => freshResponse.map(parse)
      }
  }

  /**
   * Represents an update event for a given key.
   */
  case class Update[K, V](key: K, oldV: Option[V], newV: Option[V])

  /**
   * Wrapper round an underlying instance of Loading which adds handler for doing side-effectful logging of updates.
   *
   * @param handlerEC ExecutionContext for the `handler` function to run in
   */
  case class OnUpdate[K, V](underlying: Loading[K, V])(handler: Update[K,V] => Unit)(implicit handlerEC: ExecutionContext)
    extends Loading[K, V] {

    override def fetchAndParse(key: K): Future[MissingOrETagged[V]] =
      handle(key, None, underlying.fetchAndParse(key))

    override def fetchThenParseIfNecessary(key: K, oldV: ETaggedData[V]): Future[MissingOrETagged[V]] =
      handle(key, oldV.toOption, underlying.fetchThenParseIfNecessary(key, oldV))

    private def handle(key: K, oldV: Option[V], fut: Future[MissingOrETagged[V]]): Future[MissingOrETagged[V]] = {
      for {
        wrappedNewV <- fut
      } {
        handler(Update(key, oldV, wrappedNewV.toOption))
      }
      fut
    }
  }
}

