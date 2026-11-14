package com.example.routerspeed

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object Scram {
    private const val KEY_LENGTH_BITS = 256

    fun randomNonce(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    fun clientProof(
        password: String,
        saltHex: String,
        iterations: Int,
        firstNonce: String,
        finalNonce: String,
    ): String {
        require(iterations > 0) { "PBKDF2 iterations must be positive" }

        val keySpec = PBEKeySpec(
            password.toCharArray(),
            saltHex.hexToBytes(),
            iterations,
            KEY_LENGTH_BITS,
        )
        val saltedPassword = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(keySpec)
            .encoded
        keySpec.clearPassword()

        // The router's CryptoJS calls use the first argument as the message and
        // the second as the key. These calls intentionally preserve that order.
        val clientKey = hmacSha256(
            key = "Client Key".toByteArray(Charsets.UTF_8),
            message = saltedPassword,
        )
        val storedKey = MessageDigest.getInstance("SHA-256").digest(clientKey)
        val authMessage = "$firstNonce,$finalNonce,$finalNonce".toByteArray(Charsets.UTF_8)
        val clientSignature = hmacSha256(key = authMessage, message = storedKey)

        return ByteArray(clientKey.size) { index ->
            (clientKey[index].toInt() xor clientSignature[index].toInt()).toByte()
        }.toHex()
    }

    private fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(key, "HmacSHA256"))
        }.doFinal(message)

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "Invalid hexadecimal value" }
        return ByteArray(length / 2) { index ->
            val high = Character.digit(this[index * 2], 16)
            val low = Character.digit(this[index * 2 + 1], 16)
            require(high >= 0 && low >= 0) { "Invalid hexadecimal value" }
            ((high shl 4) or low).toByte()
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
