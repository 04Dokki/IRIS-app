package com.iris.security.ui.gallery

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.iris.security.R
import com.iris.security.databinding.FragmentGalleryBinding
import com.iris.security.databinding.ItemGalleryImageBinding
import com.iris.security.ui.dashboard.MainViewModel
import com.iris.security.util.ImageDownloadManager
import kotlinx.coroutines.launch

class CapturedImagesFragment : Fragment() {

    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: GalleryAdapter

    // ─── Permission launcher ─────────────────────────────────────────────────

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadGallery()
        else Toast.makeText(requireContext(),
            "Storage permission needed to save images", Toast.LENGTH_LONG).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupListeners()
        checkPermissionAndLoad()
        observeAlerts()
    }

    private fun observeAlerts() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.alerts.collect {
                loadGallery()
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = GalleryAdapter(
            onDownload = { item -> downloadFromAlert(item) },
            onDelete = { item -> deleteImage(item) }
        )
        binding.rvGallery.apply {
            this.adapter = this@CapturedImagesFragment.adapter
            layoutManager = GridLayoutManager(requireContext(), 2)
        }
    }

    private fun setupListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            loadGallery()
            binding.swipeRefresh.isRefreshing = false
        }

        binding.btnDownloadAll.setOnClickListener {
            downloadAllAlertImages()
        }
    }

    private fun checkPermissionAndLoad() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(requireContext(), permission)
            == PackageManager.PERMISSION_GRANTED) {
            loadGallery()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    private fun loadGallery() {
        // Load images already saved to IRIS folder on device
        val savedImages = loadIrisSavedImages()

        // Also collect alert images from memory that haven't been saved yet
        val alertImages = viewModel.alertRepo.alerts.value
            .filter { !it.imageUrl.isNullOrEmpty() }
            .map { alert ->
                GalleryItem(
                    id = alert.id,
                    imageUrl = alert.imageUrl!!,
                    timestamp = alert.timestamp,
                    label = alert.label,
                    isSaved = false
                )
            }

        val combined = (alertImages + savedImages)
            .distinctBy { it.id }
            .sortedByDescending { it.timestamp }

        if (combined.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.rvGallery.visibility = View.GONE
        } else {
            binding.layoutEmpty.visibility = View.GONE
            binding.rvGallery.visibility = View.VISIBLE
            adapter.submitList(combined)
        }

        binding.tvImageCount.text = "${combined.size} image${if (combined.size != 1) "s" else ""}"
    }

    private fun loadIrisSavedImages(): List<GalleryItem> {
        val images = mutableListOf<GalleryItem>()
        val projection = arrayOf(
            android.provider.MediaStore.Images.Media._ID,
            android.provider.MediaStore.Images.Media.DISPLAY_NAME,
            android.provider.MediaStore.Images.Media.DATE_TAKEN
        )
        val selection = "${android.provider.MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%IRIS Security%")

        requireContext().contentResolver.query(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, selection, selectionArgs,
            "${android.provider.MediaStore.Images.Media.DATE_TAKEN} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATE_TAKEN)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol)
                val date = cursor.getLong(dateCol)
                val uri = ContentUris.withAppendedId(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )
                images.add(GalleryItem(
                    id = "saved_$id",
                    imageUrl = uri.toString(),
                    timestamp = date,
                    label = name,
                    isSaved = true,
                    localUri = uri
                ))
            }
        }
        return images
    }

    private fun downloadFromAlert(item: GalleryItem) {
        if (item.isSaved) {
            Toast.makeText(requireContext(), "Already saved to gallery", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressDownload.visibility = View.VISIBLE
            val fileName = ImageDownloadManager.generateFileName()
            val result = ImageDownloadManager.downloadImage(
                requireContext(), item.imageUrl, fileName
            )
            binding.progressDownload.visibility = View.GONE

            when (result) {
                is ImageDownloadManager.DownloadResult.Success -> {
                    Toast.makeText(
                        requireContext(),
                        "Saved to Pictures/IRIS Security",
                        Toast.LENGTH_LONG
                    ).show()
                    loadGallery()
                }
                is ImageDownloadManager.DownloadResult.Error -> {
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun downloadAllAlertImages() {
        val unsaved = viewModel.alertRepo.alerts.value
            .filter { !it.imageUrl.isNullOrEmpty() }

        if (unsaved.isEmpty()) {
            Toast.makeText(requireContext(), "No new images to download", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressDownload.visibility = View.VISIBLE
            var successCount = 0

            unsaved.forEach { alert ->
                val result = ImageDownloadManager.downloadImage(
                    requireContext(),
                    alert.imageUrl!!,
                    ImageDownloadManager.generateFileName()
                )
                if (result is ImageDownloadManager.DownloadResult.Success) successCount++
            }

            binding.progressDownload.visibility = View.GONE
            Toast.makeText(
                requireContext(),
                "$successCount image${if (successCount != 1) "s" else ""} saved to Pictures/IRIS Security",
                Toast.LENGTH_LONG
            ).show()
            loadGallery()
        }
    }

    private fun deleteImage(item: GalleryItem) {
        if (item.localUri != null) {
            requireContext().contentResolver.delete(item.localUri, null, null)
            Toast.makeText(requireContext(), "Image deleted", Toast.LENGTH_SHORT).show()
            loadGallery()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// ─── Data class ───────────────────────────────────────────────────────────────

data class GalleryItem(
    val id: String,
    val imageUrl: String,
    val timestamp: Long,
    val label: String,
    val isSaved: Boolean,
    val localUri: Uri? = null
)

// ─── RecyclerView Adapter ────────────────────────────────────────────────────

class GalleryAdapter(
    private val onDownload: (GalleryItem) -> Unit,
    private val onDelete: (GalleryItem) -> Unit
) : ListAdapter<GalleryItem, GalleryAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(val binding: ItemGalleryImageBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGalleryImageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            Glide.with(root)
                .load(item.imageUrl)
                .placeholder(R.drawable.bg_placeholder)
                .centerCrop()
                .into(ivCapture)

            tvImageLabel.text = item.label
            tvImageTime.text = item.timestamp.toFormattedShort()

            if (item.isSaved) {
                btnDownload.setImageResource(R.drawable.ic_check_circle)
                btnDownload.isEnabled = false
                btnDownload.alpha = 0.4f
            } else {
                btnDownload.setImageResource(R.drawable.ic_download)
                btnDownload.isEnabled = true
                btnDownload.alpha = 1.0f
            }

            btnDownload.setOnClickListener { onDownload(item) }
            btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    private fun Long.toFormattedShort(): String {
        val sdf = java.text.SimpleDateFormat("MMM d • HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(this))
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<GalleryItem>() {
            override fun areItemsTheSame(a: GalleryItem, b: GalleryItem) = a.id == b.id
            override fun areContentsTheSame(a: GalleryItem, b: GalleryItem) = a == b
        }
    }
}
