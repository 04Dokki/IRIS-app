package com.iris.security.ui.dashboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.badge.BadgeDrawable
import com.iris.security.R
import com.iris.security.databinding.ActivityMainBinding
import com.iris.security.data.repository.MqttRepository
import com.iris.security.service.MqttService
import com.iris.security.util.toast
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    val viewModel: MainViewModel by viewModels()
    private var alertBadge: BadgeDrawable? = null

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) toast("Enable notifications to receive intruder alerts")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        setupBadge()
        observeViewModel()
        requestNotificationPermission()
        startMqttService()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        binding.bottomNavigation.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.dashboardFragment,
                R.id.alertsFragment,
                R.id.galleryFragment,
                R.id.settingsFragment ->
                    binding.bottomNavigation.visibility = View.VISIBLE
                else ->
                    binding.bottomNavigation.visibility = View.GONE
            }
        }
    }

    private fun setupBadge() {
        alertBadge = binding.bottomNavigation.getOrCreateBadge(R.id.alertsFragment).apply {
            isVisible = false
            backgroundColor = getColor(R.color.iris_danger)
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.unreadCount.collect { count ->
                alertBadge?.apply {
                    if (count > 0) { number = count; isVisible = true }
                    else isVisible = false
                }
            }
        }
        lifecycleScope.launch {
            viewModel.connectionState.collect { state ->
                updateConnectionIndicator(state)
            }
        }
    }

    private fun updateConnectionIndicator(state: MqttRepository.ConnectionState) {
        val colorRes = when (state) {
            MqttRepository.ConnectionState.CONNECTED    -> R.color.iris_online
            MqttRepository.ConnectionState.CONNECTING  -> R.color.iris_warning
            else                                        -> R.color.iris_danger
        }
        binding.connectionDot.setColorFilter(getColor(colorRes))
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startMqttService() {
        startService(Intent(this, MqttService::class.java))
    }
}
