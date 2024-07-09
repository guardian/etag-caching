package com.gu.etagcaching

import com.github.blemale.scaffeine.{AsyncLoadingCache, Scaffeine}
import com.gu.etagcaching.fetching.{ETaggedData, Missing, MissingOrETagged}

import scala.concurrent.{ExecutionContext, Future}

/**
 * ETagCache can be used when accessing services that support `ETag`s and conditional-fetching, like S3.
 * Advantages of using `ETagCache`:
 *
 *  - CPU and network bandwidth savings
 *  - Better recency than using a simple cache - content can be 'always-up-to-date'
 *  - From using the (S)Caffeine cache library: In-flight requests for a given key are unified, so 100
 *    simultaneous requests for a key will lead to just one fetch-and-parse
 *
 * ETagCache caches resolved-key-values along with their `ETag`, a content hash supplied & recognised by the
 * remote service. ETagCache (when used with a [[AlwaysWaitForRefreshedValue]] policy)
 * ''will'' always connect to the service for each request for a key-value, but where it already holds a
 * cached `ETaggedData`, it will send the `ETag` with it's request, and the service will return a
 * response indicating if the content has changed or not. If the content is unchanged, the response will
 * be blank, saving network bandwidth, and the old value can be used, saving CPU by not having to parse the
 * data again.
 *
 * Limitations:
 *  - Like any cache, you need to have enough RAM to ensure you can hold the objects you want in memory.
 *
 * @tparam K The 'key' or resource identifier type - for instance, a URL or S3 Object Id.
 * @tparam V The 'value' for the key - a parsed representation of whatever was in the resource data.
 */
class ETagCache[K, V](
  loading: Loading[K, V],
  freshnessPolicy: FreshnessPolicy,
  configureCache: ConfigCache
)(implicit ec: ExecutionContext) {

  private val cache: AsyncLoadingCache[K, MissingOrETagged[V]] = configureCache(Scaffeine()).buildAsyncFuture[K, MissingOrETagged[V]](
    loader = loading.fetchAndParse,
    reloadLoader = Some(
       (key: K, old: MissingOrETagged[V]) => old match {
         case Missing => loading.fetchAndParse(key)
         case oldETaggedData: ETaggedData[V] => loading.fetchThenParseIfNecessary(key, oldETaggedData)
       }
    ))

  private val read = freshnessPolicy.on(cache)

  def get(key: K): Future[Option[V]] = read(key).map(_.toOption)
}
