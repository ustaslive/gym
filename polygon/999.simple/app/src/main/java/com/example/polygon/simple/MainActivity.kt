package com.example.polygon.simple

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private var showingInitialMessage = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val messageView: TextView = findViewById(R.id.messageText)
        val toggleButton: Button = findViewById(R.id.toggleButton)

        toggleButton.setOnClickListener {
            showingInitialMessage = !showingInitialMessage
            val nextText = if (showingInitialMessage) {
                getString(R.string.message_initial)
            } else {
                getString(R.string.message_alternate)
            }
            messageView.text = nextText
        }
    }
}
