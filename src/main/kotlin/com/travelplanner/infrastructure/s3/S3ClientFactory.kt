package com.travelplanner.infrastructure.s3

import aws.sdk.kotlin.services.s3.S3Client
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.net.url.Url
import com.travelplanner.infrastructure.config.S3Config

class S3ClientFactory(private val config: S3Config) {

    fun createClient(): S3Client {
        return S3Client {
            region = config.region
            endpointUrl = Url.parse(config.endpoint)
            credentialsProvider = StaticS3CredentialsProvider(config.accessKey, config.secretKey)
            forcePathStyle = true
        }
    }
}

private class StaticS3CredentialsProvider(
    private val accessKeyId: String,
    private val secretAccessKey: String
) : CredentialsProvider {
    override suspend fun resolve(attributes: Attributes): Credentials {
        return Credentials(
            accessKeyId = accessKeyId,
            secretAccessKey = secretAccessKey
        )
    }
}
