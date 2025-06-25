package com.brainrotdecantation.adcovermuteskip

import android.graphics.Canvas
import android.util.Log
import android.view.SurfaceHolder

class DrawingThread(private var surfaceHolder: SurfaceHolder, private val drawCallback: (Canvas) -> Unit) : Thread() {
    private var running = false
    private val lock = Any() // it's both Any() in Kotlin and java.lang.Object() in Java, the explicit casting seems required as Any doesn't have notify/wait

    fun updateSurfaceHolder(newSurfaceHolder: SurfaceHolder) {
        synchronized(lock) {
            surfaceHolder = newSurfaceHolder
        }
    }
    fun requestStop() {
        Log.d("DetectorService_DrawerThread", "REQUEST_STOP")
        running = false
        synchronized(lock) {
            (lock as Object).notify() // Important to wake up the thread at the lock.wait() and cleanly exit the run() function
        }
    }

    fun requestDraw() {
        Log.i("DetectorService_DrawerThread", "REQUEST_DRAW")
        synchronized(lock) {
            (lock as Object).notify() // Wake up the thread to draw
        }
    }

    override fun run() {
        Log.d("DetectorService_DrawerThread", "RUN STARTED")
        running = true

        while (running) {
            var canvas: Canvas? = null
            try {
                canvas = surfaceHolder.lockCanvas()
                synchronized(surfaceHolder) {
                    if (canvas != null) {
                        drawCallback(canvas)
                    }
                }
            } catch (e: Exception) {
                Log.e("DrawingThread", "Error while attempting to draw on the canvas: ${e.message}")
            } finally {
                if (canvas != null) {
                    try {
                        surfaceHolder.unlockCanvasAndPost(canvas)
                    } catch (e: IllegalArgumentException) {
                        Log.e("DrawingThread", "Error posting the canvas: ${e.message}")
                        // This often happens if the surface has already been destroyed
                        // or if the canvas was already unlocked.
                    }
                }
            }

            // Sleep or wait for a new draw request
            synchronized(lock) {
                try {
                    (lock as Object).wait() // Wait for notification to draw again
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
        Log.d("DetectorService_DrawerThread", "RUN EXIT cleanly")
    }
}