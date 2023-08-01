package com.gu.aws.sdkv2.s3

import com.adobe.testing.s3mock.testcontainers.S3MockContainer
import com.gu.aws.s3.ObjectId
import com.gu.aws.sdkv2.s3.ExampleParser.parseFruit
import com.gu.aws.sdkv2.s3.S3ClientForS3Mock.createS3clientFor
import com.gu.aws.sdkv2.s3.response.Transformer.Bytes
import com.gu.etagcaching.ETagCache
import com.gu.etagcaching.FreshnessPolicy.AlwaysWaitForRefreshedValue
import org.scalatest.BeforeAndAfter
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.PutObjectRequest

import java.io.File
import java.util.zip.GZIPInputStream
import scala.concurrent.duration.DurationInt
import scala.jdk.FutureConverters.CompletionStageOps
import scala.util.Using

class S3ObjectFetchingTest extends AnyFlatSpec with Matchers with ScalaFutures with IntegrationPatience with BeforeAndAfter {
  val ExampleS3Object: ObjectId = ObjectId("test-bucket", "path")

  val s3Mock: S3MockContainer = new S3MockContainer("latest").withInitialBuckets(ExampleS3Object.bucket)
  before(s3Mock.start())
  after(s3Mock.stop())
  lazy val s3Client: S3AsyncClient = createS3clientFor(s3Mock) // lazy val because we need the s3Mock to start first

  "S3ObjectFetching" should "have an example to show how an S3-backed ETagCache is set up" in {
    val fruitCache = new ETagCache(
      S3ObjectFetching(s3Client, Bytes).thenDecoding {
        bytes => Using(new GZIPInputStream(bytes.asInputStream()))(parseFruit).get
      },
      AlwaysWaitForRefreshedValue,
      _.maximumSize(500).expireAfterAccess(1.hour)
    )

    uploadFile("banana.xml.gz")
    fruitCache.get(ExampleS3Object).futureValue.colour shouldBe "yellow"

    uploadFile("kiwi.xml.gz")
    fruitCache.get(ExampleS3Object).futureValue.colour shouldBe "green"
    // Note that the value is correct, without us explicitly clearing the cache - ETag-checking saved us!
  }

  private def uploadFile(demoFile: String): Unit = {
    val path = new File(getClass.getClassLoader.getResource("demo-files/" + demoFile).getFile).toPath

    val s3response = s3Client.putObject(
      PutObjectRequest.builder().bucket(ExampleS3Object.bucket).key(ExampleS3Object.key).build(),
      path
    ).asScala.futureValue

    assert(s3response.sdkHttpResponse.isSuccessful)
  }
}
