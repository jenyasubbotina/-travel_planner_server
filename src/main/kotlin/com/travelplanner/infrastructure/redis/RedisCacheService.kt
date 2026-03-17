package com.travelplanner.infrastructure.redis

import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanCursor
import org.slf4j.LoggerFactory

class RedisCacheService(private val redisFactory: RedisFactory) {

    private val logger = LoggerFactory.getLogger(RedisCacheService::class.java)

    fun get(key: String): String? {
        return try {
            redisFactory.getConnection().use { connection ->
                connection.sync().get(key)
            }
        } catch (e: Exception) {
            logger.error("Redis GET failed for key={}", key, e)
            null
        }
    }

    fun set(key: String, value: String, ttlSeconds: Long = 300) {
        try {
            redisFactory.getConnection().use { connection ->
                connection.sync().setex(key, ttlSeconds, value)
            }
        } catch (e: Exception) {
            logger.error("Redis SET failed for key={}", key, e)
        }
    }

    fun delete(key: String) {
        try {
            redisFactory.getConnection().use { connection ->
                connection.sync().del(key)
            }
        } catch (e: Exception) {
            logger.error("Redis DEL failed for key={}", key, e)
        }
    }

    fun invalidateByPrefix(prefix: String) {
        try {
            redisFactory.getConnection().use { connection ->
                val sync = connection.sync()
                var cursor: ScanCursor = ScanCursor.INITIAL
                val scanArgs = ScanArgs.Builder.matches("$prefix*").limit(100)

                do {
                    val result = sync.scan(cursor, scanArgs)
                    val keys = result.keys
                    if (keys.isNotEmpty()) {
                        sync.del(*keys.toTypedArray())
                    }
                    cursor = result
                } while (!cursor.isFinished)
            }
        } catch (e: Exception) {
            logger.error("Redis invalidateByPrefix failed for prefix={}", prefix, e)
        }
    }
}
