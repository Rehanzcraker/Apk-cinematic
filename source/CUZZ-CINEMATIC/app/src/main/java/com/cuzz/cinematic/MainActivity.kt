
package com.cuzz.cinematic

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var status: TextView
    private lateinit var shutter: FrameLayout
    private lateinit var shutterCore: View
    private lateinit var qualityText: TextView
    private lateinit var liveButton: TextView

    private var camera: Camera? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var imageCapture: ImageCapture? = null
    private var recording: Recording? = null

    private var frontCamera = false
    private var torchOn = false
    private var stabilizationOn = true
    private var quality1080 = true
    private var livePhotoEnabled = true
    private var currentMode = "VIDEO"

    private val handler = Handler(Looper.getMainLooper())

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            if (result[Manifest.permission.CAMERA] == true) {
                startCamera()
            } else {
                status.text = "IZIN KAMERA DIPERLUKAN"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        buildInterface()

        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT <= 28) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        if (permissions.all {
                ContextCompat.checkSelfPermission(this, it) ==
                    PackageManager.PERMISSION_GRANTED
            }) {
            startCamera()
        } else {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun background(
        color: Int,
        radius: Float,
        stroke: Int = 0
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius.toInt()).toFloat()
            if (stroke > 0) {
                setStroke(dp(stroke), Color.WHITE)
            }
        }
    }

    private fun text(
        value: String,
        size: Float,
        color: Int = Color.WHITE
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
    }

    private fun buildInterface() {

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        root.addView(
            previewView,
            FrameLayout.LayoutParams(-1, -1)
        )

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(0x55000000)
        }

        val flashButton = text("ϟ", 27f)
        top.addView(flashButton, LinearLayout.LayoutParams(dp(42), dp(48)))

        flashButton.setOnClickListener {
            torchOn = !torchOn
            camera?.cameraControl?.enableTorch(torchOn && !frontCamera)
            flashButton.alpha = if (torchOn) 1f else 0.55f
        }

        liveButton = text("LIVE ●", 12f).apply {
            setPadding(dp(9), dp(8), dp(9), dp(8))
            background = background(0x66000000, 20f, 1)
        }

        top.addView(liveButton)

        liveButton.setOnClickListener {
            livePhotoEnabled = !livePhotoEnabled
            liveButton.text = if (livePhotoEnabled) "LIVE ●" else "LIVE OFF"
            liveButton.setTextColor(
                if (livePhotoEnabled) 0xFFFFD88A.toInt()
                else Color.WHITE
            )
            status.text = if (livePhotoEnabled)
                "LIVE PHOTO AKTIF"
            else
                "LIVE PHOTO NONAKTIF"
        }

        top.addView(
            View(this),
            LinearLayout.LayoutParams(0, 1, 1f)
        )

        qualityText = text("1080P 30", 12f).apply {
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = background(0x66000000, 20f)
        }

        top.addView(qualityText)

        qualityText.setOnClickListener {
            if (recording != null) return@setOnClickListener

            quality1080 = !quality1080
            qualityText.text = if (quality1080) "1080P 30" else "720P 30"
            startCamera()
        }

        val settingsButton = text("⋯", 27f)
        top.addView(settingsButton, LinearLayout.LayoutParams(dp(42), dp(48)))

        settingsButton.setOnClickListener {
            showSettings()
        }

        root.addView(
            top,
            FrameLayout.LayoutParams(-1, dp(66), Gravity.TOP)
        )

        status = text("MENYIAPKAN KAMERA", 11f).apply {
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = background(0x88000000.toInt(), 18f)
        }

        root.addView(
            status,
            FrameLayout.LayoutParams(
                -2, -2,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply {
                topMargin = dp(78)
            }
        )

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(24))
            setBackgroundColor(0x99000000.toInt())
        }

        val zoomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        listOf("0.5x", "1x", "2x").forEach { zoom ->
            val button = text(
                zoom,
                if (zoom == "1x") 16f else 13f,
                if (zoom == "1x") 0xFFFFD88A.toInt() else Color.WHITE
            )

            button.setPadding(dp(17), dp(12), dp(17), dp(12))
            zoomRow.addView(button)

            button.setOnClickListener {
                val ratio = when (zoom) {
                    "0.5x" -> 0.5f
                    "2x" -> 2f
                    else -> 1f
                }
                setZoom(ratio)
            }
        }

        bottom.addView(zoomRow)

        val modes = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
        }

        listOf("CINEMATIC", "VIDEO", "PHOTO").forEach { mode ->

            val item = text(
                mode,
                12f,
                if (mode == currentMode) 0xFFFFD88A.toInt()
                else Color.WHITE
            )

            item.setPadding(dp(13), dp(10), dp(13), dp(10))
            modes.addView(item)

            item.setOnClickListener {
                if (recording != null) return@setOnClickListener

                currentMode = mode

                for (i in 0 until modes.childCount) {
                    val child = modes.getChildAt(i) as TextView
                    child.setTextColor(
                        if (child.text == currentMode)
                            0xFFFFD88A.toInt()
                        else Color.WHITE
                    )
                }

                status.text = when (mode) {
                    "CINEMATIC" -> "CINEMATIC MODE"
                    "PHOTO" -> "PHOTO MODE"
                    else -> "VIDEO MODE"
                }
            }
        }

        bottom.addView(modes)

        val shutterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val gallery = text("▣", 27f)
        shutterRow.addView(gallery, LinearLayout.LayoutParams(dp(65), dp(70)))

        gallery.setOnClickListener {
            openGallery()
        }

        val holder = FrameLayout(this)

        shutter = FrameLayout(this).apply {
            background = background(Color.TRANSPARENT, 50f, 2)
        }

        shutterCore = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFFFF7E8.toInt())
            }
        }

        shutter.addView(
            shutterCore,
            FrameLayout.LayoutParams(dp(57), dp(57), Gravity.CENTER)
        )

        holder.addView(
            shutter,
            FrameLayout.LayoutParams(dp(76), dp(76), Gravity.CENTER)
        )

        shutterRow.addView(holder, LinearLayout.LayoutParams(dp(100), dp(82)))

        shutter.setOnClickListener {
            when (currentMode) {
                "PHOTO" -> {
                    if (livePhotoEnabled) takeLivePhoto()
                    else takePhoto()
                }
                else -> toggleRecording()
            }
        }

        val flip = text("⟳", 32f)
        shutterRow.addView(flip, LinearLayout.LayoutParams(dp(65), dp(70)))

        flip.setOnClickListener {
            if (recording != null) return@setOnClickListener
            frontCamera = !frontCamera
            torchOn = false
            startCamera()
        }

        bottom.addView(shutterRow)

        root.addView(
            bottom,
            FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
        )

        setContentView(root)
    }

    private fun showSettings() {
        val items = arrayOf(
            "PILIH FILTER",
            "TONE & WARMTH",
            "STABILIZER ON/OFF"
        )

        android.app.AlertDialog.Builder(this)
            .setTitle("CUZZ CINEMATIC")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showFilterDialog()
                    1 -> showToneWarmth()
                    2 -> {
                        stabilizationOn = !stabilizationOn
                        startCamera()
                    }
                }
            }
            .show()
    }

    private fun showFilterDialog() {
        val items = PhotoFilters.filters.toTypedArray()

        android.app.AlertDialog.Builder(this)
            .setTitle("IPHONE STYLE FILTER")
            .setSingleChoiceItems(
                items,
                PhotoFilters.filters.indexOf(PhotoFilters.selected)
            ) { dialog, which ->
                PhotoFilters.selected = items[which]
                status.text = "FILTER: ${items[which]}"
                dialog.dismiss()
            }
            .setNegativeButton("TUTUP", null)
            .show()
    }

    // FILTER CONTROL: INTENSITAS, TONE, WARMTH
    private fun showToneWarmth() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(16))
        }

        val intensityLabel = text(
            "INTENSITAS FILTER: ${PhotoFilters.intensity}%",
            14f
        )

        val intensitySlider = SeekBar(this).apply {
            max = 100
            progress = PhotoFilters.intensity
        }

        intensitySlider.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    PhotoFilters.intensity = progress
                    intensityLabel.text = "INTENSITAS FILTER: $progress%"
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            }
        )

        val toneLabel = text("TONE: ${PhotoFilters.tone.toInt()}", 14f)

        val toneSlider = SeekBar(this).apply {
            max = 100
            progress = (PhotoFilters.tone + 50).toInt().coerceIn(0, 100)
        }

        toneSlider.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    PhotoFilters.tone = (progress - 50).toFloat()
                    toneLabel.text = "TONE: ${PhotoFilters.tone.toInt()}"
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            }
        )

        val warmthLabel = text("WARMTH: ${PhotoFilters.warmth.toInt()}", 14f)

        val warmthSlider = SeekBar(this).apply {
            max = 100
            progress = (PhotoFilters.warmth + 50).toInt().coerceIn(0, 100)
        }

        warmthSlider.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    PhotoFilters.warmth = (progress - 50).toFloat()
                    warmthLabel.text = "WARMTH: ${PhotoFilters.warmth.toInt()}"
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            }
        )

        layout.addView(intensityLabel)
        layout.addView(intensitySlider)
        layout.addView(toneLabel)
        layout.addView(toneSlider)
        layout.addView(warmthLabel)
        layout.addView(warmthSlider)

        android.app.AlertDialog.Builder(this)
            .setTitle("FILTER CONTROL")
            .setView(layout)
            .setPositiveButton("SIMPAN", null)
            .show()
    }

    private fun setZoom(requested: Float) {
        val state = camera?.cameraInfo?.zoomState?.value ?: return

        val ratio = requested.coerceIn(
            state.minZoomRatio,
            state.maxZoomRatio
        )

        camera?.cameraControl?.setZoomRatio(ratio)
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)

        future.addListener({
            try {
                val provider = future.get()

                val selector = if (frontCamera)
                    CameraSelector.DEFAULT_FRONT_CAMERA
                else
                    CameraSelector.DEFAULT_BACK_CAMERA

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val cameraInfo = provider.getCameraInfo(selector)

                val quality = if (quality1080) Quality.FHD else Quality.HD

                val qualitySelector = QualitySelector.from(
                    quality,
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                )

                val recorder = Recorder.Builder()
                    .setQualitySelector(qualitySelector)
                    .build()

                val capabilities = Recorder.getVideoCapabilities(cameraInfo)
                val enableEis =
                    stabilizationOn && capabilities.isStabilizationSupported

                val video = VideoCapture.Builder(recorder)
                    .setVideoStabilizationEnabled(enableEis)
                    .build()

                val photo = ImageCapture.Builder()
                    .setCaptureMode(
                        ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                    )
                    .build()

                provider.unbindAll()

                camera = provider.bindToLifecycle(
                    this,
                    selector,
                    preview,
                    video,
                    photo
                )

                videoCapture = video
                imageCapture = photo

                camera?.cameraControl?.enableTorch(
                    torchOn && !frontCamera
                )

                status.text = when {
                    enableEis -> "EIS AKTIF"
                    stabilizationOn -> "EIS TIDAK DIDUKUNG"
                    else -> "STABILIZER OFF"
                }

            } catch (e: Exception) {
                status.text = "KAMERA ERROR: ${e.localizedMessage}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleRecording() {
        if (recording != null) {
            recording?.stop()
            return
        }

        val capture = videoCapture ?: return
        val filename = "CUZZ_${System.currentTimeMillis()}.mp4"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) {
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    "Movies/CUZZ CINEMATIC"
                )
            }
        }

        val output = MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build()

        var pending = capture.output.prepareRecording(this, output)

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            pending = pending.withAudioEnabled()
        }

        recording = pending.start(
            ContextCompat.getMainExecutor(this)
        ) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    status.text = "● MEREKAM"
                    shutterCore.background = background(
                        0xFFE53935.toInt(), 8f
                    )
                }

                is VideoRecordEvent.Finalize -> {
                    recording = null
                    resetShutter()

                    status.text = if (!event.hasError())
                        "VIDEO TERSIMPAN"
                    else
                        "VIDEO GAGAL: ${event.error}"
                }
            }
        }
    }


private fun takeLivePhoto() {
    if (recording != null) {
        status.text = "TUNGGU REKAMAN SELESAI"
        return
    }

    val capture = videoCapture ?: run {
        status.text = "KAMERA VIDEO BELUM SIAP"
        return
    }

    val liveId = System.currentTimeMillis()
    val filename = "CUZZ_LIVE_$liveId.mp4"

    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")

        if (Build.VERSION.SDK_INT >= 29) {
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                "Movies/CUZZ CINEMATIC"
            )
        }
    }

    val output = MediaStoreOutputOptions.Builder(
        contentResolver,
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    ).setContentValues(values).build()

    try {
        val pending = capture.output.prepareRecording(this, output)

        recording = pending.start(
            ContextCompat.getMainExecutor(this)
        ) { event ->

            when (event) {
                is VideoRecordEvent.Start -> {
                    status.text = "LIVE PHOTO • MEREKAM"

                    // Ambil foto setelah perekaman benar-benar dimulai.
                    takePhoto(liveId)

                    // Hentikan motion setelah sekitar 3 detik.
                    handler.postDelayed({
                        recording?.stop()
                    }, 3000)
                }

                is VideoRecordEvent.Finalize -> {
                    recording = null
                    resetShutter()

                    status.text = if (!event.hasError()) {
                        "LIVE PHOTO TERSIMPAN"
                    } else {
                        "LIVE PHOTO GAGAL: ${event.error}"
                    }
                }
            }
        }

    } catch (e: Exception) {
        recording = null
        status.text = "LIVE PHOTO ERROR: ${e.message}"
    }
}

        if (recording != null) {
            status.text = "TUNGGU VIDEO SELESAI"
            return
        }

        val capture = videoCapture

        if (capture == null) {
            takePhoto()
            return
        }

        val filename = "CUZZ_LIVE_${System.currentTimeMillis()}.mp4"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) {
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    "Movies/CUZZ CINEMATIC"
                )
            }
        }

        val output = MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build()

        try {
            val pending = capture.output.prepareRecording(this, output)

            recording = pending.start(
                ContextCompat.getMainExecutor(this)
            ) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        status.text = "LIVE PHOTO • MEREKAM"
                    }

                    is VideoRecordEvent.Finalize -> {
                        recording = null
                        if (!event.hasError()) {
                            status.text = "LIVE PHOTO TERSIMPAN"
                        } else {
                            status.text = "KLIP LIVE GAGAL: ${event.error}"
                        }
                    }
                }
            }

            takePhoto()

            handler.postDelayed({
                recording?.stop()
            }, 2500)

        } catch (e: Exception) {
            recording = null
            status.text = "LIVE PHOTO ERROR: ${e.message}"
            takePhoto()
        }
    }

    private fun takePhoto(liveId: Long? = null) {
        val capture = imageCapture ?: return

        val filename = if (liveId != null) {
    "CUZZ_LIVE_$liveId.jpg"
} else {
    "CUZZ_PHOTO_${System.currentTimeMillis()}.jpg"
}

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "Pictures/CUZZ CINEMATIC"
                )
            }
        }

        val options = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ).build()

        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    result: ImageCapture.OutputFileResults
                ) {
                    val uri = result.savedUri

                    if (uri == null) {
                        status.text = "FOTO TERSIMPAN"
                        return
                    }

                    try {
                        val bitmap = contentResolver
                            .openInputStream(uri)?.use {
                                android.graphics.BitmapFactory.decodeStream(it)
                            }

                        if (bitmap == null) {
                            status.text = "FOTO TERSIMPAN"
                            return
                        }

                        val filtered = PhotoFilters.apply(bitmap)

                        contentResolver.openOutputStream(uri, "wt")?.use {
                            filtered.compress(
                                Bitmap.CompressFormat.JPEG,
                                95,
                                it
                            )
                        }

                        filtered.recycle()
                        bitmap.recycle()

                        status.text = "FOTO FILTER TERSIMPAN"

                    } catch (e: Exception) {
                        status.text = "FILTER ERROR: ${e.message}"
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    status.text = "FOTO GAGAL: ${exception.message}"
                }
            }
        )
    }

    private fun resetShutter() {
        shutterCore.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFFFFF7E8.toInt())
        }
    }

    private fun openGallery() {
        val intent = android.content.Intent(
            android.content.Intent.ACTION_VIEW
        ).apply {
            setDataAndType(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "image/*"
            )
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            startActivity(intent)
        } catch (_: Exception) {
            status.text = "GALERI TIDAK DITEMUKAN"
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        recording?.stop()
        super.onDestroy()
    }
}
