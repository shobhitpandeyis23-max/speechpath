package com.example.speechpath

/**
 * On-device pronunciation scorer.
 * Compares the recognized speech text with the expected word
 * and calculates a pronunciation score without needing a backend server.
 */
object OnDeviceScorer {

    /**
     * Calculate a pronunciation score (0-100) by comparing the recognized
     * text with the expected word.
     */
    fun calculateScore(expectedWord: String, recognizedText: String): Int {
        if (recognizedText.isBlank()) return 0

        val expected = expectedWord.lowercase().trim()
        val recognized = recognizedText.lowercase().trim()
        val recognizedWords = recognized.split("\\s+".toRegex())

        // Layer 1: Exact word match (50%)
        val wordMatchScore = if (recognizedWords.any { it == expected }) {
            1.0
        } else {
            // Find the best matching word
            recognizedWords.maxOfOrNull { textSimilarity(expected, it) } ?: 0.0
        }

        // Layer 2: Overall text similarity (30%)
        val textScore = textSimilarity(expected, recognized)

        // Layer 3: First-letter and length similarity (20%)
        val structureScore = structuralSimilarity(expected, recognizedWords)

        val finalScore = (wordMatchScore * 0.50) + (textScore * 0.30) + (structureScore * 0.20)

        return (finalScore * 100).toInt().coerceIn(0, 100)
    }

    /**
     * Generate helpful feedback based on the score and phoneme.
     */
    fun generateFeedback(
        score: Int,
        expectedWord: String,
        recognizedText: String,
        targetPhoneme: String
    ): String {
        val phonemeClean = targetPhoneme.trim('/')

        if (recognizedText.isBlank()) {
            return "I couldn't hear you. Try speaking louder and closer to the mic."
        }

        return when {
            score >= 90 -> "Excellent! Your /$phonemeClean/ sound in \"$expectedWord\" was perfect! \uD83C\uDF89"
            score >= 75 -> "Great job! Your /$phonemeClean/ sound is very good. Keep it up!"
            score >= 60 -> "Good effort! Focus on the /$phonemeClean/ sound. I heard \"$recognizedText\" instead of \"$expectedWord\"."
            score >= 40 -> "Keep trying! I heard \"$recognizedText\" — try saying \"$expectedWord\" more slowly."
            else -> "Let's try again! Focus on the /$phonemeClean/ sound in \"$expectedWord\". Speak clearly and slowly."
        }
    }

    /**
     * Calculate text similarity between two strings using Levenshtein distance.
     * Returns a value between 0.0 (completely different) and 1.0 (identical).
     */
    private fun textSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0

        val maxLen = maxOf(a.length, b.length)
        val distance = levenshteinDistance(a, b)

        return 1.0 - (distance.toDouble() / maxLen)
    }

    /**
     * Check structural similarity: does the recognized word start with the same
     * letter and have a similar length?
     */
    private fun structuralSimilarity(expected: String, recognizedWords: List<String>): Double {
        if (recognizedWords.isEmpty()) return 0.0

        return recognizedWords.maxOf { word ->
            var score = 0.0

            // Same first letter
            if (word.isNotEmpty() && expected.isNotEmpty() && word[0] == expected[0]) {
                score += 0.5
            }

            // Similar length (within 2 characters)
            val lengthDiff = kotlin.math.abs(word.length - expected.length)
            score += when {
                lengthDiff == 0 -> 0.5
                lengthDiff == 1 -> 0.35
                lengthDiff == 2 -> 0.2
                else -> 0.0
            }

            score
        }
    }

    /**
     * Calculate the Levenshtein (edit) distance between two strings.
     */
    private fun levenshteinDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }

        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,       // deletion
                    dp[i][j - 1] + 1,       // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }

        return dp[a.length][b.length]
    }
}
