package com.gu.etagcaching.aws

import com.gu.etagcaching.fetching.Fetching

package object s3 {
  /**
   * This type provides an interface to get bytes out of S3, independent of the version of the
   * AWS SDK in use. When code depends on this interface, it's not directly tied to AWS SDK
   * version 1 or 2, and consumers can provide an instance of the interface backed by whatever
   * client they prefer (even AWS SDK v1, though that is discouraged).
   *
   * Assuming you're using AWS SDK v2, get an instance of `S3ByteArrayFetching` by adding
   * "com.gu.etag-caching" %% "aws-s3-sdk-v2" as a dependency, then:
   *
   * {{{
   * import com.gu.etagcaching.aws.sdkv2.s3.S3ObjectFetching
   *
   * val s3AsyncClient: software.amazon.awssdk.services.s3.S3AsyncClient = ??? // AWS SDK v2
   * val s3Fetching: S3ByteArrayFetching = S3ObjectFetching.byteArraysWith(s3AsyncClient)
   * }}}
   */
  type S3ByteArrayFetching = Fetching[ObjectId, Array[Byte]]
}
