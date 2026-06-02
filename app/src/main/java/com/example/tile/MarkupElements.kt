package com.aarash.idin

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode

interface MarkupElement {
    fun draw(canvas: Canvas)
}

// Brush coretan bebas & Eraser
class PathElement(
    val path: Path,
    val color: Int,
    val strokeWidth: Float,
    val isEraser: Boolean = false
) : MarkupElement {
    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = this@PathElement.color
        this.strokeWidth = this@PathElement.strokeWidth
        if (isEraser) {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(path, paint)
    }
}

// Shapes: Rectangle, Rounded Rectangle, Circle, Line, Arrow
class ShapeElement(
    val type: Type,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val color: Int,
    val strokeWidth: Float
) : MarkupElement {
    enum class Type {
        RECTANGLE,
        ROUNDED_RECTANGLE,
        CIRCLE,
        LINE,
        ARROW
    }

    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = this@ShapeElement.color
        this.strokeWidth = this@ShapeElement.strokeWidth
    }

    override fun draw(canvas: Canvas) {
        when (type) {
            Type.RECTANGLE -> {
                canvas.drawRect(
                    minOf(startX, endX),
                    minOf(startY, endY),
                    maxOf(startX, endX),
                    maxOf(startY, endY),
                    paint
                )
            }
            Type.ROUNDED_RECTANGLE -> {
                val rect = RectF(
                    minOf(startX, endX),
                    minOf(startY, endY),
                    maxOf(startX, endX),
                    maxOf(startY, endY)
                )
                val radius = 24f
                canvas.drawRoundRect(rect, radius, radius, paint)
            }
            Type.CIRCLE -> {
                val rect = RectF(
                    minOf(startX, endX),
                    minOf(startY, endY),
                    maxOf(startX, endX),
                    maxOf(startY, endY)
                )
                canvas.drawOval(rect, paint)
            }
            Type.LINE -> {
                canvas.drawLine(startX, startY, endX, endY, paint)
            }
            Type.ARROW -> {
                drawArrow(canvas, startX, startY, endX, endY, paint)
            }
        }
    }

    private fun drawArrow(canvas: Canvas, sx: Float, sy: Float, ex: Float, ey: Float, paint: Paint) {
        canvas.drawLine(sx, sy, ex, ey, paint)
        
        val dx = ex - sx
        val dy = ey - sy
        val angle = Math.atan2(dy.toDouble(), dx.toDouble())
        
        val arrowLength = strokeWidth * 4f + 15f
        val arrowAngle = Math.toRadians(30.0)
        
        val x1 = ex - arrowLength * Math.cos(angle - arrowAngle)
        val y1 = ey - arrowLength * Math.sin(angle - arrowAngle)
        val x2 = ex - arrowLength * Math.cos(angle + arrowAngle)
        val y2 = ey - arrowLength * Math.sin(angle + arrowAngle)
        
        val path = Path().apply {
            moveTo(ex, ey)
            lineTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
            close()
        }
        
        val fillPaint = Paint(paint).apply {
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, fillPaint)
    }
}


