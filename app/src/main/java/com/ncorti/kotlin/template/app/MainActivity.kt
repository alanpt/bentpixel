package com.ncorti.kotlin.template.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.ncorti.kotlin.template.app.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var areUiGesturesEnabled = false
    private var mediaFiles: List<File> = emptyList()
    private var currentMediaIndex = 0
    private var mediaPlayer: MediaPlayer? = null
    private var screenWidth = 0
    private var screenHeight = 0
    private var videoSurface: Surface? = null

    companion object {
        private const val REQUEST_READ_EXTERNAL_STORAGE = 1
    }

    private fun setupViewDimensions() {
        binding.skewableRectangleView.post {
            screenWidth = binding.skewableRectangleView.width
            screenHeight = binding.skewableRectangleView.height
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideSystemBars()
        requestReadExternalStoragePermission()
        setupViewDimensions()
        setupTouchListener()
    }

    private fun requestReadExternalStoragePermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_READ_EXTERNAL_STORAGE
            )
        } else {
            loadMediaFiles()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_READ_EXTERNAL_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadMediaFiles()
            } else {
                Log.e("MainActivity", "Read external storage permission denied")
            }
        }
    }

    private fun isClickInCenter(x: Float, y: Float): Boolean {
        val centerX = screenWidth / 2f
        val centerY = screenHeight / 2f
        val tenPercentWidth = screenWidth * 0.1f
        val tenPercentHeight = screenHeight * 0.1f
        return x >= centerX - tenPercentWidth && x <= centerX + tenPercentWidth && y >= centerY - tenPercentHeight && y <= centerY + tenPercentHeight
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            if (!areUiGesturesEnabled) {
                hideSystemBars()
            } else {
                showSystemBars()
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.action == KeyEvent.ACTION_UP) {
                toggleSystemBars()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun toggleSystemBars() {
        areUiGesturesEnabled = !areUiGesturesEnabled
        if (areUiGesturesEnabled) {
            showSystemBars()
        } else {
            hideSystemBars()
        }
    }

    private fun hideSystemBars() {
        window.setDecorFitsSystemWindows(false)
        val controller = window.insetsController
        if (controller != null) {
            controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            controller.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun showSystemBars() {
        window.setDecorFitsSystemWindows(true)
        val controller = window.insetsController
        if (controller != null) {
            controller.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_DEFAULT
        }
    }

    private fun loadMediaFiles() {
        val vjFolder =
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "VJ")
        Log.d("MainActivity", "VJ folder path: ${vjFolder.absolutePath}")
        Log.d("MainActivity", "VJ folder exists: ${vjFolder.exists()}")

        MediaFileChecker.checkMediaFiles(this, vjFolder)
        mediaFiles = vjFolder.listFiles { file ->
            file.isFile && arrayOf(".mp4", ".ogv").any {
                file.name.lowercase().endsWith(it)
            }
        }?.toList() ?: emptyList()
        Log.d("MainActivity", "Number of media files found: ${mediaFiles.size}")

        if (mediaFiles.isNotEmpty()) {
            currentMediaIndex = 0
            playCurrentMedia()
        } else {
            showDefaultImage()
        }
    }

    private fun playNextMedia() {
        if (mediaFiles.isEmpty()) {
            Log.w("MainActivity", "No media files to play")
            return
        }
        currentMediaIndex = (currentMediaIndex + 1) % mediaFiles.size
        val nextFile = mediaFiles[currentMediaIndex]
        Log.d("MainActivity", "Playing next media: ${nextFile.name}") // Added log
        playCurrentMedia()
    }

    private fun playCurrentMedia() {
        val currentFile = mediaFiles[currentMediaIndex]
        playVideo(currentFile)
    }

    override fun onPause() {
        super.onPause()
        mediaPlayer?.pause()
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }

    private fun playVideo(file: File) {
        try {
            Log.d("MainActivity", "Playing video: ${file.absolutePath}")
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, getSecureUri(file))
                val surfaceTexture = SurfaceTexture(0)
                videoSurface = Surface(surfaceTexture)
                setSurface(videoSurface)
                setOnPreparedListener {
                    Log.d("MainActivity", "MediaPlayer prepared")
                    start()
                    isLooping = true
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("MainActivity", "MediaPlayer error: $what | $extra")
                    showDefaultImage()
                    true
                }
                setOnInfoListener { _, what, extra ->
                    Log.d("MainActivity", "MediaPlayer info: $what | $extra")
                    false
                }
                setOnCompletionListener {
                    Log.d("MainActivity", "MediaPlayer completed")
                }
                prepareAsync()
            }
            binding.skewableRectangleView.setVideoSurface(videoSurface)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error playing video", e)
            showDefaultImage()
        }
    }

    private fun getSecureUri(file: File): Uri {
        Log.d("MainActivity", "Getting secure URI for: ${file.absolutePath}")
        return FileProvider.getUriForFile(
            this,
            "com.burntpixel.bentpixel.fileprovider", // Correct authority
            file
        ).also {
            Log.d("MainActivity", "Secure URI: $it")
        }
    }

    private fun showDefaultImage() {
        Log.w("MainActivity", "Showing default image")
    }

    private fun setupTouchListener() {
        binding.skewableRectangleView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val x = event.x
                val y = event.y
                if (isClickInCenter(x, y)) {
                    playNextMedia()
                    return@setOnTouchListener true
                }
            }
            false
        }
    }
}