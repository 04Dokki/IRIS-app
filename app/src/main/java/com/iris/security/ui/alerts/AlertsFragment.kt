package com.iris.security.ui.alerts

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.iris.security.R
import com.iris.security.data.model.AlertEvent
import com.iris.security.databinding.FragmentAlertsBinding
import com.iris.security.databinding.ItemAlertBinding
import com.iris.security.ui.dashboard.MainViewModel
import com.iris.security.util.ImageDownloadManager
import com.iris.security.util.gone
import com.iris.security.util.toFormattedDateTime
import com.iris.security.util.visible
import kotlinx.coroutines.launch

class AlertsFragment : Fragment() {

    private var _binding: FragmentAlertsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: AlertAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlertsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeViewModel()

        binding.btnAcknowledgeAll.setOnClickListener {
            viewModel.acknowledgeAll()
        }

        binding.swipeRefresh.setOnRefreshListener {
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun setupRecyclerView() {
        adapter = AlertAdapter(
            onAcknowledge = { alert -> viewModel.acknowledgeAlert(alert.id) },
            onDownloadImage = { alert -> downloadAlertImage(alert) }
        )
        binding.rvAlerts.apply {
            this.adapter = this@AlertsFragment.adapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun downloadAlertImage(alert: AlertEvent) {
        val imageUrl = alert.imageUrl
        if (imageUrl.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "No image for this alert", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            Toast.makeText(requireContext(), "Downloading…", Toast.LENGTH_SHORT).show()
            val fileName = "IRIS_Alert_${ImageDownloadManager.generateFileName()}"
            val result = ImageDownloadManager.downloadImage(requireContext(), imageUrl, fileName)

            when (result) {
                is ImageDownloadManager.DownloadResult.Success -> {
                    Toast.makeText(
                        requireContext(),
                        "Saved to Pictures/IRIS Security",
                        Toast.LENGTH_LONG
                    ).show()
                }
                is ImageDownloadManager.DownloadResult.Error -> {
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.alerts.collect { alerts ->
                adapter.submitList(alerts)
                if (alerts.isEmpty()) {
                    binding.layoutEmpty.visible()
                    binding.rvAlerts.gone()
                } else {
                    binding.layoutEmpty.gone()
                    binding.rvAlerts.visible()
                }
                val unread = alerts.count { !it.acknowledged }
                binding.tvUnreadCount.text = if (unread > 0) "$unread unread" else "All read"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class AlertAdapter(
    private val onAcknowledge: (AlertEvent) -> Unit,
    private val onDownloadImage: (AlertEvent) -> Unit
) : ListAdapter<AlertEvent, AlertAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(val binding: ItemAlertBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAlertBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val alert = getItem(position)
        with(holder.binding) {
            tvAlertTitle.text = alert.label
            tvAlertTime.text = alert.timestamp.toFormattedDateTime()

            if (alert.confidence != null) {
                tvConfidence.visible()
                tvConfidence.text = "Confidence: ${(alert.confidence * 100).toInt()}%"
            } else {
                tvConfidence.gone()
            }

            val (iconRes, colorRes) = when (alert.type) {
                AlertEvent.AlertType.INTRUDER_DETECTED -> Pair(R.drawable.ic_intruder, R.color.iris_danger)
                AlertEvent.AlertType.MOTION_DETECTED   -> Pair(R.drawable.ic_motion, R.color.iris_warning)
                AlertEvent.AlertType.SYSTEM_ONLINE     -> Pair(R.drawable.ic_online, R.color.iris_online)
                AlertEvent.AlertType.SYSTEM_OFFLINE    -> Pair(R.drawable.ic_offline, R.color.iris_danger)
                else -> Pair(R.drawable.ic_info, R.color.iris_accent)
            }
            ivTypeIcon.setImageResource(iconRes)
            ivTypeIcon.setColorFilter(root.context.getColor(colorRes))

            if (!alert.imageUrl.isNullOrEmpty()) {
                ivThumbnail.visible()
                btnDownloadImage.visible()
                Glide.with(root)
                    .load(alert.imageUrl)
                    .placeholder(R.drawable.bg_placeholder)
                    .into(ivThumbnail)
            } else {
                ivThumbnail.gone()
                btnDownloadImage.gone()
            }

            viewUnread.visibility = if (!alert.acknowledged) View.VISIBLE else View.INVISIBLE
            root.alpha = if (alert.acknowledged) 0.6f else 1.0f

            btnDownloadImage.setOnClickListener { onDownloadImage(alert) }
            root.setOnClickListener { onAcknowledge(alert) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<AlertEvent>() {
            override fun areItemsTheSame(a: AlertEvent, b: AlertEvent) = a.id == b.id
            override fun areContentsTheSame(a: AlertEvent, b: AlertEvent) = a == b
        }
    }
}
