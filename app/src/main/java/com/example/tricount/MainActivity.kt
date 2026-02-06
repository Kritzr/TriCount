package com.example.tricount

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.tricount.data.SessionManager

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionManager = SessionManager(this)

        // Check if user is logged in
        if (sessionManager.isLoggedIn()) {
            // User is logged in, go to HomeActivity
            startActivity(Intent(this, HomeActivity::class.java))
        } else {
            // User is not logged in, go to LoginActivity
            startActivity(Intent(this, LoginActivity::class.java))
        }

        finish()
    }
}