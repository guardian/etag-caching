# etag-caching
_Only fetch what's needed, only parse what you don't already have_

[![core Scala version support](https://index.scala-lang.org/guardian/etag-caching/core/latest-by-scala-version.svg?platform=jvm)](https://index.scala-lang.org/guardian/etag-caching/core)
[![aws-s3-sdk-v2 Scala version support](https://index.scala-lang.org/guardian/etag-caching/aws-s3-sdk-v2/latest-by-scala-version.svg?platform=jvm)](https://index.scala-lang.org/guardian/etag-caching/aws-s3-sdk-v2)

### Example usage

The main API entry-point is the `ETagCache` class.

This example (taken from [`S3ObjectFetchingTest`](https://github.com/guardian/etag-caching/blob/main/aws-s3/aws-sdk-v2/src/test/scala/com/gu/etagcaching/aws/sdkv2/s3/S3ObjectFetchingTest.scala))
shows an `ETagCache` setup for fetching-and-parsing compressed XML from S3:

```scala
import com.gu.etagcaching.ETagCache
import com.gu.etagcaching.aws.s3.ObjectId
import com.gu.etagcaching.aws.sdkv2.s3.S3ObjectFetching
import com.gu.etagcaching.aws.sdkv2.s3.response.Transformer.Bytes
import software.amazon.awssdk.services.s3.S3AsyncClient
import scala.util.Using

val s3Client: S3AsyncClient = ???
def parseFruit(is: InputStream): Fruit = ???

val fruitCache = new ETagCache[ObjectId, Fruit](
  S3ObjectFetching(s3Client, Bytes).thenParsing {
    bytes => Using(new GZIPInputStream(bytes.asInputStream()))(parseFruit).get
  },
  AlwaysWaitForRefreshedValue,
  _.maximumSize(500).expireAfterAccess(1.hour)
)

fruitCache.get(exampleS3id) // Future[Fruit]
```