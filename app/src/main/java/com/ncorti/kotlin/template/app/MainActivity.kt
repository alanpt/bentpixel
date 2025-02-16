package com.ncorti.kotlin.template.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ncorti.kotlin.template.app.databinding.ActivityMainBinding
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var areUiGesturesEnabled = false
    private var mediaFiles: List<File> = emptyList()
    private var currentMediaIndex = 0
    private var mediaPlayer: MediaPlayer? = null
    private var screenWidth = 0
    private var screenHeight = 0

    companion object {
        private const val REQUEST_READ_EXTERNAL_STORAGE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideSystemBars()
        requestReadExternalStoragePermission()
        binding.skewableRectangleView.post {
            screenWidth = binding.skewableRectangleView.width
            screenHeight = binding.skewableRectangleView.height
        }
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

    private fun requestReadExternalStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_READ_EXTERNAL_STORAGE)
        } else {
            loadMediaFiles()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            val controller = window.insetsController
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        }
    }

    private fun showSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true)
            val controller = window.insetsController
            if (controller != null) {
                controller.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_DEFAULT
                }
            }
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private fun loadMediaFiles() {
        val vjFolderPath = Environment.getExternalStorageDirectory().absolutePath + "/VJ"
        val vjFolder = File(vjFolderPath)
        MediaFileChecker.checkMediaFiles(this, vjFolder)
        if (vjFolder.exists() && vjFolder.isDirectory) {
            mediaFiles = vjFolder.listFiles { file ->
                val lowerCaseName = file.name.lowercase()
                lowerCaseName.endsWith(".png") || lowerCaseName.endsWith(".jpg") || lowerCaseName.endsWith(".jpeg") || lowerCaseName.endsWith(".gif") || lowerCaseName.endsWith(".mp4") || lowerCaseName.endsWith(".ogv")
            }?.toList() ?: emptyList()
            if (mediaFiles.isNotEmpty()) {
                currentMediaIndex = 0
                playVideo(mediaFiles[currentMediaIndex])
            } else {
                Log.w("MainActivity", "VJ folder is empty")
                showDefaultImage()
            }
        } else {
            Log.w("MainActivity", "VJ folder does not exist")
            showDefaultImage()
        }
    }

    private fun playNextMedia() {
        if (mediaFiles.isEmpty()) {
            Log.w("MainActivity", "No media files to play")
            return
        }
        currentMediaIndex = (currentMediaIndex + 1) % mediaFiles.size
        playCurrentMedia()
    }

    private fun playCurrentMedia() {
        val currentFile = mediaFiles[currentMediaIndex]
        val fileExtension = currentFile.extension.lowercase()
        if (fileExtension == "mp4" || fileExtension == "ogv") {
            playVideo(currentFile)
        } else {
            showImage(currentFile)
        }
    }

    private fun showImage(file: File) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmap = BitmapFactory.decodeStream(FileInputStream(file))
                withContext(Dispatchers.Main) {
                    binding.skewableRectangleView.setBitmap(bitmap)
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    showDefaultImage()
                }
            }
        }
    }

    private fun playVideo(file: File) {
        try {
            binding.skewableRectangleView.setBitmap(null)
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@MainActivity, Uri.fromFile(file))
                setOnPreparedListener {
                    start()
                    isLooping = true
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("MainActivity", "MediaPlayer error: what=$what, extra=$extra")
                    showDefaultImage()
                    true
                }
                prepareAsync()
            }
        } catch (e: IOException) {
            showDefaultImage()
        }
    }

    private fun showDefaultImage() {
        Log.w("MainActivity", "Showing default image")
    }
}