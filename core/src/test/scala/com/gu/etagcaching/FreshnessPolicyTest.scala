package com.gu.etagcaching

import com.github.blemale.scaffeine.{AsyncLoadingCache, Scaffeine}
import com.gu.etagcaching.FreshnessPolicy.{AlwaysWaitForRefreshedValue, TolerateOldValueWhileRefreshing}
import com.gu.etagcaching.fetching.{ETaggedData, Fetching, MissingOrETagged}
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.concurrent.TimeLimits.failAfter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar._

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}

class FreshnessPolicyTest extends AnyFlatSpec with Matchers with ScalaFutures with OptionValues {

  case class DemoCache(policy: FreshnessPolicy) {
    import scala.concurrent.ExecutionContext.Implicits.global

    lazy val exampleCache: AsyncLoadingCache[String, Int] = {
      def simulateWork(task: String, key: String, millis: Long): Unit = {
        println(s"$task begin: $key")
        Thread.sleep(millis)
        println(s"$task end: $key")
      }

      Scaffeine().buildAsyncFuture[String, Int](
        loader = key => Future {
          simulateWork("load", key, 50)
          0
        },
        reloadLoader = Some { case (key, oldValue) => Future {
          simulateWork("reload", key, 10)
          oldValue + 1
        }
        }
      )
    }

    private val reading = policy.on(exampleCache)

    def read() = reading("sample-key").futureValue

  }

  "AlwaysWaitForRefreshedValue policy" should "always give us the latest, refreshed value, even tho' it takes some time to make the ETag-checking fetch" in {
    val demo = DemoCache(AlwaysWaitForRefreshedValue)
    demo.read() shouldBe 0
    demo.read() shouldBe 1
  }

  "TolerateOldValueWhileRefreshing policy" should "return instantly if there's an available old value" in {
    val demo = DemoCache(TolerateOldValueWhileRefreshing)

    demo.read() shouldBe 0
    failAfter(5.millis) { // should be instant, because we're _not_ waiting for the ETag-checking fetch
      demo.read() shouldBe 0
    }
  }

  it should "mean that ETagCache won't make additional requests if a value is cached locally" in {
    val fetching: Fetching[String, Int] = TestFetching.withIncrementingValues

    val eTagCache = new ETagCache[String, Int](
      fetching.thenParsing(identity),
      TolerateOldValueWhileRefreshing,
      _.maximumSize(1).expireAfterWrite(100.millis)
    )(ExecutionContext.Implicits.global)

    eTagCache.get("KEY").futureValue.value shouldBe 0
    eTagCache.get("KEY").futureValue.value shouldBe 0
    eTagCache.get("KEY").futureValue.value shouldBe 0
    Thread.sleep(200)
    eTagCache.get("KEY").futureValue.value shouldBe 1
    Thread.sleep(30)
    eTagCache.get("KEY").futureValue.value shouldBe 1
    Thread.sleep(30)
    eTagCache.get("KEY").futureValue.value shouldBe 1
  }
}
