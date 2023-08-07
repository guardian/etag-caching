package com.gu.etagcaching.aws.sdkv2.s3

import com.gu.etagcaching.Endo
import com.gu.etagcaching.aws.s3.ObjectId
import com.gu.etagcaching.aws.sdkv2.s3.response.Transformer
import com.gu.etagcaching.fetching.{ETaggedData, Fetching}
import software.amazon.awssdk.core.internal.util.ThrowableUtils
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.{GetObjectRequest, S3Exception}

import java.net.HttpURLConnection.HTTP_NOT_MODIFIED
import scala.compat.java8.FutureConverters._
import scala.concurrent.{ExecutionContext, Future}

case class S3ObjectFetching[Response](s3Client: S3AsyncClient, transformer: Transformer[Response])
  extends Fetching[ObjectId, Response] {
  private def wrapWithETag(resp: Response): ETaggedData[Response] =
    ETaggedData(transformer.rawResponseObjectOf(resp).eTag(), resp)

  private def performFetch(
    resourceId: ObjectId,
    reqModifier: Endo[GetObjectRequest.Builder] = identity,
  )(implicit ec: ExecutionContext): Future[ETaggedData[Response]] = {
    val requestBuilder = GetObjectRequest.builder().bucket(resourceId.bucket).key(resourceId.key)
    val request = reqModifier(requestBuilder).build()
    s3Client.getObject(request, transformer.asyncResponseTransformer())
      .toScala
      .transform(
        wrapWithETag,
        ThrowableUtils.getRootCause // see https://github.com/guardian/ophan/commit/49fa22176
      )
  }

  def fetch(key: ObjectId)(implicit ec: ExecutionContext): Future[ETaggedData[Response]] = performFetch(key)

  def fetchOnlyIfETagChanged(key: ObjectId, eTag: String)(implicit ec: ExecutionContext): Future[Option[ETaggedData[Response]]] =
    performFetch(key, _.ifNoneMatch(eTag)).map(Some(_)).recover {
      case e: S3Exception if e.statusCode == HTTP_NOT_MODIFIED => None // no fresh download because the ETag matched!
    }
}

