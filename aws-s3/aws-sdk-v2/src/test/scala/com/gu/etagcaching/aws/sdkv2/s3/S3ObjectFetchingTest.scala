package com.gu.etagcaching.aws.sdkv2.s3

import com.adobe.testing.s3mock.testcontainers.S3MockContainer
import com.gu.etagcaching.ETagCache
import com.gu.etagcaching.FreshnessPolicy.AlwaysWaitForRefreshedValue
import com.gu.etagcaching.aws.s3.{ObjectId, S3ByteArrayFetching}
import com.gu.etagcaching.aws.sdkv2.s3.ExampleParser.parseFruit
import com.gu.etagcaching.aws.sdkv2.s3.S3ClientForS3Mock.createS3clientFor
import com.gu.etagcaching.aws.sdkv2.s3.TestS3Objects.bucket
import com.gu.etagcaching.aws.sdkv2.s3.response.Transformer.Bytes
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, OptionValues}
import org.testcontainers.DockerClientFactory
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.PutObjectRequest

import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import scala.compat.java8.FutureConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

object TestS3Objects {
  val bucket = "test-bucket"
  val atomicInteger = new AtomicInteger()
  def generate(): TestS3Objects = TestS3Objects(atomicInteger.getAndIncrement())
}
case class TestS3Objects(id: Int) {
  val example: ObjectId = ObjectId(bucket, s"$id/path")
  val nonExistent: ObjectId = ObjectId(bucket, s"$id/nothing-should-be-here")
}
class S3ObjectFetchingTest extends AnyFlatSpec with Matchers with ScalaFutures with OptionValues with IntegrationPatience with BeforeAndAfterAll {

  require(DockerClientFactory.instance().isDockerAvailable,
    """
      |****
      |
      |This test uses S3Mock/testcontainers/Docker, and requires Docker Engine to be running on the machine -
      |specifically, on developer laptops it requires *Docker Desktop*.
      |
      |See https://github.com/guardian/etag-caching/pull/137 for more details!
      |
      |****
      |""".stripMargin
  )
  val s3Mock: S3MockContainer = new S3MockContainer("latest").withInitialBuckets(TestS3Objects.bucket)
  override def beforeAll(): Unit = s3Mock.start()
  override def afterAll(): Unit = s3Mock.stop()
  lazy val s3Client: S3AsyncClient = createS3clientFor(s3Mock) // lazy val because we need the s3Mock to start first

  "S3ObjectFetching" should "have an example to show how an S3-backed ETagCache is set up" in {
    implicit val testS3Objects: TestS3Objects = TestS3Objects.generate()

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
    fruitCache.get(testS3Objects.example).futureValue.value.colour shouldBe "yellow"
    fruitCache.get(testS3Objects.example).futureValue.value.colour shouldBe "yellow"

    uploadFile("kiwi.xml.gz")
    fruitCache.get(testS3Objects.example).futureValue.value.colour shouldBe "green"
    // Note that the value is correct, without us explicitly clearing the cache - ETag-checking saved us!

    fruitCache.get(testS3Objects.nonExistent).futureValue shouldBe None
  }

  it should "support a simple way to fetch byte arrays" in {
    implicit val testS3Objects: TestS3Objects = TestS3Objects.generate()

    val s3Fetching: S3ByteArrayFetching = S3ObjectFetching.byteArraysWith(s3Client)

    val cache = new ETagCache(
      s3Fetching.thenParsing(bytes => new String(bytes)),
      AlwaysWaitForRefreshedValue,
      _.maximumSize(500).expireAfterAccess(1.hour)
    )

    uploadFile("hello.txt")
    cache.get(testS3Objects.example).futureValue.value shouldBe "Hello World!"

    uploadFile("goodbye.txt")
    cache.get(testS3Objects.example).futureValue.value shouldBe "Au revoir!"
  }

  private def uploadFile(demoFile: String)(implicit testS3Objects: TestS3Objects): Unit = {
    val path = new File(getClass.getClassLoader.getResource("demo-files/" + demoFile).getFile).toPath

    val s3response = s3Client.putObject(
      PutObjectRequest.builder().bucket(testS3Objects.example.bucket).key(testS3Objects.example.key).build(),
      path
    ).toScala.futureValue

    assert(s3response.sdkHttpResponse.isSuccessful)
  }
}
