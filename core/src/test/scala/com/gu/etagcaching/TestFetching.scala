package com.gu.etagcaching

import com.gu.etagcaching.fetching.{ETaggedData, Fetching, MissingOrETagged}

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}

object TestFetching {

  def withIncrementingValues: Fetching[String, Int] = new Fetching[String, Int] {
    val counter = new AtomicInteger()

    override def fetch(key: String)(implicit ec: ExecutionContext): Future[MissingOrETagged[Int]] = {
      val count = counter.getAndIncrement()
      Future.successful(ETaggedData(count.toString, count))
    }
    override def fetchOnlyIfETagChanged(key: String, eTag: String)(implicit ec: ExecutionContext): Future[Option[MissingOrETagged[Int]]] =
      fetch(key)(ec).map(Some(_))
  }
}
