package com.gu.etagcaching.aws.sdkv2.s3

import com.adobe.testing.s3mock.testcontainers.S3MockContainer
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.http.SdkHttpConfigurationOption
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.{S3AsyncClient, S3Configuration}
import software.amazon.awssdk.utils.AttributeMap

import java.net.URI

object S3ClientForS3Mock {
  def createS3clientFor(s3Mock: S3MockContainer): S3AsyncClient = S3AsyncClient.builder()
    .region(Region.of("us-east-1"))
    .credentialsProvider(
      StaticCredentialsProvider.create(AwsBasicCredentials.create("foo", "bar")))
    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
    .endpointOverride(URI.create(s3Mock.getHttpsEndpoint))
    .httpClient(
      NettyNioAsyncHttpClient.builder()
        .buildWithDefaults(
          AttributeMap.builder()
            .put[java.lang.Boolean](SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES, true)
            .build()
        )
    ).build()
}
