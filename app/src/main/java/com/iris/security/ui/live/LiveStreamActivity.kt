package com.iris.security.ui.live

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.iris.security.databinding.ActivityLiveStreamBinding
import com.iris.security.util.ImageDownloadManager
import kotlinx.coroutines.*
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL

class LiveStreamActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLiveStreamBinding
    private var streamJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isStreaming = false
    private var lastFrame: Bitmap? = null
    private var connection: HttpURLConnection? = null

    private val uiHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiveStreamBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        val streamUrl = intent.getStringExtra(EXTRA_STREAM_URL) ?: run { finish(); return }
        setupClickListeners()
        startStream(streamUrl)
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnCapture.setOnClickListener {
            val frame = lastFrame
            if (frame == null) {
                Toast.makeText(this, "No frame available yet", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            captureAndSaveFrame(frame)
        }

        binding.btnFlashlight.setOnClickListener {
            Toast.makeText(this, "Flashlight command sent", Toast.LENGTH_SHORT).show()
        }
    }

    private fun captureAndSaveFrame(bitmap: Bitmap) {
        lifecycleScope.launch {
            binding.progressLoading.visibility = View.VISIBLE
            val fileName = "IRIS_Capture_${ImageDownloadManager.generateFileName()}"
            val result = ImageDownloadManager.saveBitmap(this@LiveStreamActivity, bitmap, fileName)
            binding.progressLoading.visibility = View.GONE

            when (result) {
                is ImageDownloadManager.DownloadResult.Success -> {
                    Toast.makeText(
                        this@LiveStreamActivity,
                        "Frame saved to Pictures/IRIS Security",
                        Toast.LENGTH_LONG
                    ).show()
                }
                is ImageDownloadManager.DownloadResult.Error -> {
                    Toast.makeText(
                        this@LiveStreamActivity,
                        "Save failed: ${result.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun startStream(url: String) {
        binding.progressLoading.visibility = View.VISIBLE
        binding.tvStreamStatus.text = "Connecting to stream…"
        isStreaming = true

        streamJob = scope.launch {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 10000
                conn.connect()
                connection = conn

                val inputStream = BufferedInputStream(conn.inputStream)
                val buffer = mutableListOf<Byte>()
                var firstFrame = true

                while (isStreaming && isActive) {
                    val byte = inputStream.read()
                    if (byte == -1) break
                    buffer.add(byte.toByte())

                    if (buffer.size >= 2) {
                        val last = buffer[buffer.size - 1]
                        val prev = buffer[buffer.size - 2]
                        if (prev == 0xFF.toByte() && last == 0xD9.toByte()) {
                            var startIdx = 0
                            for (i in 0 until buffer.size - 1) {
                                if (buffer[i] == 0xFF.toByte() && buffer[i + 1] == 0xD8.toByte()) {
                                    startIdx = i; break
                                }
                            }
                            val jpegBytes = buffer.subList(startIdx, buffer.size).toByteArray()
                            val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)

                            if (bitmap != null) {
                                lastFrame = bitmap
                                uiHandler.post {
                                    if (firstFrame) {
                                        binding.progressLoading.visibility = View.GONE
                                        binding.tvStreamStatus.text = "● LIVE"
                                        firstFrame = false
                                    }
                                    binding.ivStream.setImageBitmap(bitmap)
                                }
                            }
                            buffer.clear()
                        }
                    }
                    if (buffer.size > 500_000) buffer.clear()
                }
            } catch (e: Exception) {
                uiHandler.post {
                    binding.progressLoading.visibility = View.GONE
                    binding.tvStreamStatus.text = "Stream error: ${e.message}"
                }
            } finally {
                connection?.disconnect()
                connection = null
            }
        }
    }

    private fun stopStream() {
        isStreaming = false
        streamJob?.cancel()
        connection?.disconnect()
        connection = null
    }

    override fun onPause() {
        super.onPause()
        stopStream()
    }

    override fun onResume() {
        super.onResume()
        val url = intent.getStringExtra(EXTRA_STREAM_URL) ?: return
        if (!isStreaming) startStream(url)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStream()
        scope.cancel()
    }

    companion object {
        const val EXTRA_STREAM_URL = "extra_stream_url"
    }
}
