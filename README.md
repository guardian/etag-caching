# etag-caching
_Only fetch what's needed, only parse what you don't already have_

### Example usage

This example (taken from [`S3ObjectFetchingTest`](https://github.com/guardian/etag-caching/blob/main/aws-s3/aws-sdk-v2/src/test/scala/com/gu/aws/sdkv2/s3/S3ObjectFetchingTest.scala))
shows how `ETagCache` might be setup for fetching-and-parsing compressed XML from S3:

```scala
import com.gu.etagcaching.ETagCache
import com.gu.aws.s3.ObjectId
import com.gu.aws.sdkv2.s3.response.Transformer.Bytes
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