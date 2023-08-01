package com.gu.aws.s3

case class ObjectId(bucket: String, key: String) {
  val s3Uri: String = s"s3://$bucket/$key"
}