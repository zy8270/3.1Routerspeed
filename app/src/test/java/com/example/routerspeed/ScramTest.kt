package com.example.routerspeed

import org.junit.Assert.assertEquals
import org.junit.Test

class ScramTest {
    @Test
    fun clientProofMatchesIndependentVector() {
        val firstNonce = "7884251407a5f2d8a645e985c2bc6f84bcd37757a7277c45981f0258a1d16430"
        val finalNonce = firstNonce + "iCLsdHOPJLbpBhhFGOX13f0GcD0fsbYm"

        val proof = Scram.clientProof(
            password = "test-password",
            saltHex = "11b86cf962093f7badbaeeec06ece121e0efd9c195fc9cce264b53862eb59e35",
            iterations = 100,
            firstNonce = firstNonce,
            finalNonce = finalNonce,
        )

        assertEquals("44a3df724027a98f6b3586a82ab0f9ce11b81fb4273fba8b767c56624b91bdf6", proof)
    }
}
