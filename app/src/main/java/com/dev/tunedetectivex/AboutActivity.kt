package com.dev.tunedetectivex

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.google.android.material.button.MaterialButton

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val buttonMatrixChat: MaterialButton = findViewById(R.id.buttonMatrixChat)
        val buttonRepo: MaterialButton = findViewById(R.id.buttonRepo)

        buttonMatrixChat.setOnClickListener {
            openMatrixChat()
        }

        buttonRepo.setOnClickListener {
            openGitHubRepo()
        }
    }

    private fun openMatrixChat() {
        val matrixChatUrl = "https://matrix.to/#/!HKIBPXETQFYecRxILT:matrix.org"
        val intent = Intent(Intent.ACTION_VIEW, matrixChatUrl.toUri())
        startActivity(intent)
    }

    private fun openGitHubRepo() {
        val repoUrl = "https://github.com/nooowavailable/tunedetectivex"
        val intent = Intent(Intent.ACTION_VIEW, repoUrl.toUri())
        startActivity(intent)
    }
}