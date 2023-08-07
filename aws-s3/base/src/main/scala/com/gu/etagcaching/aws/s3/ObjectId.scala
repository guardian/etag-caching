package com.gu.etagcaching.aws.s3

case class ObjectId(bucket: String, key: String) {
  require(!key.startsWith("/"))

  val s3Uri: String = s"s3://$bucket/$key"
}