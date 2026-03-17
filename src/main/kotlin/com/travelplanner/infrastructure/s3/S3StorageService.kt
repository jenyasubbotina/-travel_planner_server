package com.travelplanner.infrastructure.s3

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.DeleteObjectRequest
import aws.sdk.kotlin.services.s3.model.HeadObjectRequest
import aws.sdk.kotlin.services.s3.model.NoSuchKey
import aws.sdk.kotlin.services.s3.model.NotFound
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.sdk.kotlin.services.s3.presigners.presignPutObject
import com.travelplanner.infrastructure.config.S3Config
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.minutes

class S3StorageService(
    private val s3Client: S3Client,
    private val config: S3Config
) {

    private val logger = LoggerFactory.getLogger(S3StorageService::class.java)

    suspend fun generatePresignedUploadUrl(
        key: String,
        contentType: String,
        expirationMinutes: Long = 15
    ): String {
        val putRequest = PutObjectRequest {
            bucket = config.bucket
            this.key = key
            this.contentType = contentType
        }

        val presigned = s3Client.presignPutObject(putRequest, expirationMinutes.minutes)
        return presigned.url.toString()
    }

    suspend fun deleteObject(key: String) {
        try {
            val request = DeleteObjectRequest {
                bucket = config.bucket
                this.key = key
            }
            s3Client.deleteObject(request)
            logger.info("Deleted S3 object: {}", key)
        } catch (e: Exception) {
            logger.error("Failed to delete S3 object: {}", key, e)
            throw e
        }
    }

    suspend fun objectExists(key: String): Boolean {
        return try {
            val request = HeadObjectRequest {
                bucket = config.bucket
                this.key = key
            }
            s3Client.headObject(request)
            true
        } catch (e: NotFound) {
            false
        } catch (e: NoSuchKey) {
            false
        } catch (e: Exception) {
            logger.error("Failed to check S3 object existence: {}", key, e)
            false
        }
    }
}
