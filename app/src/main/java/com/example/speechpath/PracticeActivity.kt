package com.example.speechpath

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.activity.viewModels



class PracticeActivity : AppCompatActivity() {

    private lateinit var txtFeedback: TextView

    private lateinit var words: List<String>

    private var currentWordIndex = 0

    private lateinit var tts: TextToSpeech
    private var audioRecord: AudioRecord? = null

    private var recordingThread: Thread? = null

    private var isRecording = false

    private lateinit var wavFile: File
    private val sampleRate = 16000

    private val channelConfig =
        AudioFormat.CHANNEL_IN_MONO

    private val audioFormat =
        AudioFormat.ENCODING_PCM_16BIT

    private val bufferSize by lazy {

        AudioRecord.getMinBufferSize(
            sampleRate,
            channelConfig,
            audioFormat
        )
    }
    private val scoreViewModel:
            ScoreViewModel by viewModels()



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_practice)

        val phoneme = intent.getStringExtra("phoneme") ?: "/sh/"

        words = PhonemeBank.getWords(phoneme)

        tts = TextToSpeech(this) { status ->

            if (status == TextToSpeech.SUCCESS) {

                tts.language = Locale("en", "IN")

                tts.setSpeechRate(0.85f)
            }
        }

        val txtPhoneme =
            findViewById<TextView>(R.id.txtPhoneme)

        val txtHint =
            findViewById<TextView>(R.id.txtHint)

        val txtWord =
            findViewById<TextView>(R.id.txtWord)

        val txtScore =
            findViewById<TextView>(R.id.txtScore)

         txtFeedback =
            findViewById<TextView>(R.id.txtFeedback)
        val btnHear =
            findViewById<Button>(R.id.btnHear)
        val btnRecord =
            findViewById<Button>(R.id.btnRecord)
        val btnBack =
            findViewById<android.widget.ImageButton>(R.id.btnBack)
        txtPhoneme.text = "$phoneme Sound"

        txtHint.text = "Focus on the $phoneme sound"

        txtWord.text =
            words[currentWordIndex].uppercase()
        btnBack.setOnClickListener {

            finish()
        }

        btnHear.setOnClickListener {

            val word = words[currentWordIndex]

            tts.speak(
                word,
                TextToSpeech.QUEUE_FLUSH,
                null,
                null
            )
        }
        btnRecord.setOnClickListener {

            if (!isRecording) {

                startRecording()

                btnRecord.text = "Stop"
            } else {

                stopRecording()

                btnRecord.text = "Record"

                txtFeedback.text =
                    "Audio saved successfully"
            }
        }
        scoreViewModel.scoreResult.observe(this) {

            txtScore.text =
                "Score: ${it.score}%"

            txtFeedback.text =
                it.feedback
        }

        scoreViewModel.errorMessage.observe(this) {

            txtFeedback.text = it
        }

    }
    private fun startRecording() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                100
            )

            return
        }

        wavFile = File(
            filesDir,
            "speech.wav"
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        audioRecord?.startRecording()
        runOnUiThread {

            txtFeedback.text =
                "Recording started"
        }

        isRecording = true

        recordingThread = Thread {

            writeAudioToFile()

        }

        recordingThread?.start()

    }
    private fun stopRecording() {

        isRecording = false

        audioRecord?.stop()

        // Wait for the recording thread to finish writing the file
        recordingThread?.join()
        recordingThread = null

        audioRecord?.release()
        audioRecord = null

        updateWavHeader(wavFile)
        scoreViewModel.uploadWavFile(
            wavFile,
            words[currentWordIndex],
            intent.getStringExtra("phoneme") ?: "/sh/"
        )
    }
    private fun writeAudioToFile() {

        val data = ByteArray(bufferSize)

        val outputStream =
            FileOutputStream(wavFile)

        writeWavHeader(outputStream)

        while (isRecording) {

            val read =
                audioRecord?.read(
                    data,
                    0,
                    data.size
                ) ?: 0

            if (read > 0) {

                outputStream.write(
                    data,
                    0,
                    read
                )
            }
        }

        outputStream.close()
        runOnUiThread {

            txtFeedback.text =
                "WAV saved"
        }
    }
    private fun writeWavHeader(
        outputStream: FileOutputStream
    ) {

        val header = ByteArray(44)

        val byteRate =
            sampleRate * 2

        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()

        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()

        header[16] = 16
        header[20] = 1
        header[22] = 1

        header[24] =
            (sampleRate and 0xff).toByte()

        header[25] =
            (sampleRate shr 8).toByte()

        header[28] =
            (byteRate and 0xff).toByte()

        header[29] =
            (byteRate shr 8).toByte()

        header[32] = 2

        header[34] = 16

        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()

        outputStream.write(header, 0, 44)
    }
    private fun updateWavHeader(file: File) {

        val totalAudioLen =
            file.length() - 44

        val totalDataLen =
            totalAudioLen + 36

        val randomAccessFile =
            RandomAccessFile(file, "rw")

        randomAccessFile.seek(4)

        randomAccessFile.writeInt(
            Integer.reverseBytes(
                totalDataLen.toInt()
            )
        )

        randomAccessFile.seek(40)

        randomAccessFile.writeInt(
            Integer.reverseBytes(
                totalAudioLen.toInt()
            )
        )
        randomAccessFile.close()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            val btnRecord = findViewById<Button>(R.id.btnRecord)
            startRecording()
            btnRecord.text = "Stop"
        } else {
            txtFeedback.text = "Microphone permission is required to record audio."
        }
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}