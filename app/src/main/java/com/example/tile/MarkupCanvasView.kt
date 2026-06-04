package com.aarash.idin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

class MarkupCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Tool {
        BRUSH, CROP
    }

    var activeTool = Tool.BRUSH
    var brushColor = Color.RED
    var brushSize = 12f
    
    var bitmap: Bitmap? = null
        set(value) {
            field = value
            if (value != null) {
                cropRect.set(0f, 0f, value.width.toFloat(), value.height.toFloat())
                resetTransformations()
            }
            invalidate()
        }

    val elements = mutableListOf<MarkupElement>()
    private val redoStack = mutableListOf<MarkupElement>()

    private var currentPath: Path? = null
    private var startX = 0f
    private var startY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var isDrawing = false

    private val drawMatrix = Matrix()
    private val inverseMatrix = Matrix()
    private var scaleFactor = 1f
    private var translationX = 0f
    private var translationY = 0f

    var cropRatio: Float? = null
        set(value) {
            field = value
            adjustCropRectToRatio()
            invalidate()
        }
    val cropRect = RectF()
    private var activeCropHandle = -1
    private val cropHandleSize = 50f

    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scale = detector.scaleFactor
            val px = detector.focusX
            val py = detector.focusY
            
            val nextScale = scaleFactor * scale
            if (nextScale in 0.5f..10.0f) {
                scaleFactor = nextScale
                drawMatrix.postScale(scale, scale, px, py)
                invalidate()
            }
            return true
        }
    })

    private var overlayBitmap: Bitmap? = null
    private var overlayCanvas: Canvas? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun undo() {
        if (elements.isNotEmpty()) {
            val last = elements.removeAt(elements.size - 1)
            redoStack.add(last)
            invalidate()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val last = redoStack.removeAt(redoStack.size - 1)
            elements.add(last)
            invalidate()
        }
    }

    fun clearAll() {
        elements.clear()
        redoStack.clear()
        invalidate()
    }

    fun resetTransformations() {
        val bmp = bitmap ?: return
        scaleFactor = 1f
        translationX = 0f
        translationY = 0f
        drawMatrix.reset()

        val viewW = width.toFloat()
        val viewH = height.toFloat()
        if (viewW > 0 && viewH > 0) {
            val scaleX = viewW / bmp.width
            val scaleY = viewH / bmp.height
            val scale = minOf(scaleX, scaleY)
            scaleFactor = scale
            
            val dx = (viewW - bmp.width * scale) / 2f
            val dy = (viewH - bmp.height * scale) / 2f
            translationX = dx
            translationY = dy
            drawMatrix.postScale(scale, scale)
            drawMatrix.postTranslate(dx, dy)
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) {
            resetTransformations()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap ?: return

        canvas.save()
        canvas.concat(drawMatrix)
        canvas.drawBitmap(bmp, 0f, 0f, null)

        if (overlayBitmap == null || overlayBitmap?.width != bmp.width || overlayBitmap?.height != bmp.height) {
            overlayBitmap?.recycle()
            overlayBitmap = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
            overlayCanvas = Canvas(overlayBitmap!!)
        }

        val oBmp = overlayBitmap!!
        val oCanvas = overlayCanvas!!
        
        oCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        for (element in elements) {
            element.draw(oCanvas)
        }

        if (isDrawing) {
            if (activeTool == Tool.BRUSH) {
                currentPath?.let {
                    val p = Paint().apply {
                        isAntiAlias = true
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                        color = brushColor
                        strokeWidth = brushSize
                    }
                    oCanvas.drawPath(it, p)
                }
            }
        }

        canvas.drawBitmap(oBmp, 0f, 0f, null)
        canvas.restore()

        if (activeTool == Tool.CROP) {
            drawCropOverlay(canvas)
        } else {
            // Draw a subtle translucent crop border in other modes to indicate the crop bounds
            drawSubtleCropBorder(canvas)
        }
    }

    private fun drawSubtleCropBorder(canvas: Canvas) {
        val tl = imageToViewCoords(cropRect.left, cropRect.top)
        val br = imageToViewCoords(cropRect.right, cropRect.bottom)

        val cl = tl[0]
        val ct = tl[1]
        val cr = br[0]
        val cb = br[1]

        val borderPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRect(cl, ct, cr, cb, borderPaint)

        // Draw overlay shadow outside the crop area (same dark shadow as crop mode for consistency)
        val darkPaint = Paint().apply {
            color = Color.parseColor("#99000000") // Consistent shadow
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), ct, darkPaint)
        canvas.drawRect(0f, cb, width.toFloat(), height.toFloat(), darkPaint)
        canvas.drawRect(0f, ct, cl, cb, darkPaint)
        canvas.drawRect(cr, ct, width.toFloat(), cb, darkPaint)
    }

    private fun viewToImageCoords(vx: Float, vy: Float): FloatArray {
        drawMatrix.invert(inverseMatrix)
        val points = floatArrayOf(vx, vy)
        inverseMatrix.mapPoints(points)
        return points
    }

    private fun imageToViewCoords(ix: Float, iy: Float): FloatArray {
        val points = floatArrayOf(ix, iy)
        drawMatrix.mapPoints(points)
        return points
    }

    private var lastTouchX = 0f
    private var lastTouchY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val bmp = bitmap ?: return false

        scaleGestureDetector.onTouchEvent(event)

        if (event.pointerCount > 1) {
            isDrawing = false
            return true
        }

        val x = event.x
        val y = event.y

        val imgCoords = viewToImageCoords(x, y)
        val ix = imgCoords[0]
        val iy = imgCoords[1]

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = x
                lastTouchY = y

                when (activeTool) {
                    Tool.BRUSH -> {
                        isDrawing = true
                        val path = Path().apply {
                            moveTo(ix, iy)
                        }
                        currentPath = path
                        startX = ix
                        startY = iy
                    }
                    Tool.CROP -> {
                        activeCropHandle = getCropHandleAt(x, y)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = x - lastTouchX
                val dy = y - lastTouchY

                when (activeTool) {
                    Tool.BRUSH -> {
                        if (isDrawing) {
                            currentPath?.lineTo(ix, iy)
                            invalidate()
                        }
                    }
                    Tool.CROP -> {
                        if (activeCropHandle != -1) {
                            moveCropHandle(activeCropHandle, ix, iy, x, y)
                            invalidate()
                        }
                    }
                }
                lastTouchX = x
                lastTouchY = y
            }
            MotionEvent.ACTION_UP -> {
                when (activeTool) {
                    Tool.BRUSH -> {
                        if (isDrawing) {
                            currentPath?.let {
                                elements.add(PathElement(it, brushColor, brushSize, false))
                                redoStack.clear()
                            }
                        }
                    }
                    Tool.CROP -> {
                        activeCropHandle = -1
                    }
                }
                isDrawing = false
                currentPath = null
                invalidate()
            }
        }
        return true
    }

    private fun drawCropOverlay(canvas: Canvas) {
        val bmp = bitmap ?: return

        val tl = imageToViewCoords(cropRect.left, cropRect.top)
        val br = imageToViewCoords(cropRect.right, cropRect.bottom)

        val cl = tl[0]
        val ct = tl[1]
        val cr = br[0]
        val cb = br[1]

        val darkPaint = Paint().apply {
            color = Color.parseColor("#99000000")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), ct, darkPaint)
        canvas.drawRect(0f, cb, width.toFloat(), height.toFloat(), darkPaint)
        canvas.drawRect(0f, ct, cl, cb, darkPaint)
        canvas.drawRect(cr, ct, width.toFloat(), cb, darkPaint)

        val borderPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawRect(cl, ct, cr, cb, borderPaint)

        val gridPaint = Paint().apply {
            color = Color.parseColor("#66FFFFFF")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val w = cr - cl
        val h = cb - ct
        
        canvas.drawLine(cl + w / 3f, ct, cl + w / 3f, cb, gridPaint)
        canvas.drawLine(cl + 2f * w / 3f, ct, cl + 2f * w / 3f, cb, gridPaint)
        
        canvas.drawLine(cl, ct + h / 3f, cr, ct + h / 3f, gridPaint)
        canvas.drawLine(cl, ct + 2f * h / 3f, cr, ct + 2f * h / 3f, gridPaint)

        val handlePaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        drawCornerHandle(canvas, cl, ct, -1, -1, handlePaint)
        drawCornerHandle(canvas, cr, ct, 1, -1, handlePaint)
        drawCornerHandle(canvas, cr, cb, 1, 1, handlePaint)
        drawCornerHandle(canvas, cl, cb, -1, 1, handlePaint)
    }

    private fun drawCornerHandle(canvas: Canvas, cx: Float, cy: Float, dx: Int, dy: Int, paint: Paint) {
        val thick = 12f
        val len = 50f
        
        canvas.drawRect(
            cx - (if (dx < 0) 0f else len),
            cy - thick / 2f,
            cx + (if (dx > 0) 0f else len),
            cy + thick / 2f,
            paint
        )
        canvas.drawRect(
            cx - thick / 2f,
            cy - (if (dy < 0) 0f else len),
            cx + thick / 2f,
            cy + (if (dy > 0) 0f else len),
            paint
        )
    }

    private fun getCropHandleAt(vx: Float, vy: Float): Int {
        val tl = imageToViewCoords(cropRect.left, cropRect.top)
        val br = imageToViewCoords(cropRect.right, cropRect.bottom)

        val cl = tl[0]
        val ct = tl[1]
        val cr = br[0]
        val cb = br[1]

        val radius = 80f

        if (distance(vx, vy, cl, ct) < radius) return 0
        if (distance(vx, vy, cr, ct) < radius) return 1
        if (distance(vx, vy, cr, cb) < radius) return 2
        if (distance(vx, vy, cl, cb) < radius) return 3

        if (vy in ct..cb) {
            if (Math.abs(vx - cl) < radius) return 7
            if (Math.abs(vx - cr) < radius) return 5
        }
        if (vx in cl..cr) {
            if (Math.abs(vy - ct) < radius) return 4
            if (Math.abs(vy - cb) < radius) return 6
        }

        if (vx in cl..cr && vy in ct..cb) {
            return 8
        }

        return -1
    }

    private fun moveCropHandle(handle: Int, ix: Float, iy: Float, vx: Float, vy: Float) {
        val bmp = bitmap ?: return
        val margin = 100f
        val limitW = bmp.width.toFloat()
        val limitH = bmp.height.toFloat()

        val cx = ix.coerceIn(0f, limitW)
        val cy = iy.coerceIn(0f, limitH)

        when (handle) {
            0 -> {
                cropRect.left = cx.coerceAtMost(cropRect.right - margin)
                cropRect.top = cy.coerceAtMost(cropRect.bottom - margin)
            }
            1 -> {
                cropRect.right = cx.coerceAtLeast(cropRect.left + margin)
                cropRect.top = cy.coerceAtMost(cropRect.bottom - margin)
            }
            2 -> {
                cropRect.right = cx.coerceAtLeast(cropRect.left + margin)
                cropRect.bottom = cy.coerceAtLeast(cropRect.top + margin)
            }
            3 -> {
                cropRect.left = cx.coerceAtMost(cropRect.right - margin)
                cropRect.bottom = cy.coerceAtLeast(cropRect.top + margin)
            }
            4 -> {
                cropRect.top = cy.coerceAtMost(cropRect.bottom - margin)
            }
            5 -> {
                cropRect.right = cx.coerceAtLeast(cropRect.left + margin)
            }
            6 -> {
                cropRect.bottom = cy.coerceAtLeast(cropRect.top + margin)
            }
            7 -> {
                cropRect.left = cx.coerceAtMost(cropRect.right - margin)
            }
            8 -> {
                val lastImg = viewToImageCoords(lastTouchX, lastTouchY)
                val currentImg = viewToImageCoords(vx, vy)
                val dxImg = currentImg[0] - lastImg[0]
                val dyImg = currentImg[1] - lastImg[1]

                val rectW = cropRect.width()
                val rectH = cropRect.height()

                cropRect.left = (cropRect.left + dxImg).coerceIn(0f, limitW - rectW)
                cropRect.top = (cropRect.top + dyImg).coerceIn(0f, limitH - rectH)
                cropRect.right = cropRect.left + rectW
                cropRect.bottom = cropRect.top + rectH
            }
        }

        cropRatio?.let {
            adjustCropRectToRatio(fixedHandle = handle)
        }
    }

    private fun adjustCropRectToRatio(fixedHandle: Int = -1) {
        val ratio = cropRatio ?: return
        val bmp = bitmap ?: return

        var w = cropRect.width()
        var h = cropRect.height()

        if (w / h > ratio) {
            w = h * ratio
        } else {
            h = w / ratio
        }

        val cx = cropRect.centerX()
        val cy = cropRect.centerY()

        when (fixedHandle) {
            4, 0, 1 -> {
                cropRect.top = cropRect.bottom - h
                cropRect.left = cx - w / 2f
                cropRect.right = cx + w / 2f
            }
            6, 2, 3 -> {
                cropRect.bottom = cropRect.top + h
                cropRect.left = cx - w / 2f
                cropRect.right = cx + w / 2f
            }
            else -> {
                cropRect.left = cx - w / 2f
                cropRect.top = cy - h / 2f
                cropRect.right = cx + w / 2f
                cropRect.bottom = cy + h / 2f
            }
        }

        if (cropRect.left < 0) {
            val shift = -cropRect.left
            cropRect.left = 0f
            cropRect.right += shift
        }
        if (cropRect.right > bmp.width) {
            val shift = cropRect.right - bmp.width
            cropRect.right = bmp.width.toFloat()
            cropRect.left -= shift
        }
        if (cropRect.top < 0) {
            val shift = -cropRect.top
            cropRect.top = 0f
            cropRect.bottom += shift
        }
        if (cropRect.bottom > bmp.height) {
            val shift = cropRect.bottom - bmp.height
            cropRect.bottom = bmp.height.toFloat()
            cropRect.top -= shift
        }
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    fun getFinalBitmap(): Bitmap? {
        val bmp = bitmap ?: return null
        
        val cw = cropRect.width().toInt()
        val ch = cropRect.height().toInt()
        
        if (cw <= 0 || ch <= 0) return null
        
        val finalBmp = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(finalBmp)

        canvas.drawBitmap(bmp, -cropRect.left, -cropRect.top, null)

        canvas.save()
        canvas.translate(-cropRect.left, -cropRect.top)
        for (element in elements) {
            element.draw(canvas)
        }
        canvas.restore()

        return finalBmp
    }
}
