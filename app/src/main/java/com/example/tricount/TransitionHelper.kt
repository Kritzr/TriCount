package com.example.tricount

import android.app.Activity
import android.content.Intent

object TransitionHelper {

    // Call this after startActivity() to animate forward
    fun Activity.slideIn() {
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    // Call this in finish() or onBackPressed() to animate back
    fun Activity.slideBack() {
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    // Convenience: start activity with forward animation
    fun Activity.startActivityWithTransition(intent: Intent) {
        startActivity(intent)
        slideIn()
    }
}