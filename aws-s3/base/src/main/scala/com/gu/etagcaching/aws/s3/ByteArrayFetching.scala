package com.gu.etagcaching.aws.s3

import com.gu.etagcaching.fetching.Fetching

trait ByteArrayFetching[AWSClient] {
  def byteArrayWith(awsClient: AWSClient): Fetching[ObjectId, Array[Byte]]
}
