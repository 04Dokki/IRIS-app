package com.iris.security.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import com.iris.security.data.model.IrisConfig
import com.iris.security.databinding.ActivitySetupBinding
import com.iris.security.ui.dashboard.MainActivity
import com.iris.security.util.PreferenceManager

class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private val prefs by lazy { PreferenceManager.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        populateExistingConfig()
    }

    private fun setupViews() {
        // Password visibility toggle
        binding.tilMqttPassword.setEndIconOnClickListener {
            val et = binding.etMqttPassword
            if (et.inputType == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else {
                et.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            }
            et.setSelection(et.text?.length ?: 0)
        }

        // Real-time validation
        binding.etDeviceIp.doOnTextChanged { text, _, _, _ ->
            if (!text.isNullOrEmpty()) binding.tilDeviceIp.error = null
        }
        binding.etMqttBroker.doOnTextChanged { text, _, _, _ ->
            if (!text.isNullOrEmpty()) binding.tilMqttBroker.error = null
        }

        // Save button
        binding.btnSave.setOnClickListener {
            if (validateAndSave()) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }

        // Test connection button
        binding.btnTestConnection.setOnClickListener {
            testConnection()
        }
    }

    private fun populateExistingConfig() {
        prefs.config?.let { config ->
            binding.etDeviceIp.setText(config.deviceIp)
            binding.etMqttBroker.setText(config.mqttBroker)
            binding.etMqttPort.setText(config.mqttPort.toString())
            binding.etMqttUser.setText(config.mqttUser)
            binding.etMqttPassword.setText(config.mqttPassword)
            binding.etDeviceId.setText(config.deviceId)
        }
    }

    private fun validateAndSave(): Boolean {
        var valid = true

        val deviceIp = binding.etDeviceIp.text?.toString()?.trim() ?: ""
        val mqttBroker = binding.etMqttBroker.text?.toString()?.trim() ?: ""
        val mqttPort = binding.etMqttPort.text?.toString()?.trim()?.toIntOrNull() ?: 1883
        val mqttUser = binding.etMqttUser.text?.toString()?.trim() ?: ""
        val mqttPass = binding.etMqttPassword.text?.toString() ?: ""
        val deviceId = binding.etDeviceId.text?.toString()?.trim()?.ifEmpty { "iris_01" } ?: "iris_01"

        if (deviceIp.isEmpty()) {
            binding.tilDeviceIp.error = "Device IP address is required"
            valid = false
        }
        if (mqttBroker.isEmpty()) {
            binding.tilMqttBroker.error = "MQTT broker address is required"
            valid = false
        }

        if (!valid) return false

        val config = IrisConfig(
            deviceIp = deviceIp,
            mqttBroker = mqttBroker,
            mqttPort = mqttPort,
            mqttUser = mqttUser,
            mqttPassword = mqttPass,
            deviceId = deviceId
        )

        prefs.config = config
        prefs.isSetupComplete = true
        prefs.lastKnownStreamUrl = "http://$deviceIp:81/stream"

        Toast.makeText(this, "Configuration saved!", Toast.LENGTH_SHORT).show()
        return true
    }

    private fun testConnection() {
        val ip = binding.etDeviceIp.text?.toString()?.trim()
        if (ip.isNullOrEmpty()) {
            binding.tilDeviceIp.error = "Enter IP first"
            return
        }
        Toast.makeText(this, "Testing connection to $ip...", Toast.LENGTH_SHORT).show()
        // In production: ping HTTP endpoint on the ESP32
    }
}
