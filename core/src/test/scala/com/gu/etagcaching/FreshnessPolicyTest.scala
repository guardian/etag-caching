package com.gu.etagcaching

import com.github.blemale.scaffeine.{AsyncLoadingCache, Scaffeine}
import com.gu.etagcaching.DemoCache.withPolicy
import com.gu.etagcaching.FreshnessPolicy.{AlwaysWaitForRefreshedValue, TolerateOldValueWhileRefreshing}
import com.gu.etagcaching.fetching.Fetching
import com.gu.etagcaching.testkit.TestFetching
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.concurrent.TimeLimits.failAfter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar._

import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

object DemoCache {
  def withPolicy(policy: FreshnessPolicy, repeats: Int)(f: DemoCache => Unit): Unit = for (n <- 1 to repeats) {
    val demoCache = DemoCache(policy)
    demoCache.log(s"Run $n")
    try {
      f(demoCache)
    } catch {
      case NonFatal(e) => throw new RuntimeException(demoCache.logs, e)
    }
  }
}

case class DemoCache(policy: FreshnessPolicy) extends ScalaFutures {
  
  private val logStore: ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue[String]()

  def logs: String = logStore.asScala.mkString("\n")

  def log(mess: String): Unit = logStore.add(mess)

  lazy val exampleCache: AsyncLoadingCache[String, Int] = {
    def simulateWork(task: String, key: String, millis: Long): Unit = {
      log(s"$task begin: $key")
      Thread.sleep(millis)
      log(s"$task end: $key")
    }

    Scaffeine().buildAsyncFuture[String, Int](
      loader = key => Future.successful {
        simulateWork("load", key, 15)
        0
      },
      reloadLoader = Some { case (key, oldValue) => Future.successful {
        simulateWork("reload", key, 10)
        oldValue + 1
      }
      }
    )
  }

  private val reading = policy.on(exampleCache)

  def read(): Int = reading("sample-key").futureValue
}

class FreshnessPolicyTest extends AnyFlatSpec with Matchers with ScalaFutures with OptionValues {

  "AlwaysWaitForRefreshedValue policy" should "always give us the latest, refreshed value, even tho' it takes some time to make the ETag-checking fetch" in {
    withPolicy(AlwaysWaitForRefreshedValue, repeats = 1000) { demo =>
      demo.read() shouldBe 0
      demo.read() shouldBe 1
    }
  }

  "TolerateOldValueWhileRefreshing policy" should "return instantly if there's an available old value" in {
    withPolicy(TolerateOldValueWhileRefreshing, repeats = 1000) { demo =>
      demo.read() shouldBe 0
      failAfter(4.millis) { // should be instant, because we're _not_ waiting for the ETag-checking fetch
        demo.read() shouldBe 0
      }
    }
  }

  it should "mean that ETagCache won't make additional requests if a value is cached locally" in {
    val fetching: Fetching[String, Int] = TestFetching.withIncrementingValues

    val eTagCache = new ETagCache[String, Int](
      fetching.thenParsing(identity)(ExecutionContext.parasitic),
      TolerateOldValueWhileRefreshing,
      _.maximumSize(1).expireAfterWrite(100.millis)
    )

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
