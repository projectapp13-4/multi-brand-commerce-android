package com.gurbakir.account.oauth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

private const val PKCE_VERIFIER_BYTE_COUNT = 64

data class PkcePair(val verifier: String, val challenge: String) {
    override fun toString(): String = "PkcePair(<redacted>)"
}

class PkceGenerator(private val secureRandom: SecureRandom = SecureRandom()) {
    fun generate(): PkcePair {
        val verifierBytes = ByteArray(PKCE_VERIFIER_BYTE_COUNT).also(secureRandom::nextBytes)
        val verifier = verifierBytes.base64Url()
        val challenge = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)).base64Url()
        return PkcePair(verifier = verifier, challenge = challenge)
    }
}

private fun ByteArray.base64Url(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(this)
