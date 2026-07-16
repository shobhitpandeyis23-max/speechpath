package com.example.speechpath

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity

class PhonemeSelectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_phoneme_selection)

        val spinner = findViewById<Spinner>(R.id.spinnerPhoneme)

        val phonemes = arrayOf(
            "Select Phoneme",
            "/sh/",
            "/r/",
            "/l/",
            "/th/",
            "/s/",
            "/z/"
        )

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            phonemes
        )

        spinner.adapter = adapter

        spinner.setOnItemSelectedListener(
            object : android.widget.AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {

                    if (position == 0) return

                    val intent = Intent(
                        this@PhonemeSelectionActivity,
                        PracticeActivity::class.java
                    )

                    intent.putExtra("phoneme", phonemes[position])

                    startActivity(intent)
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                }
            }
        )
    }
}