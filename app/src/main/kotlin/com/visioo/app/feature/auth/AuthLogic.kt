package com.visioo.app.feature.auth

import java.text.Normalizer
import kotlin.random.Random

object AuthValidation {
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    const val MIN_PASSWORD = 8

    fun isValidEmail(value: String) = value.trim().length <= 254 && EMAIL.matches(value.trim())
    fun isValidPassword(value: String) = value.length in MIN_PASSWORD..200
}

/** Asks for a few words of the phrase, to prove the user wrote it down. */
class RecoveryChallenge(private val words: List<String>, val positions: List<Int>) {
    init {
        require(positions.isNotEmpty() && positions.all { it in words.indices })
    }

    fun check(answers: List<String>): Boolean =
        answers.size == positions.size && positions.indices.all { i -> normalize(answers[i]) == normalize(words[positions[i]]) }

    companion object {
        const val SIZE = 3

        fun create(words: List<String>, random: Random = Random.Default) =
            RecoveryChallenge(words, words.indices.shuffled(random).take(SIZE).sorted())

        /** Accent and case insensitive, so a TV keyboard without accents is enough. */
        fun normalize(word: String): String =
            Normalizer.normalize(word.trim(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()
    }
}

object VipKeyFormat {
    private const val PREFIX_LENGTH = 4
    private const val KEY_LENGTH = 16

    /** Uppercases, drops separators and re-inserts dashes: `vsoo8k7m` becomes `VSOO-8K7M`. */
    fun format(input: String): String =
        input.uppercase().filter { it.isLetterOrDigit() }.take(KEY_LENGTH).chunked(PREFIX_LENGTH).joinToString("-")

    fun isComplete(formatted: String) = formatted.count { it.isLetterOrDigit() } == KEY_LENGTH
}
