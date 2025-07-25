package com.gu.etagcaching

import com.gu.etagcaching.FreshnessPolicy.TolerateOldValueWhileRefreshing
import com.gu.etagcaching.Loading.Update
import com.gu.etagcaching.fetching.Fetching
import org.scalatest.OptionValues
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class LoadingTest extends AnyFlatSpec with Matchers with ScalaFutures with OptionValues with Eventually {
  "onUpdate" should "give callbacks that allow logging updates" in {
    val updates: mutable.Buffer[Update[String, Int]] = mutable.Buffer.empty

    val fetching: Fetching[String, Int] = TestFetching.withIncrementingValues

    val cache = new ETagCache(
      fetching.thenParsing(identity).onUpdate { update =>
        updates.append(update)
        println(s"Got an update: $update")
      },
      TolerateOldValueWhileRefreshing,
      _.maximumSize(1).refreshAfterWrite(100.millis)
    )

    val expectedUpdates = Seq(
      Update("key", None, Some(0)),
      Update("key", Some(0), Some(1))
    )

    cache.get("key").futureValue shouldBe Some(0)
    updates shouldBe expectedUpdates.take(1)

    Thread.sleep(105)

    eventually { cache.get("key").futureValue shouldBe Some(1) }
    updates.toSeq shouldBe expectedUpdates

    Thread.sleep(105)
    updates.toSeq shouldBe expectedUpdates // No updates if we're not requesting the key from the cache
  }
}
