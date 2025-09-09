package com.gu.etagcaching

import com.gu.etagcaching.FreshnessPolicy.TolerateOldValueWhileRefreshing
import com.gu.etagcaching.Loading.Update
import com.gu.etagcaching.fetching.{ETaggedData, Fetching}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Inside, OptionValues}

import java.time.DayOfWeek.{SATURDAY, SUNDAY}
import java.time.temporal.ChronoUnit.DAYS
import java.time.{DayOfWeek, ZoneId, ZonedDateTime}
import java.util.Locale
import java.util.Locale.UK
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class LoadingTest extends AnyFlatSpec with Matchers with ScalaFutures with OptionValues with Eventually with Inside {
  val exampleFetchingDayLength: Fetching[ZonedDateTime, java.time.Duration] = TestFetching.withLookup { dateTime =>
    val day = dateTime.truncatedTo(DAYS)
    Some(java.time.Duration.between(day, day.plusDays(1)))
  }
  val momentInShortUKDay: ZonedDateTime = ZonedDateTime.of(2025, 3, 30, 21, 55, 56, 0, ZoneId.of("Europe/London"))
  val momentInNormalUSDay: ZonedDateTime = momentInShortUKDay.withZoneSameInstant(ZoneId.of("America/Chicago"))

  "Creating a Loading instance from a Fetching instance" should "work with 'thenParsing'" in {
    val loading: Loading[ZonedDateTime, Long] = exampleFetchingDayLength.thenParsing(_.toHours)

    loading.fetchAndParse(momentInShortUKDay).futureValue.toOption.value shouldBe 23
  }

  it should "work with 'thenParsingWithKey'" in {
    val loading: Loading[ZonedDateTime, String] =
      exampleFetchingDayLength.thenParsingWithKey((key, response) => s"${key.getZone.getId.split('/')(1)}: ${response.toHours} hours")

    loading.fetchAndParse(momentInShortUKDay).futureValue.toOption.value shouldBe "London: 23 hours"
    loading.fetchAndParse(momentInNormalUSDay).futureValue.toOption.value shouldBe "Chicago: 24 hours"
  }

  private def loadingFavouriteDayOfWeekInDifferentCountries() = new TestLoading[Locale, String, DayOfWeek](DayOfWeek.valueOf)

  "Loading" should "not do any parsing if fetching found no change" in {
    val sample = loadingFavouriteDayOfWeekInDifferentCountries()

    sample.dataStore(UK) = "SATURDAY"
    inside(sample.loading.fetchAndParse(UK).futureValue) {
      case initialLoad: ETaggedData[DayOfWeek] =>
        sample.countingParser.count() shouldBe 1
        initialLoad.result shouldBe SATURDAY

        sample.loading.fetchThenParseIfNecessary(UK, initialLoad).futureValue.toOption.value shouldBe SATURDAY
        sample.countingParser.count() shouldBe 1
    }
  }

  it should "do parsing when values change" in {
    val sample = loadingFavouriteDayOfWeekInDifferentCountries()

    sample.dataStore(UK) = "SATURDAY"
    inside(sample.loading.fetchAndParse(UK).futureValue) {
      case initialLoad: ETaggedData[DayOfWeek] =>
        sample.countingParser.count() shouldBe 1
        initialLoad.result shouldBe SATURDAY

        sample.dataStore(UK) = "SUNDAY"
        sample.loading.fetchThenParseIfNecessary(UK, initialLoad).futureValue.toOption.value shouldBe SUNDAY
        sample.countingParser.count() shouldBe 2
    }
  }


  "onUpdate" should "give callbacks that allow logging updates" in {
    val updates: mutable.Buffer[Update[String, Int]] = mutable.Buffer.empty

    val fetching: Fetching[String, Int] = TestFetching.withIncrementingValues

    val cache = new ETagCache(
      fetching.thenParsing(identity).onUpdate { update =>
        updates.append(update)
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

class TestLoading[K, Response, V](parser: Response => V) {
  val dataStore = mutable.Map[K, Response]()
  val countingParser = new CountingParser[Response, V](parser)
  val loading: Loading[K, V] = TestFetching.withLookup(dataStore.get).thenParsing(countingParser)
}