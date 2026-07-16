package com.example.speechpath

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {

    @Multipart
    @POST("/api/score")
    suspend fun uploadAudio(

        @Part audio_file: MultipartBody.Part,

        @Part("expected_text")
        expectedText: RequestBody,

        @Part("target_phoneme")
        targetPhoneme: RequestBody

    ): Response<ScoreResponse>
}