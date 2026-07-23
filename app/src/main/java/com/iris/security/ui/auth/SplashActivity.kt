package com.iris.security.ui.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.iris.security.databinding.ActivitySplashBinding
import com.iris.security.ui.dashboard.MainActivity
import com.iris.security.util.PreferenceManager

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Navigate after 2 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            val prefs = PreferenceManager.getInstance()
            val destination = if (prefs.isSetupComplete) {
                Intent(this, MainActivity::class.java)
            } else {
                Intent(this, SetupActivity::class.java)
            }
            startActivity(destination)
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }, 2200)
    }
}
