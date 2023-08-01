package com.gu.etagcaching

import com.github.blemale.scaffeine.AsyncLoadingCache

import scala.concurrent.Future

sealed trait FreshnessPolicy {
  def on[K, V](cache: AsyncLoadingCache[K, V]): K => Future[V]
}

object FreshnessPolicy {
  /**
   * The values returned by the cache will **always** be up-to-date -
   * the Future will not complete until any cached value has had
   * its ETag checked, and a new value has been computed if the ETag
   * has changed.
   *
   * This may be a good choice for situations where you know the
   * resulting value may be cached (eg by the CDN) for a 'long'
   * time (eg, a Front getting cached for 1 minute), and you'd
   * rather *not* have to wait _another_ additional minute for the
   * next request to come through to get the refreshed value -
   * "it's acceptable to have a Front 1 minute out of date, but
   * doubling that time is hard to justify".
   */
  case object AlwaysWaitForRefreshedValue extends FreshnessPolicy {
    def on[K, V](cache: AsyncLoadingCache[K, V]): K => Future[V] = {
      val syncCache = cache.synchronous()

      { k => syncCache.refresh(k) }
    }
  }

  /**
   * The cache will return the old value for a key if it's still available,
   * not waiting for any refresh to complete.
   *
   * This may be suitable in high-traffic situations, where you know that
   * even if a stale value is used, another request will be coming in a
   * short period of time to get the new value. You want to give a near-instant
   * response to the user without them having to wait for a big download/parse,
   * and it doesn't matter if one or two people briefly see old data.
   */
  case object TolerateOldValueWhileRefreshing extends FreshnessPolicy {
    def on[K, V](cache: AsyncLoadingCache[K, V]): K => Future[V] = cache.get
  }
}