package com.iris.security.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.iris.security.databinding.FragmentSettingsBinding
import com.iris.security.ui.auth.SetupActivity
import com.iris.security.util.PreferenceManager
import com.iris.security.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private val prefs by lazy { PreferenceManager.getInstance() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSettings()
        setupListeners()
    }

    private fun loadSettings() {
        binding.switchAlerts.isChecked = prefs.alertsEnabled
        binding.switchMotion.isChecked = prefs.motionAlertsEnabled
        binding.switchSound.isChecked = prefs.soundEnabled
        binding.switchVibration.isChecked = prefs.vibrationEnabled

        val config = prefs.config
        binding.tvDeviceId.text = config?.deviceId ?: "Not configured"
        binding.tvDeviceIp.text = config?.deviceIp ?: "Not configured"
        binding.tvMqttBroker.text = config?.mqttBroker ?: "Not configured"
        binding.tvAppVersion.text = "IRIS v1.0.0"
    }

    private fun setupListeners() {
        binding.switchAlerts.setOnCheckedChangeListener { _, checked ->
            prefs.alertsEnabled = checked
        }
        binding.switchMotion.setOnCheckedChangeListener { _, checked ->
            prefs.motionAlertsEnabled = checked
        }
        binding.switchSound.setOnCheckedChangeListener { _, checked ->
            prefs.soundEnabled = checked
        }
        binding.switchVibration.setOnCheckedChangeListener { _, checked ->
            prefs.vibrationEnabled = checked
        }

        binding.btnEditConfig.setOnClickListener {
            startActivity(Intent(requireContext(), SetupActivity::class.java))
        }

        binding.btnRebootDevice.setOnClickListener {
            val deviceId = prefs.config?.deviceId ?: return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                viewModel.mqttRepo.rebootDevice(deviceId)
            }
            toast("Reboot command sent to IRIS device")
        }

        binding.btnClearAlerts.setOnClickListener {
            viewModel.alertRepo.clearAll()
            toast("Alert history cleared")
        }

        binding.btnReconnect.setOnClickListener {
            val config = prefs.config ?: return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                viewModel.mqttRepo.connect(config)
            }
            toast("Reconnecting…")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
