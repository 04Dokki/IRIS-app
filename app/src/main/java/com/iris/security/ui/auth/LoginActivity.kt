package com.iris.security.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Stub LoginActivity — IRIS uses device-config based setup instead of user accounts.
 * Redirects immediately to SetupActivity.
 */
class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, SetupActivity::class.java))
        finish()
    }
}
