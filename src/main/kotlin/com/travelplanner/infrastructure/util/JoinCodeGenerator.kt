package com.travelplanner.infrastructure.util

import kotlin.random.Random

object JoinCodeGenerator {
    private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    fun generate(): String = (1..8)
        .map { ALPHABET[Random.nextInt(ALPHABET.length)] }
        .joinToString("")
}
