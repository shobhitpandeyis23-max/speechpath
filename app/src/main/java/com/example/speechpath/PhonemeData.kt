package com.example.speechpath

data class PhonemeData(
    val symbol: String,
    val words: List<String>
)

object PhonemeBank {

    val phonemes = listOf(

        PhonemeData(
            "/sh/",
            listOf("ship", "shoe", "shell", "shop")
        ),

        PhonemeData(
            "/r/",
            listOf("red", "run", "rice", "rope")
        ),

        PhonemeData(
            "/l/",
            listOf("lamp", "lake", "leaf", "lock")
        ),

        PhonemeData(
            "/th/",
            listOf("three", "think", "throw", "thank")
        ),

        PhonemeData(
            "/s/",
            listOf("sun", "sand", "sock", "salt")
        ),

        PhonemeData(
            "/z/",
            listOf("zoo", "zero", "zip", "zone")
        )
    )

    fun getWords(symbol: String): List<String> {

        for (phoneme in phonemes) {

            if (phoneme.symbol == symbol) {
                return phoneme.words
            }
        }

        return listOf("ship")
    }
}