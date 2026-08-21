package com.example.speechpath

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


class PracticeActivity : AppCompatActivity() {

    private lateinit var txtFeedback: TextView
    private lateinit var txtScore: TextView
    private lateinit var txtWord: TextView
    private lateinit var btnRecord: Button

    private lateinit var words: List<String>
    private var currentWordIndex = 0
    private var phoneme: String = "/sh/"

    private lateinit var tts: TextToSpeech
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_practice)

        phoneme = intent.getStringExtra("phoneme") ?: "/sh/"
        words = PhonemeBank.getWords(phoneme)

        // Initialize Text-to-Speech
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale("en", "IN")
                tts.setSpeechRate(0.85f)
            }
        }

        // Find views
        val txtPhoneme = findViewById<TextView>(R.id.txtPhoneme)
        val txtHint = findViewById<TextView>(R.id.txtHint)
        txtWord = findViewById(R.id.txtWord)
        txtScore = findViewById(R.id.txtScore)
        txtFeedback = findViewById(R.id.txtFeedback)
        val btnHear = findViewById<Button>(R.id.btnHear)
        btnRecord = findViewById(R.id.btnRecord)
        val btnBack = findViewById<android.widget.ImageButton>(R.id.btnBack)

        // Set initial text
        txtPhoneme.text = "$phoneme Sound"
        txtHint.text = "Focus on the $phoneme sound"
        txtWord.text = words[currentWordIndex].uppercase()

        // Back button
        btnBack.setOnClickListener { finish() }

        // Hear button — speaks the word aloud
        btnHear.setOnClickListener {
            val word = words[currentWordIndex]
            tts.speak(word, TextToSpeech.QUEUE_FLUSH, null, null)
        }

        // Record button — starts/stops speech recognition
        btnRecord.setOnClickListener {
            if (!isListening) {
                startListening()
            } else {
                stopListening()
            }
        }
    }

    /**
     * Start listening for speech using Android's SpeechRecognizer.
     * This handles everything on-device: mic capture → speech-to-text → scoring.
     */
    private fun startListening() {
        // Check microphone permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                100
            )
            return
        }

        // Check if speech recognition is available
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            txtFeedback.text = "Speech recognition is not available on this device."
            return
        }

        // Create the speech recognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                btnRecord.text = "Stop"
                txtFeedback.text = "Listening... Say the word!"
                txtScore.text = "Score: --"
            }

            override fun onBeginningOfSpeech() {
                txtFeedback.text = "Hearing you..."
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Could use this to show a volume indicator
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                txtFeedback.text = "Processing..."
                isListening = false
                btnRecord.text = "Record"
            }

            override fun onError(error: Int) {
                isListening = false
                btnRecord.text = "Record"

                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH ->
                        "Couldn't understand. Try saying \"${words[currentWordIndex]}\" more clearly."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        "No speech detected. Tap Record and speak the word."
                    SpeechRecognizer.ERROR_AUDIO ->
                        "Audio recording error. Please try again."
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Network error. Check your internet connection."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        "Microphone permission is required."
                    else ->
                        "Error occurred (code: $error). Please try again."
                }
                txtFeedback.text = errorMsg
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )

                if (!matches.isNullOrEmpty()) {
                    val recognizedText = matches[0]
                    val expectedWord = words[currentWordIndex]

                    // Score the pronunciation on-device
                    val score = OnDeviceScorer.calculateScore(expectedWord, recognizedText)
                    val feedback = OnDeviceScorer.generateFeedback(
                        score, expectedWord, recognizedText, phoneme
                    )

                    txtScore.text = "Score: $score%"
                    txtFeedback.text = feedback
                } else {
                    txtFeedback.text = "Couldn't hear you. Try speaking louder."
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        // Configure the recognition intent
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        speechRecognizer?.startListening(intent)
    }

    /**
     * Stop the speech recognizer if it's currently listening.
     */
    private fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
        btnRecord.text = "Record"
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else {
            txtFeedback.text = "Microphone permission is required to record audio."
        }
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        speechRecognizer?.destroy()
        super.onDestroy()
    }
}