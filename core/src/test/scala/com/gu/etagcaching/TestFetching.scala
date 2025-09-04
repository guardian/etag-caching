package com.gu.etagcaching

import com.gu.etagcaching.fetching.{ETaggedData, Fetching, Missing, MissingOrETagged}

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}

object TestFetching {

  def withIncrementingValues: Fetching[String, Int] = new Fetching[String, Int] {
    val counter = new AtomicInteger()

    override def fetch(key: String): Future[MissingOrETagged[Int]] = {
      val count = counter.getAndIncrement()
      Future.successful(ETaggedData(count.toString, count))
    }
    override def fetchOnlyIfETagChanged(key: String, eTag: String): Future[Option[MissingOrETagged[Int]]] =
      fetch(key).map(Some(_))(ExecutionContext.parasitic)
  }

  /**
   * Create a test [[Fetching]] instance with a (possibly impure) `lookup` function.
   *
   * The [[Fetching]] instance will use the object hashcode to create the object's ETag.
   *
   * The `lookup` function can return different values for the same input if we want to simulate
   * the value for a key changing over time.
   */
  def withLookup[K, V](lookup: K => Option[V]): Fetching[K, V] = new Fetching[K, V] {
    override def fetch(key: K): Future[MissingOrETagged[V]] = Future.successful {
      lookup(key).fold[MissingOrETagged[V]](Missing) { value =>
        ETaggedData(value.hashCode().toString, value)
      }
    }
    override def fetchOnlyIfETagChanged(key: K, ETag: String): Future[Option[MissingOrETagged[V]]] =
      fetch(key).map {
        case ETaggedData(ETag, _) => None
        case other => Some(other)
      }(ExecutionContext.parasitic)
  }
}
