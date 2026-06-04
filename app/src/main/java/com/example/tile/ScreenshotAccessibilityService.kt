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
    private var optionsPanel: View? = null
    private var isOverlayShowing = false
    private var activeColor = Color.RED

    private val colors = intArrayOf(
        Color.RED,
        Color.parseColor("#FFD700"), // Yellow/Gold
        Color.GREEN,
        Color.BLUE,
        Color.CYAN,
        Color.parseColor("#008080"), // Tosca
        Color.WHITE,
        Color.BLACK
    )

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
                return true
            }
        }
        return super.onKeyEvent(event)
    }

    fun takeScreenshot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                takeScreenshot(
                    android.view.Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult) {
                            val hardwareBuffer = screenshotResult.hardwareBuffer
                            val colorSpace = screenshotResult.colorSpace
                            val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                            hardwareBuffer.close()

                            if (bitmap != null) {
                                val softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
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

    private fun showMarkupOverlay(bitmap: Bitmap) {
        if (isOverlayShowing) return

        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.activity_markup, null)

        val oView = overlayView ?: return

        canvasView = oView.findViewById(R.id.canvasView)
        canvasView?.bitmap = bitmap

        dialogCancelOverlay = oView.findViewById(R.id.dialogCancelOverlay)
        optionsPanel = oView.findViewById(R.id.optionsPanel)

        // Bind floating bottom buttons
        val toolBrush = oView.findViewById<ImageView>(R.id.toolBrush)
        val toolCrop = oView.findViewById<ImageView>(R.id.toolCrop)
        val btnSave = oView.findViewById<ImageView>(R.id.btnSave)
        val btnShare = oView.findViewById<ImageView>(R.id.btnShare)

        // Bind Batal button click handler
        oView.findViewById<ImageView>(R.id.btnCancel).setOnClickListener {
            handleCancelClick()
        }

        // Bind Undo/Redo listeners inside Brush panel
        oView.findViewById<ImageView>(R.id.btnUndoBrush).setOnClickListener { canvasView?.undo() }
        oView.findViewById<ImageView>(R.id.btnRedoBrush).setOnClickListener { canvasView?.redo() }

        // Bind Undo/Redo listeners inside Crop panel
        oView.findViewById<ImageView>(R.id.btnUndoCrop).setOnClickListener { canvasView?.undo() }
        oView.findViewById<ImageView>(R.id.btnRedoCrop).setOnClickListener { canvasView?.redo() }
        
        btnShare.setOnClickListener {
            val finalBmp = canvasView?.getFinalBitmap()
            if (finalBmp != null) {
                handleShare(finalBmp)
                dismissMarkupOverlay()
            } else {
                Toast.makeText(this, "Gagal memproses gambar", Toast.LENGTH_SHORT).show()
            }
        }
        
        btnSave.setOnClickListener {
            val finalBmp = canvasView?.getFinalBitmap()
            if (finalBmp != null) {
                val success = saveBitmapToGallery(finalBmp)
                if (success) {
                    Toast.makeText(this, "Screenshot saved", Toast.LENGTH_SHORT).show()
                    dismissMarkupOverlay()
                } else {
                    Toast.makeText(this, "Gagal menyimpan gambar", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Gagal memproses gambar", Toast.LENGTH_SHORT).show()
            }
        }

        // Dialog buttons
        oView.findViewById<Button>(R.id.dialogCancelBtnNo).setOnClickListener {
            dialogCancelOverlay?.visibility = View.GONE
        }
        oView.findViewById<Button>(R.id.dialogCancelBtnYes).setOnClickListener {
            dismissMarkupOverlay()
        }

        // Setup bottom tools selection
        toolBrush.setOnClickListener {
            switchTool(MarkupCanvasView.Tool.BRUSH, toolBrush, toolCrop, oView)
        }
        toolCrop.setOnClickListener {
            switchTool(MarkupCanvasView.Tool.CROP, toolCrop, toolBrush, oView)
        }

        // Initialize default tool visual state (Brush is active)
        highlightTool(toolBrush, true)
        highlightTool(toolCrop, false)

        setupColorPalette(oView)
        setupBrushSizeControls(oView)
        setupCropSelectors(oView)

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
            optionsPanel = null
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

    private fun switchTool(tool: MarkupCanvasView.Tool, activeView: ImageView, inactiveView: ImageView, root: View) {
        canvasView?.activeTool = tool
        highlightTool(activeView, true)
        highlightTool(inactiveView, false)

        val panelCropOptionsLayout = root.findViewById<LinearLayout>(R.id.panelCropOptionsLayout)

        if (tool == MarkupCanvasView.Tool.BRUSH) {
            optionsPanel?.visibility = View.VISIBLE
            panelCropOptionsLayout?.visibility = View.GONE
        } else {
            optionsPanel?.visibility = View.GONE
            panelCropOptionsLayout?.visibility = View.VISIBLE
        }
        canvasView?.invalidate()
    }

    private fun highlightTool(view: ImageView, active: Boolean) {
        if (active) {
            view.setColorFilter(Color.parseColor("#FFA000")) // Orange accent for active
            view.setBackgroundResource(R.drawable.bg_circle_btn)
        } else {
            view.setColorFilter(Color.parseColor("#37474F")) // Dark slate for inactive
            view.background = null
        }
    }

    private fun setupColorPalette(root: View) {
        val colorViews = listOf(
            Pair(root.findViewById<ImageView>(R.id.colorRed), colors[0]),
            Pair(root.findViewById<ImageView>(R.id.colorYellow), colors[1]),
            Pair(root.findViewById<ImageView>(R.id.colorGreen), colors[2]),
            Pair(root.findViewById<ImageView>(R.id.colorBlue), colors[3]),
            Pair(root.findViewById<ImageView>(R.id.colorCyan), colors[4]),
            Pair(root.findViewById<ImageView>(R.id.colorTosca), colors[5]),
            Pair(root.findViewById<ImageView>(R.id.colorWhite), colors[6]),
            Pair(root.findViewById<ImageView>(R.id.colorBlack), colors[7])
        )

        for (item in colorViews) {
            val view = item.first
            val color = item.second

            // Draw solid color circle
            val gd = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke(2, if (color == Color.WHITE) Color.LTGRAY else Color.TRANSPARENT)
            }
            view.background = gd

            // Set checkmark visibility
            if (color == activeColor) {
                view.setColorFilter(if (color == Color.WHITE || color == Color.YELLOW || color == Color.CYAN) Color.BLACK else Color.WHITE)
            } else {
                view.setColorFilter(Color.TRANSPARENT)
            }

            view.setOnClickListener {
                activeColor = color
                canvasView?.brushColor = color
                
                // Update checks
                for (other in colorViews) {
                    val otherView = other.first
                    val otherColor = other.second
                    if (otherColor == activeColor) {
                        otherView.setColorFilter(if (otherColor == Color.WHITE || otherColor == Color.YELLOW || otherColor == Color.CYAN) Color.BLACK else Color.WHITE)
                    } else {
                        otherView.setColorFilter(Color.TRANSPARENT)
                    }
                }
            }
        }
    }

    private fun setupBrushSizeControls(root: View) {
        val seekBarBrushSize = root.findViewById<SeekBar>(R.id.seekBarBrushSize)
        val txtSizePercentage = root.findViewById<TextView>(R.id.txtSizePercentage)

        // Bind preset dots (4 dots)
        val presetXS = root.findViewById<View>(R.id.presetSizeXS)
        val presetS = root.findViewById<View>(R.id.presetSizeS)
        val presetM = root.findViewById<View>(R.id.presetSizeM)
        val presetL = root.findViewById<View>(R.id.presetSizeL)

        // Draw circular dots
        val makeDot = { v: View, size: Int ->
            val gd = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            v.background = gd
            v.layoutParams.width = size
            v.layoutParams.height = size
            v.requestLayout()
        }
        
        makeDot(presetXS, 12)
        makeDot(presetS, 20)
        makeDot(presetM, 28)
        makeDot(presetL, 36)

        val updateSeekbar = { progress: Int ->
            seekBarBrushSize.progress = progress
            canvasView?.brushSize = progress.coerceAtLeast(4).toFloat()
            txtSizePercentage.text = "$progress%"
        }

        presetXS.setOnClickListener { updateSeekbar(8) }
        presetS.setOnClickListener { updateSeekbar(24) }
        presetM.setOnClickListener { updateSeekbar(48) }
        presetL.setOnClickListener { updateSeekbar(80) }

        seekBarBrushSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                canvasView?.brushSize = progress.coerceAtLeast(4).toFloat()
                txtSizePercentage.text = "$progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
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
