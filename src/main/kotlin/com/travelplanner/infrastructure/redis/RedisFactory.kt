package com.travelplanner.infrastructure.redis

import com.travelplanner.infrastructure.config.RedisConfig
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection

class RedisFactory(private val config: RedisConfig) {

    private val client: RedisClient by lazy {
        RedisClient.create(
            RedisURI.builder()
                .withHost(config.host)
                .withPort(config.port)
                .build()
        )
    }

    fun getConnection(): StatefulRedisConnection<String, String> = client.connect()

    fun shutdown() {
        client.shutdown()
    }
}
