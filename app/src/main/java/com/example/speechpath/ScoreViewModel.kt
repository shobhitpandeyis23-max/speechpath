package com.example.speechpath

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class ScoreViewModel : ViewModel() {

    val scoreResult =
        MutableLiveData<ScoreResponse>()

    val errorMessage =
        MutableLiveData<String>()

    fun uploadWavFile(
        wavFile: File,
        expectedText: String,
        targetPhoneme: String
    ) {

        viewModelScope.launch {

            try {

                val requestFile =
                    wavFile.asRequestBody(
                        "audio/wav"
                            .toMediaTypeOrNull()
                    )

                val audioPart =
                    MultipartBody.Part.createFormData(
                        "audio_file",
                        wavFile.name,
                        requestFile
                    )

                val expectedBody =
                    expectedText.toRequestBody(
                        "text/plain"
                            .toMediaTypeOrNull()
                    )

                val phonemeBody =
                    targetPhoneme.toRequestBody(
                        "text/plain"
                            .toMediaTypeOrNull()
                    )

                val response =
                    RetrofitClient.apiService
                        .uploadAudio(
                            audioPart,
                            expectedBody,
                            phonemeBody
                        )

                if (response.isSuccessful &&
                    response.body() != null
                ) {

                    scoreResult.value =
                        response.body()

                } else {

                    errorMessage.value =
                        "Upload failed"
                }

            } catch (e: Exception) {

                errorMessage.value =
                    e.message
            }
        }
    }
}