package com.brainrotdecantation.adcovermuteskip

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.util.Log
import android.util.TypedValue
import android.view.SurfaceHolder
import android.view.SurfaceView

class OverlaySurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    private var drawingThread: DrawingThread? = null
    private val coverRects = mutableListOf<Rect?>(null, null, null, null, null)
    private val uncoverRects = mutableListOf<Rect?>(null)
    private var somethingChanged = false
    private var opacity: Int = 245

    init {
        Log.d("DetectorService_OverlaySurfaceView", "INIT entry for ${this.hashCode()}")
        holder.addCallback(this)
    }

    fun updateCoverRect(index: Int, newRect: Rect?) {
        if( index < 0 || index >= coverRects.size) return
        if(newRect == null && coverRects[index] == null) return // No change
        if(newRect == null) {
            coverRects[index] = null
            Log.w("DetectorService_OverlaySurfaceView", "CHANGE Cover $index Disabled")
            somethingChanged = true
        } else {
            if(coverRects[index] == null) {
                coverRects[index] = newRect
                Log.w("DetectorService_OverlaySurfaceView", "CHANGE Cover $index Enabled")
                somethingChanged = true
            } else {
                if(newRect.top == coverRects[index]?.top && newRect.left == coverRects[index]?.left && newRect.right == coverRects[index]?.right && newRect.bottom == coverRects[index]?.bottom) return // No change
                coverRects[index] = newRect
                Log.w("DetectorService_OverlaySurfaceView", "CHANGE Cover $index MOVED RECT")
                somethingChanged = true
            }
        }
    }

    fun updateUncoverRect(index: Int, newRect: Rect?) {
        if( index < 0 || index >= uncoverRects.size) return
        if(newRect == null && uncoverRects[index] == null) return // No change
        if(newRect == null) {
            uncoverRects[index] = null
            Log.w("DetectorService_OverlaySurfaceView", "CHANGE Uncover $index Disabled")
            somethingChanged = true
        } else {
            if(uncoverRects[index] == null) {
                uncoverRects[index] = newRect
                Log.w("DetectorService_OverlaySurfaceView", "CHANGE Uncover $index Enabled")
                somethingChanged = true
            } else {
                if(newRect.top == uncoverRects[index]?.top && newRect.left == uncoverRects[index]?.left && newRect.right == uncoverRects[index]?.right && newRect.bottom == uncoverRects[index]?.bottom) return // No change
                uncoverRects[index] = newRect
                Log.w("DetectorService_OverlaySurfaceView", "CHANGE Uncover $index MOVED RECT")
                somethingChanged = true
            }
        }
    }

    /*fun clearAllCoversAndUncovers() {
        for(i in 0 until coverRects.size) {
            if(coverRects[i] != null) somethingChanged = true
            coverRects[i] = null
        }
        for(i in 0 until uncoverRects.size) {
            if(uncoverRects[i] != null) somethingChanged = true
            uncoverRects[i] = null
        }
    }*/

    fun redraw() {
        if(!somethingChanged) {
            Log.d("DetectorService_OverlaySurfaceView", "No change, no redraw")
            return
        }
        Log.d("DetectorService_OverlaySurfaceView", "Redraw requested")
        drawingThread?.requestDraw()
        somethingChanged = false
    }

    fun stopDrawingThread() {
        drawingThread?.requestStop()
        drawingThread = null
    }

    /*
    Summary of the weird initialisation process:
    It seems setZOrderOnTop(true) or holder.setFormat(PixelFormat.TRANSLUCENT) triggers a destruction/recreation of the surface
    It's important to make sure to only create the DrawingThread once
    +++SURFACE CREATED entry with creationId=0
    SURFACE DESTROYED, Thread stop request sent
    +++SURFACE CREATED entry with creationId=1
    ---SURFACE CREATED exit with creationId=1
    +++++SURFACE CHANGED entry
    -----SURFACE CHANGED exit
    Creation of the DrawingThread
    ---SURFACE CREATED exit with creationId=0
    +++++SURFACE CHANGED entry
    -----SURFACE CHANGED exit
     */
    private var creationCounter = 0
    private var completedCreationCounter = 0
    fun getCreationCounter(): Int { return creationCounter }
    fun getCompletedCreationCounter(): Int { return completedCreationCounter }
    override fun surfaceCreated(holder: SurfaceHolder) {
        val creationId = creationCounter
        creationCounter += 1
        Log.d("DetectorService_OverlaySurfaceView", "+++SURFACE CREATED entry with creationId=$creationId")

        setZOrderOnTop(true) // Place surface on top of regular window content
        holder.setFormat(PixelFormat.TRANSLUCENT) // Necessary inside, doing it within the layoutParams doesn't work

        if(creationId == 0) {
            Log.d("DetectorService_OverlaySurfaceView", "Creation of the DrawingThread")
            drawingThread = DrawingThread(holder, ::drawContent).apply { start() }
        }

        Log.d("DetectorService_OverlaySurfaceView", "---SURFACE CREATED exit with creationId=$creationId")
        completedCreationCounter += 1
    }

    // Triggered automatically during a SurfaceView change of layoutParams
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d("DetectorService_OverlaySurfaceView", "+++++SURFACE CHANGED entry")
        drawingThread?.updateSurfaceHolder(holder) // Not sure if it's necessary
        Log.d("DetectorService_OverlaySurfaceView", "-----SURFACE CHANGED exit")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopDrawingThread()
        Log.d("DetectorService_OverlaySurfaceView", "SURFACE DESTROYED, Thread stop request sent")
    }

    fun updateOpacity(newOpacity: Int) {
        if(opacity == newOpacity.coerceIn(0, 255)) return
        opacity = newOpacity.coerceIn(0, 255)
        transparentPain = Paint().apply {
            color = Color.argb(opacity, 35, 45, 65) // Indigo Blue
            style = Paint.Style.FILL
        }
        //if(!noDraw) drawingThread?.requestDraw()
    }

    // This function is called by the drawing thread, before redrawing, you just need to modify targetRect in OverlaySurfaceView.updateTargetRect(newRect)
    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        // No need for color or style, as CLEAR mode operates on the alpha channel
        // and effectively makes pixels transparent.
    }
    var transparentPain = Paint().apply {
        color = Color.argb(opacity, 35, 45, 65) // Indigo Blue
        style = Paint.Style.FILL
    }
    val borderPaint = Paint().apply {
        color = Color.parseColor("#506795") // Light blue
        style = Paint.Style.STROKE
        strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, context.resources.displayMetrics)
    }
    private fun drawContent(canvas: Canvas) {
        canvas.drawColor(0, PorterDuff.Mode.CLEAR)
        coverRects.forEach { rect ->
            rect?.let {
                canvas.drawRect(it, transparentPain)
                canvas.drawRect(it, borderPaint)
            }
        }
        uncoverRects.forEach { rect ->
            rect?.let {
                canvas.drawRect(it, clearPaint)
            }
        }
    }
}