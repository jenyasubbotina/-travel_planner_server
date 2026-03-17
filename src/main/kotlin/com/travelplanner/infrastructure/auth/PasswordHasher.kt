package com.travelplanner.infrastructure.auth

import org.mindrot.jbcrypt.BCrypt

object PasswordHasher {

    private const val LOG_ROUNDS = 12

    fun hash(password: String): String =
        BCrypt.hashpw(password, BCrypt.gensalt(LOG_ROUNDS))

    fun verify(password: String, hash: String): Boolean =
        BCrypt.checkpw(password, hash)
}
