package com.gu.etagcaching

import com.gu.etagcaching.FreshnessPolicy.AlwaysWaitForRefreshedValue
import com.gu.etagcaching.Loading.Update
import com.gu.etagcaching.LoadingTest.TestApparatus
import com.gu.etagcaching.fetching.{ETaggedData, Fetching}
import com.gu.etagcaching.testkit.{CountingParser, TestFetching}
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.matchers.should.Matchers._
import org.scalatest.{Inside, OptionValues}

import java.time.DayOfWeek
import java.time.DayOfWeek.{MONDAY, SATURDAY, THURSDAY}
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField.DAY_OF_WEEK
import java.util.Locale
import java.util.Locale.{FRANCE, GERMANY, UK}
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global

object LoadingTest {
  /**
   * Uses a mock (optionally mutable) Map 'dataStore'. Provides an instance of [[Loading]] that fetches from that
   * datastore, and parses using the provided parser (counting how many times that parsing occurs).
   */
  class TestApparatus[K, Response, V](dataStore: scala.collection.Map[K, Response])(parser: Response => V) {
    private val countingParser = new CountingParser[Response, V](parser)
    val loading: Loading[K, V] = TestFetching.withStubDataStore(dataStore).thenParsing(countingParser)

    def parseCount(): Long = countingParser.count()

    def parsesCountedDuringConditionalLoadOf(k: K, oldV: ETaggedData[V]): Long = {
      val before = parseCount()
      loading.fetchThenParseIfNecessary(k, oldV).futureValue.toOption.value shouldBe parser(dataStore(k))
      parseCount() - before
    }
  }
}

class LoadingTest extends AnyFlatSpec with Matchers with ScalaFutures with OptionValues with Eventually with Inside {

  "Creating a Loading instance from a Fetching instance" should "be done with 'thenParsing'" in {
    val fetching: Fetching[Locale, String] =
      TestFetching.withStubDataStore(Map(FRANCE -> "THURSDAY", GERMANY -> "MONDAY"))

    val loading: Loading[Locale, DayOfWeek] = fetching.thenParsing(DayOfWeek.valueOf)

    loading.fetchAndParse(FRANCE).futureValue.toOption.value shouldBe THURSDAY
    loading.fetchAndParse(GERMANY).futureValue.toOption.value shouldBe MONDAY
  }

  it should "be possible with 'thenParsingWithKey', if we need the key for the 'parsing' phase" in {
    val fetching: Fetching[Locale, String] =
      TestFetching.withStubDataStore(Map(FRANCE -> "jeudi", GERMANY -> "Montag"))

    val loading: Loading[Locale, DayOfWeek] = fetching.thenParsingWithKey { (locale, response) =>
      DayOfWeek.of(DateTimeFormatter.ofPattern("EEEE", locale).parse(response).get(DAY_OF_WEEK))
    }

    loading.fetchAndParse(FRANCE).futureValue.toOption.value shouldBe THURSDAY
    loading.fetchAndParse(GERMANY).futureValue.toOption.value shouldBe MONDAY
  }

  "fetchThenParseIfNecessary" should "*only* do parsing if fetching found a change in ETag value" in {
    val dataStore = mutable.Map(UK -> "SATURDAY")
    val testApparatus = new TestApparatus(dataStore)(parser = DayOfWeek.valueOf)

    inside(testApparatus.loading.fetchAndParse(UK).futureValue) { case initialLoad: ETaggedData[DayOfWeek] =>
      testApparatus.parseCount() shouldBe 1
      initialLoad.result shouldBe SATURDAY

      // No additional parse performed, as UK value's ETag unchanged
      testApparatus.parsesCountedDuringConditionalLoadOf(UK, initialLoad) shouldBe 0

      dataStore(UK) = "MONDAY"

      // UK's ETag changed, we must parse the new value!
      testApparatus.parsesCountedDuringConditionalLoadOf(UK, initialLoad) shouldBe 1
    }
  }

  "onUpdate" should "provide callbacks that allow logging updates" in {
    val dataStore = mutable.Map(UK -> "SATURDAY")
    val testApparatus = new TestApparatus(dataStore)(parser = DayOfWeek.valueOf)

    val updates: mutable.Buffer[Update[Locale, DayOfWeek]] = mutable.Buffer.empty

    val cache = new ETagCache(
      testApparatus.loading.onUpdate(update => updates.append(update)),
      AlwaysWaitForRefreshedValue,
      _.maximumSize(1)
    )

    val expectedUpdates = Seq(
      Update(UK, None, Some(SATURDAY)),
      Update(UK, Some(SATURDAY), Some(MONDAY))
    )

    cache.get(UK).futureValue shouldBe Some(SATURDAY)
    eventually(updates should contain theSameElementsInOrderAs expectedUpdates.take(1))

    dataStore(UK) = "MONDAY"

    cache.get(UK).futureValue shouldBe Some(MONDAY)
    eventually(updates should contain theSameElementsInOrderAs expectedUpdates)
  }
}
