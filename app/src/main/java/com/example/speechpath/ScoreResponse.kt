package com.example.speechpath

data class ScoreResponse(

    val score: Int,

    val feedback: String,

    val recognized_text: String
)