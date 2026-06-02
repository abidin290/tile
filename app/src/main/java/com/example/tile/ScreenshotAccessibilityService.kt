package com.aarash.idin

import android.accessibilityservice.AccessibilityService
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Layanan Aksesibilitas yang super responsif.
 * Menyediakan fitur Screenshot Tile instan dan Markup Editor langsung
 * berbasis WindowManager Overlay (tanpa beralih Activity).
 */
class ScreenshotAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var activeInstance: ScreenshotAccessibilityService? = null
            private set
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var canvasView: MarkupCanvasView? = null
    private var dialogCancelOverlay: View? = null
    private var isOverlayShowing = false

    private val colors = intArrayOf(
        Color.RED,
        Color.parseColor("#FFD700"), // Gold/Yellow
        Color.GREEN,
        Color.BLUE,
        Color.WHITE,
        Color.BLACK
    )
    private var activeColor = Color.RED

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        activeInstance = null
        dismissMarkupOverlay()
        return super.onUnbind(intent)
    }

    /**
     * Menangani penekanan tombol BACK fisik secara global ketika editor melayang aktif.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
            if (isOverlayShowing) {
                val dialog = dialogCancelOverlay
                if (dialog != null && dialog.visibility == View.VISIBLE) {
                    dialog.visibility = View.GONE
                } else {
                    val canvas = canvasView
                    if (canvas != null && canvas.elements.isNotEmpty()) {
                        dialog?.visibility = View.VISIBLE
                    } else {
                        dismissMarkupOverlay()
                    }
                }
                return true // Konsumsi event tombol BACK agar tidak memengaruhi aplikasi lain
            }
        }
        return super.onKeyEvent(event)
    }

    /**
     * Dipanggil oleh ScreenshotTileService saat tile ditekan.
     */
    fun takeScreenshot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11 (API 30)+ capture programmatically
            try {
                takeScreenshot(
                    android.view.Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult) {
                            val hardwareBuffer = screenshotResult.hardwareBuffer
                            val colorSpace = screenshotResult.colorSpace
                            val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                            hardwareBuffer.close() // Prevent native memory leaks

                            if (bitmap != null) {
                                // Copy to a software-backed bitmap to allow editing
                                val softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                                // Launch custom WindowManager Editor Overlay
                                showMarkupOverlay(softwareBitmap)
                            } else {
                                performLegacyScreenshot()
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            performLegacyScreenshot()
                        }
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
                performLegacyScreenshot()
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Android 9-10 fallback
            performLegacyScreenshot()
        } else {
            Toast.makeText(this, "Fitur ini butuh Android 9 (Pie) ke atas", Toast.LENGTH_LONG).show()
        }
    }

    private fun performLegacyScreenshot() {
        val success = performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        if (!success) {
            Toast.makeText(this, R.string.notif_screenshot_failed, Toast.LENGTH_SHORT).show()
        } else {
            fetchToastMessageFromGithub()
        }
    }

    /**
     * Memunculkan UI Editor secara instan langsung di atas WindowManager sistem
     */
    private fun showMarkupOverlay(bitmap: Bitmap) {
        if (isOverlayShowing) return

        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.activity_markup, null)

        val oView = overlayView ?: return

        canvasView = oView.findViewById(R.id.canvasView)
        canvasView?.bitmap = bitmap

        dialogCancelOverlay = oView.findViewById(R.id.dialogCancelOverlay)

        // Bind bottom tools
        val toolBrush = oView.findViewById<TextView>(R.id.toolBrush)
        val toolShape = oView.findViewById<TextView>(R.id.toolShape)
        val toolCrop = oView.findViewById<TextView>(R.id.toolCrop)

        // Option panels
        val panelBrushOptions = oView.findViewById<LinearLayout>(R.id.panelBrushOptions)
        val panelShapeOptions = oView.findViewById<LinearLayout>(R.id.panelShapeOptions)
        val panelCropOptions = oView.findViewById<LinearLayout>(R.id.panelCropOptions)
        val seekBarBrushSize = oView.findViewById<SeekBar>(R.id.seekBarBrushSize)

        // Setup Actions Click Listeners
        oView.findViewById<TextView>(R.id.btnCancel).setOnClickListener {
            handleCancelClick()
        }
        oView.findViewById<ImageView>(R.id.btnUndo).setOnClickListener {
            canvasView?.undo()
        }
        oView.findViewById<ImageView>(R.id.btnRedo).setOnClickListener {
            canvasView?.redo()
        }
        oView.findViewById<ImageView>(R.id.btnShare).setOnClickListener {
            val finalBmp = canvasView?.getFinalBitmap()
            if (finalBmp != null) {
                handleShare(finalBmp)
                dismissMarkupOverlay()
            } else {
                Toast.makeText(this, "Gagal memproses gambar", Toast.LENGTH_SHORT).show()
            }
        }
        oView.findViewById<TextView>(R.id.btnSave).setOnClickListener {
            val finalBmp = canvasView?.getFinalBitmap()
            if (finalBmp != null) {
                val success = saveBitmapToGallery(finalBmp)
                if (success) {
                    Toast.makeText(this, "Screenshot berhasil disimpan ke Galeri", Toast.LENGTH_LONG).show()
                    dismissMarkupOverlay()
                } else {
                    Toast.makeText(this, "Gagal menyimpan gambar", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Gagal memproses gambar", Toast.LENGTH_SHORT).show()
            }
        }

        // Dialog Cancel Confirmations
        oView.findViewById<Button>(R.id.dialogCancelBtnNo).setOnClickListener {
            dialogCancelOverlay?.visibility = View.GONE
        }
        oView.findViewById<Button>(R.id.dialogCancelBtnYes).setOnClickListener {
            dismissMarkupOverlay()
        }

        // Setup Bottom Tool Listeners
        toolBrush.setOnClickListener {
            switchTool(MarkupCanvasView.Tool.BRUSH, toolBrush, oView)
        }
        toolShape.setOnClickListener {
            switchTool(MarkupCanvasView.Tool.SHAPE, toolShape, oView)
        }
        toolCrop.setOnClickListener {
            switchTool(MarkupCanvasView.Tool.CROP, toolCrop, oView)
        }

        setupColorPalette(oView)
        setupBrushSizeSeekbar(seekBarBrushSize)
        setupShapeSelectors(oView)
        setupCropSelectors(oView)

        // Window Manager Layout Configuration
        val layoutParams = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
        }

        windowManager?.addView(oView, layoutParams)
        isOverlayShowing = true
    }

    private fun dismissMarkupOverlay() {
        if (isOverlayShowing && overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
            canvasView = null
            dialogCancelOverlay = null
            isOverlayShowing = false
        }
    }

    private fun handleCancelClick() {
        val canvas = canvasView
        if (canvas != null && canvas.elements.isNotEmpty()) {
            dialogCancelOverlay?.visibility = View.VISIBLE
        } else {
            dismissMarkupOverlay()
        }
    }

    private fun switchTool(tool: MarkupCanvasView.Tool, toolTextView: TextView, root: View) {
        canvasView?.activeTool = tool
        
        val toolBrush = root.findViewById<TextView>(R.id.toolBrush)
        val toolShape = root.findViewById<TextView>(R.id.toolShape)
        val toolCrop = root.findViewById<TextView>(R.id.toolCrop)

        val tools = listOf(toolBrush, toolShape, toolCrop)
        for (t in tools) {
            t.setTextColor(Color.parseColor("#88FFFFFF"))
            t.setTypeface(null, android.graphics.Typeface.NORMAL)
        }

        toolTextView.setTextColor(Color.parseColor("#1A73E8"))
        toolTextView.setTypeface(null, android.graphics.Typeface.BOLD)

        val panelBrushOptions = root.findViewById<LinearLayout>(R.id.panelBrushOptions)
        val panelShapeOptions = root.findViewById<LinearLayout>(R.id.panelShapeOptions)
        val panelCropOptions = root.findViewById<LinearLayout>(R.id.panelCropOptions)

        panelBrushOptions.visibility = View.GONE
        panelShapeOptions.visibility = View.GONE
        panelCropOptions.visibility = View.GONE

        when (tool) {
            MarkupCanvasView.Tool.BRUSH, MarkupCanvasView.Tool.SHAPE -> {
                panelBrushOptions.visibility = View.VISIBLE
                if (tool == MarkupCanvasView.Tool.SHAPE) {
                    panelShapeOptions.visibility = View.VISIBLE
                }
            }
            MarkupCanvasView.Tool.CROP -> {
                panelCropOptions.visibility = View.VISIBLE
            }
        }
        canvasView?.invalidate()
    }

    private fun setupColorPalette(root: View) {
        val colorViews = listOf(
            Pair(root.findViewById<View>(R.id.colorRed), Color.RED),
            Pair(root.findViewById<View>(R.id.colorYellow), Color.parseColor("#FFD700")),
            Pair(root.findViewById<View>(R.id.colorGreen), Color.GREEN),
            Pair(root.findViewById<View>(R.id.colorBlue), Color.BLUE),
            Pair(root.findViewById<View>(R.id.colorWhite), Color.WHITE),
            Pair(root.findViewById<View>(R.id.colorBlack), Color.BLACK)
        )

        for (item in colorViews) {
            val view = item.first
            val color = item.second

            val gd = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke(if (color == activeColor) 6 else 2, if (color == Color.WHITE) Color.LTGRAY else Color.WHITE)
            }
            view.background = gd

            view.setOnClickListener {
                activeColor = color
                canvasView?.brushColor = color
                
                for (other in colorViews) {
                    val otherView = other.first
                    val otherColor = other.second
                    val otherGd = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(otherColor)
                        setStroke(if (otherColor == activeColor) 6 else 2, if (otherColor == Color.WHITE) Color.LTGRAY else Color.WHITE)
                    }
                    otherView.background = otherGd
                }
            }
        }
    }

    private fun setupBrushSizeSeekbar(seekBarBrushSize: SeekBar) {
        seekBarBrushSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                canvasView?.brushSize = progress.coerceAtLeast(4).toFloat()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupShapeSelectors(root: View) {
        root.findViewById<Button>(R.id.btnShapeRect).setOnClickListener {
            canvasView?.shapeType = ShapeElement.Type.RECTANGLE
        }
        root.findViewById<Button>(R.id.btnShapeRoundRect).setOnClickListener {
            canvasView?.shapeType = ShapeElement.Type.ROUNDED_RECTANGLE
        }
        root.findViewById<Button>(R.id.btnShapeCircle).setOnClickListener {
            canvasView?.shapeType = ShapeElement.Type.CIRCLE
        }
        root.findViewById<Button>(R.id.btnShapeLine).setOnClickListener {
            canvasView?.shapeType = ShapeElement.Type.LINE
        }
        root.findViewById<Button>(R.id.btnShapeArrow).setOnClickListener {
            canvasView?.shapeType = ShapeElement.Type.ARROW
        }
    }

    private fun setupCropSelectors(root: View) {
        root.findViewById<Button>(R.id.btnCropFree).setOnClickListener { canvasView?.cropRatio = null }
        root.findViewById<Button>(R.id.btnCrop1_1).setOnClickListener { canvasView?.cropRatio = 1.0f }
        root.findViewById<Button>(R.id.btnCrop4_3).setOnClickListener { canvasView?.cropRatio = 4f / 3f }
        root.findViewById<Button>(R.id.btnCrop16_9).setOnClickListener { canvasView?.cropRatio = 16f / 9f }
        root.findViewById<Button>(R.id.btnCropReset).setOnClickListener {
            canvasView?.cropRatio = null
            canvasView?.bitmap?.let { bmp ->
                canvasView?.cropRect?.set(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
                canvasView?.resetTransformations()
            }
        }
    }

    private fun saveBitmapToGallery(bmp: Bitmap): Boolean {
        val filename = "QuickTile_${System.currentTimeMillis()}.png"
        var success = false
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Screenshots")
            }
            val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (imageUri != null) {
                try {
                    resolver.openOutputStream(imageUri).use { out ->
                        if (out != null) {
                            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                            success = true
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            // Legacy storage fallback (Android 9/Pie)
            @Suppress("DEPRECATION")
            val imagesDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_PICTURES
            )
            val screenshotDir = File(imagesDir, "Screenshots")
            if (!screenshotDir.exists()) {
                screenshotDir.mkdirs()
            }
            val file = File(screenshotDir, filename)
            try {
                FileOutputStream(file).use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    success = true
                }
                android.media.MediaScannerConnection.scanFile(
                    this, arrayOf(file.absolutePath), arrayOf("image/png"), null
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return success
    }

    private fun handleShare(bmp: Bitmap) {
        try {
            val shareFile = File(cacheDir, "share_screenshot.png")
            FileOutputStream(shareFile).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", shareFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            // To start a share chooser from a Service context, we MUST set FLAG_ACTIVITY_NEW_TASK
            val chooserIntent = Intent.createChooser(intent, "Bagikan via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(chooserIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Gagal membagikan gambar", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchToastMessageFromGithub() {
        Thread {
            var message = "screenshot berhasil"
            try {
                val url = java.net.URL("https://raw.githubusercontent.com/abidin290/idtv/refs/heads/main/takaraporo.txt")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 3000
                connection.readTimeout = 3000

                if (connection.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                    val stream = connection.inputStream
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(stream))
                    val response = java.lang.StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line).append("\n")
                    }
                    reader.close()
                    val fetchedMessage = response.toString().replace("\u0000", "").trim()
                    if (fetchedMessage.isNotEmpty()) {
                        message = fetchedMessage
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(this@ScreenshotAccessibilityService, message, Toast.LENGTH_SHORT).show()
            }
        }.start()
    }
}
