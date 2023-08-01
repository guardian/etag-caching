package com.gu.etagcaching

import com.github.blemale.scaffeine.{AsyncLoadingCache, Scaffeine}
import com.gu.etagcaching.FreshnessPolicy.{AlwaysWaitForRefreshedValue, TolerateOldValueWhileRefreshing}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.concurrent.TimeLimits.failAfter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FreshnessPolicyTest extends AnyFlatSpec with Matchers with ScalaFutures {

  case class ExampleCacheWithPolicy(policy: FreshnessPolicy) {
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

    val reading = policy.on(exampleCache)

    def read() = reading("sample-key").futureValue

  }

  "AlwaysWaitForRefreshedValue policy" should "always give us the latest, refreshed value" in {
    val demo = ExampleCacheWithPolicy(AlwaysWaitForRefreshedValue)
    demo.read() shouldBe 0
    demo.read() shouldBe 1
  }

  "TolerateOldValueWhileRefreshing policy" should "return instantly if there's an available old value" in {
    val demo = ExampleCacheWithPolicy(TolerateOldValueWhileRefreshing)

    demo.read() shouldBe 0
    failAfter(2.millis) { // should be instant, because we're _not_ waiting for the ETag-checking fetch
      demo.read() shouldBe 0
    }
  }
}
