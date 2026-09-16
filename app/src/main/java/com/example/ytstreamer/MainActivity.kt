package com.example.ytstreamer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.ytstreamer.audio.GainMicrophoneSource
import com.example.ytstreamer.databinding.ActivityMainBinding
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.encoder.utils.gl.TranslateTo
import com.pedro.extrasource.CameraUvcSource
import com.pedro.encoder.input.gl.render.filters.`object`.ImageObjectFilterRender
import com.pedro.library.generic.GenericStream
import com.pedro.library.util.sources.video.Camera2Source
import com.pedro.common.ConnectChecker

/**
 * Step 2: Built-in camera + External HDMI capture card (UVC) + Logo overlay + Audio gain/noise
 * reduction, all streaming to YouTube over RTMP using only a Stream Key.
 *
 * Video source switch:   GenericStream.changeVideoSource(Camera2Source | CameraUvcSource)
 * Audio source:          GainMicrophoneSource (custom, in audio/ package) — gives us gain + NS/AEC
 * Logo overlay:          ImageObjectFilterRender added to the GL pipeline
 *
 * NOTE: CameraUvcSource requires the phone to support USB-OTG (host mode) and the capture card
 * to be UVC-class-compliant (most generic "USB Video Class" HDMI grabbers are). Some phones /
 * capture cards need a powered OTG hub. Test on a real device — emulator has no USB camera.
 */
class MainActivity : AppCompatActivity(), ConnectChecker {

    private lateinit var binding: ActivityMainBinding
    private lateinit var genericStream: GenericStream
    private lateinit var gainMicSource: GainMicrophoneSource
    private var imageFilter: ImageObjectFilterRender? = null
    private var isStreaming = false
    private var usingUvc = false

    private val youtubeRtmpBase = "rtmp://a.rtmp.youtube.com/live2"

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )
    private val permissionRequestCode = 101

    // Modern photo picker — no storage permission needed
    private val pickLogoLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) applyLogo(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        gainMicSource = GainMicrophoneSource()

        // Default: built-in back camera (Camera2) + our custom gain mic source
        genericStream = GenericStream(this, this, Camera2Source(this), gainMicSource)

        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, requiredPermissions, permissionRequestCode)
        }

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnSourceBuiltin.setOnClickListener { switchToBuiltInCamera() }
        binding.btnSourceUvc.setOnClickListener { switchToUvcCaptureCard() }

        binding.btnSwitchCamera.setOnClickListener {
            if (!usingUvc) {
                try {
                    (genericStream.videoSource as? Camera2Source)?.switchCamera()
                } catch (e: Exception) {
                    Log.e("YTStreamer", "Switch camera failed", e)
                }
            } else {
                Toast.makeText(this, "HDMI source use hote hue front/back switch लागू नहीं होता", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnPickLogo.setOnClickListener {
            pickLogoLauncher.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
        }
        binding.btnRemoveLogo.setOnClickListener { removeLogo() }

        binding.btnPosTL.setOnClickListener { setLogoPosition(TranslateTo.TOP_LEFT) }
        binding.btnPosTR.setOnClickListener { setLogoPosition(TranslateTo.TOP_RIGHT) }
        binding.btnPosCenter.setOnClickListener { setLogoPosition(TranslateTo.CENTER) }
        binding.btnPosBL.setOnClickListener { setLogoPosition(TranslateTo.BOTTOM_LEFT) }
        binding.btnPosBR.setOnClickListener { setLogoPosition(TranslateTo.BOTTOM_RIGHT) }

        binding.seekGain.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val gainFactor = progress / 100f // 0 -> 0x, 100 -> 1x, 400 -> 4x
                gainMicSource.setGain(gainFactor)
                binding.tvGainLabel.text = "Gain: $progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.switchNoiseReduction.setOnCheckedChangeListener { _, isChecked ->
            gainMicSource.setNoiseSuppression(isChecked)
        }
        binding.switchEchoCancel.setOnCheckedChangeListener { _, isChecked ->
            gainMicSource.setEchoCancel(isChecked)
        }

        binding.btnStartStop.setOnClickListener {
            if (!isStreaming) startStreaming() else stopStreaming()
        }
    }

    // ---------------- Video source switching ----------------

    private fun switchToBuiltInCamera() {
        try {
            genericStream.changeVideoSource(Camera2Source(this))
            usingUvc = false
            Toast.makeText(this, "Built-in camera active", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Camera switch fail: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun switchToUvcCaptureCard() {
        try {
            // CameraUvcSource auto-detects a connected UVC (USB Video Class) device —
            // this is exactly what most HDMI-to-USB capture cards present themselves as.
            // Phone must support USB-OTG (host mode) and be connected via an OTG cable.
            val uvcSource = CameraUvcSource()
            genericStream.changeVideoSource(uvcSource)
            usingUvc = true
            Toast.makeText(this, "HDMI Capture Card source active (agar connected hai)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Capture card नहीं मिला। USB-OTG cable se जोड़ें और दोबारा try करें: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ---------------- Logo overlay ----------------

    private fun applyLogo(uri: Uri) {
        try {
            val bitmap: Bitmap = if (android.os.Build.VERSION.SDK_INT >= 28) {
                val source = android.graphics.ImageDecoder.createSource(contentResolver, uri)
                android.graphics.ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }

            val filter = imageFilter ?: ImageObjectFilterRender().also {
                imageFilter = it
                genericStream.getGlInterface().addFilter(it)
            }
            filter.setImage(bitmap)
            filter.setPosition(TranslateTo.TOP_RIGHT) // default position
            filter.setScale(20f, 20f) // ~20% of preview size, adjust as needed
        } catch (e: Exception) {
            Toast.makeText(this, "Logo load नहीं हुआ: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setLogoPosition(position: TranslateTo) {
        imageFilter?.setPosition(position)
            ?: Toast.makeText(this, "पहले कोई logo चुनें", Toast.LENGTH_SHORT).show()
    }

    private fun removeLogo() {
        imageFilter?.let {
            genericStream.getGlInterface().removeFilter(it)
            imageFilter = null
        }
    }

    // ---------------- Permissions ----------------

    private fun hasPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // ---------------- Streaming ----------------

    private fun startStreaming() {
        val streamKey = binding.etStreamKey.text.toString().trim()
        if (streamKey.isEmpty()) {
            Toast.makeText(this, "पहले Stream Key डालें", Toast.LENGTH_SHORT).show()
            return
        }
        if (!hasPermissions()) {
            Toast.makeText(this, "Camera/Mic permission चाहिए", Toast.LENGTH_SHORT).show()
            ActivityCompat.requestPermissions(this, requiredPermissions, permissionRequestCode)
            return
        }

        val fullRtmpUrl = "$youtubeRtmpBase/$streamKey"

        val prepared = try {
            genericStream.prepareVideo(1280, 720, 2500 * 1024) &&
                genericStream.prepareAudio(44100, true, 128 * 1024)
        } catch (e: Exception) {
            Log.e("YTStreamer", "prepare failed", e)
            false
        }

        if (prepared) {
            genericStream.startStream(fullRtmpUrl)
        } else {
            Toast.makeText(this, "Encoder prepare करने में समस्या", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopStreaming() {
        genericStream.stopStream()
        isStreaming = false
        binding.btnStartStop.text = "Go Live"
        binding.tvStatus.text = "Status: Stopped"
    }

    override fun onResume() {
        super.onResume()
        if (hasPermissions() && !genericStream.isOnPreview) {
            genericStream.startPreview(binding.openGlView)
        }
    }

    override fun onPause() {
        super.onPause()
        if (genericStream.isStreaming) stopStreaming()
        if (genericStream.isOnPreview) genericStream.stopPreview()
    }

    // ----- ConnectChecker callbacks -----

    override fun onConnectionStarted(url: String) {
        runOnUiThread { binding.tvStatus.text = "Status: Connecting..." }
    }

    override fun onConnectionSuccess() {
        isStreaming = true
        runOnUiThread {
            binding.btnStartStop.text = "Stop"
            binding.tvStatus.text = "Status: LIVE on YouTube"
        }
    }

    override fun onConnectionFailed(reason: String) {
        runOnUiThread {
            binding.tvStatus.text = "Status: Failed - $reason"
            Toast.makeText(this, "Connection Failed: $reason", Toast.LENGTH_LONG).show()
            stopStreaming()
        }
    }

    override fun onNewBitrate(bitrate: Long) {}

    override fun onDisconnect() {
        runOnUiThread {
            binding.tvStatus.text = "Status: Disconnected"
            binding.btnStartStop.text = "Go Live"
            isStreaming = false
        }
    }

    override fun onAuthError() {
        runOnUiThread { binding.tvStatus.text = "Status: Auth Error (check Stream Key)" }
    }

    override fun onAuthSuccess() {}
}
