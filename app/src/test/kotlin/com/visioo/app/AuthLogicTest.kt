package com.visioo.app

import com.visioo.app.feature.auth.AuthValidation
import com.visioo.app.feature.auth.RecoveryChallenge
import com.visioo.app.feature.auth.VipKeyFormat
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthLogicTest {
    @Test fun validatesEmails() {
        assertTrue(AuthValidation.isValidEmail(" a@b.co "))
        assertFalse(AuthValidation.isValidEmail("a@b"))
        assertFalse(AuthValidation.isValidEmail("a b@c.d"))
        assertFalse(AuthValidation.isValidEmail(""))
    }

    @Test fun validatesPasswordLength() {
        assertFalse(AuthValidation.isValidPassword("1234567"))
        assertTrue(AuthValidation.isValidPassword("12345678"))
    }

    private val words = listOf("soleil", "rivière", "arbre", "nuit", "heureux", "cristal", "montagne", "libre", "étoile", "océan", "sourire", "forêt")

    @Test fun challengeAsksForThreeDistinctSortedPositions() {
        repeat(50) {
            val c = RecoveryChallenge.create(words, Random(it))
            assertEquals(3, c.positions.size)
            assertEquals(c.positions.sorted(), c.positions)
            assertEquals(3, c.positions.toSet().size)
        }
    }

    @Test fun challengeAcceptsRightAnswersIgnoringCaseAndAccents() {
        val c = RecoveryChallenge(words, listOf(1, 8, 9))
        assertTrue(c.check(listOf("RIVIERE", " étoile ", "ocean")))
        assertFalse(c.check(listOf("riviere", "etoile", "soleil")))
        assertFalse(c.check(listOf("riviere", "etoile")))
    }

    @Test fun formatsVipKeysWhileTyping() {
        assertEquals("VSOO", VipKeyFormat.format("vsoo"))
        assertEquals("VSOO-8K7M", VipKeyFormat.format("vsoo8k7m"))
        assertEquals("VSOO-8K7M-QP4X-2P9D", VipKeyFormat.format("vsoo-8k7m qp4x/2p9d and more"))
        assertTrue(VipKeyFormat.isComplete("VSOO-8K7M-QP4X-2P9D"))
        assertFalse(VipKeyFormat.isComplete("VSOO-8K7M"))
    }
}
