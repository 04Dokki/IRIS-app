package com.iris.security.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.iris.security.R
import com.iris.security.data.model.AlertEvent
import com.iris.security.data.repository.MqttRepository
import com.iris.security.databinding.FragmentDashboardBinding
import com.iris.security.ui.live.LiveStreamActivity
import com.iris.security.util.toTimeAgo
import com.iris.security.util.toUptimeString
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.btnLiveStream.setOnClickListener {
            val url = viewModel.getStreamUrl()
            if (url.isEmpty()) {
                Toast.makeText(requireContext(), "Device offline — stream unavailable", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(requireContext(), LiveStreamActivity::class.java).apply {
                putExtra(LiveStreamActivity.EXTRA_STREAM_URL, url)
            }
            startActivity(intent)
        }

        binding.btnArmDisarm.setOnClickListener {
            viewModel.toggleDetection()
        }

        binding.btnTriggerAlarm.setOnClickListener {
            viewModel.triggerAlarm()
            Toast.makeText(requireContext(), "Alarm triggered!", Toast.LENGTH_SHORT).show()
        }

        binding.btnSilenceAlarm.setOnClickListener {
            viewModel.silenceAlarm()
        }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshStatus()
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.connectionState.collect { state ->
                updateConnectionUi(state)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.deviceStatus.collect { status ->
                if (status == null) {
                    binding.layoutOffline.visibility = View.VISIBLE
                    binding.layoutOnline.visibility = View.GONE
                } else {
                    binding.layoutOffline.visibility = View.GONE
                    binding.layoutOnline.visibility = View.VISIBLE
                    binding.tvUptime.text = "Uptime: ${status.uptime.toUptimeString()}"
                    binding.tvSignal.text = "Wi-Fi: ${status.wifiRssi} dBm"
                    binding.tvEnrolled.text = "${status.enrolledFaces} enrolled faces"
                    updateArmButton(status.detectionEnabled)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.detectionArmed.collect { armed ->
                updateArmButton(armed)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.alerts.collect { alerts ->
                updateRecentAlerts(alerts)
                binding.tvTodayCount.text = alerts
                    .count { it.timestamp >= System.currentTimeMillis() - 86_400_000 }
                    .toString()
                binding.tvTotalCount.text = alerts.size.toString()
            }
        }
    }

    private fun updateConnectionUi(state: MqttRepository.ConnectionState) {
        when (state) {
            MqttRepository.ConnectionState.CONNECTED -> {
                binding.chipStatus.text = "Connected"
                binding.chipStatus.setChipBackgroundColorResource(R.color.iris_online)
            }
            MqttRepository.ConnectionState.CONNECTING -> {
                binding.chipStatus.text = "Connecting…"
                binding.chipStatus.setChipBackgroundColorResource(R.color.iris_warning)
            }
            MqttRepository.ConnectionState.DISCONNECTED,
            MqttRepository.ConnectionState.ERROR -> {
                binding.chipStatus.text = "Offline"
                binding.chipStatus.setChipBackgroundColorResource(R.color.iris_danger)
            }
        }
    }

    private fun updateArmButton(armed: Boolean) {
        if (armed) {
            binding.btnArmDisarm.text = "DISARM"
            binding.btnArmDisarm.setBackgroundColor(requireContext().getColor(R.color.iris_warning))
        } else {
            binding.btnArmDisarm.text = "ARM"
            binding.btnArmDisarm.setBackgroundColor(requireContext().getColor(R.color.iris_accent))
        }
    }

    private fun updateRecentAlerts(alerts: List<AlertEvent>) {
        val recent = alerts.take(3)

        if (recent.isEmpty()) {
            binding.tvNoAlerts.visibility = View.VISIBLE
            binding.layoutRecentAlerts.visibility = View.GONE
            return
        }

        binding.tvNoAlerts.visibility = View.GONE
        binding.layoutRecentAlerts.visibility = View.VISIBLE

        // Access included layout views directly by their IDs through the root binding
        val itemBindings = listOf(
            binding.alertItem1,
            binding.alertItem2,
            binding.alertItem3
        )

        itemBindings.forEachIndexed { index, itemBinding ->
            if (index < recent.size) {
                val alert = recent[index]
                itemBinding.root.visibility = View.VISIBLE
                itemBinding.tvAlertLabel.text = alert.label
                itemBinding.tvAlertTime.text = alert.timestamp.toTimeAgo()
                val iconRes = when (alert.type) {
                    AlertEvent.AlertType.INTRUDER_DETECTED -> R.drawable.ic_intruder
                    AlertEvent.AlertType.MOTION_DETECTED  -> R.drawable.ic_motion
                    else                                  -> R.drawable.ic_info
                }
                itemBinding.ivAlertIcon.setImageResource(iconRes)
            } else {
                itemBinding.root.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
