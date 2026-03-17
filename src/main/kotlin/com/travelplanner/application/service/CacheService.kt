package com.travelplanner.application.service

interface CacheService {

    fun get(key: String): String?

    fun set(key: String, value: String, ttlSeconds: Long = 300)

    fun delete(key: String)

    fun invalidateByPrefix(prefix: String)
}
