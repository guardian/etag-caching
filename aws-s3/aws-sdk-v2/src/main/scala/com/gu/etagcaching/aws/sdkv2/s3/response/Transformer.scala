package com.gu.etagcaching.aws.sdkv2.s3.response

import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.core.{ResponseBytes, ResponseInputStream}
import software.amazon.awssdk.services.s3.model.GetObjectResponse

/** AWS SDK v2 supports several different ways of handling an S3 response (getting a byte array, or
 * an InputStream, or downloading to a file) using `AsyncResponseTransformer`s. Unfortunately, the
 * result types do not share a common interface for extracting the response metadata
 * (`GetObjectResponse`), even though they all have an identically-named method called `response()`
 * for getting what we want.
 *
 * This Scala wrapper around the `AsyncResponseTransformer` aims to encapsulate all the things the
 * `AsyncResponseTransformer`s have in common.
 *
 * We could use Structural Types (https://docs.scala-lang.org/scala3/reference/changed-features/structural-types.html)
 * to make the differing objects seem to conform to a common interface, but this does involve
 * reflection (https://stackoverflow.com/a/26788585/438886), which is a bit inefficient/scary.
 */
case class Transformer[ResultType](
  asyncResponseTransformer: () => AsyncResponseTransformer[GetObjectResponse, ResultType],
  rawResponseObjectOf: ResultType => GetObjectResponse,
)

object Transformer {
  val Bytes: Transformer[ResponseBytes[GetObjectResponse]] =
    Transformer(() => AsyncResponseTransformer.toBytes[GetObjectResponse], _.response)

  val BlockingInputStream: Transformer[ResponseInputStream[GetObjectResponse]] =
    Transformer(() => AsyncResponseTransformer.toBlockingInputStream[GetObjectResponse], _.response)
}
