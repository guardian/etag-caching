package com.gu.etagcaching.aws.sdkv2.s3

import com.adobe.testing.s3mock.testcontainers.S3MockContainer
import com.gu.etagcaching.ETagCache
import com.gu.etagcaching.FreshnessPolicy.AlwaysWaitForRefreshedValue
import com.gu.etagcaching.aws.s3.ObjectId
import com.gu.etagcaching.aws.sdkv2.s3.ExampleParser.parseFruit
import com.gu.etagcaching.aws.sdkv2.s3.S3ClientForS3Mock.createS3clientFor
import com.gu.etagcaching.aws.sdkv2.s3.response.Transformer.Bytes
import org.scalatest.{BeforeAndAfter, OptionValues}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.PutObjectRequest

import java.io.File
import java.util.zip.GZIPInputStream
import scala.compat.java8.FutureConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class S3ObjectFetchingTest extends AnyFlatSpec with Matchers with ScalaFutures with OptionValues with IntegrationPatience with BeforeAndAfter {
  val ExampleS3Object: ObjectId = ObjectId("test-bucket", "path")
  val ExampleMissingS3Object: ObjectId = ObjectId("test-bucket", "nothing-should-be-here")

  val s3Mock: S3MockContainer = new S3MockContainer("latest").withInitialBuckets(ExampleS3Object.bucket)
  before(s3Mock.start())
  after(s3Mock.stop())
  lazy val s3Client: S3AsyncClient = createS3clientFor(s3Mock) // lazy val because we need the s3Mock to start first

  "S3ObjectFetching" should "have an example to show how an S3-backed ETagCache is set up" in {
    val fruitCache = new ETagCache[ObjectId, Fruit](
      S3ObjectFetching(s3Client, Bytes).timing(
        successWith = d => println(s"Success: $d"),
        notModifiedWith = d => println(s"Not modified: $d")
      ).thenParsing {
        bytes =>
          // Scala 2.13+ : Using(new GZIPInputStream(bytes.asInputStream()))(parseFruit).get
          parseFruit(new GZIPInputStream(bytes.asInputStream())) // Scala 2.12
      },
      AlwaysWaitForRefreshedValue,
      _.maximumSize(500).expireAfterAccess(1.hour)
    )

    uploadFile("banana.xml.gz")
    fruitCache.get(ExampleS3Object).futureValue.value.colour shouldBe "yellow"
    fruitCache.get(ExampleS3Object).futureValue.value.colour shouldBe "yellow"

    uploadFile("kiwi.xml.gz")
    fruitCache.get(ExampleS3Object).futureValue.value.colour shouldBe "green"
    // Note that the value is correct, without us explicitly clearing the cache - ETag-checking saved us!

    fruitCache.get(ExampleMissingS3Object).futureValue shouldBe None
  }

  private def uploadFile(demoFile: String): Unit = {
    val path = new File(getClass.getClassLoader.getResource("demo-files/" + demoFile).getFile).toPath

    val s3response = s3Client.putObject(
      PutObjectRequest.builder().bucket(ExampleS3Object.bucket).key(ExampleS3Object.key).build(),
      path
    ).toScala.futureValue

    assert(s3response.sdkHttpResponse.isSuccessful)
  }
}
