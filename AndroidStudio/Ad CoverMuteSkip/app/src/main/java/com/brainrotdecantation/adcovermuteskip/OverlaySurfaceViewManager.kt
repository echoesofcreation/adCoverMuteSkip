package com.brainrotdecantation.adcovermuteskip

import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.graphics.Rect
import android.util.Log
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import com.brainrotdecantation.adcovermuteskip.DetectorService.CoverOrUncoverContainer
import com.brainrotdecantation.adcovermuteskip.DetectorService.CoverRectIndex
import com.brainrotdecantation.adcovermuteskip.DetectorService.UncoverRectIndex
import com.brainrotdecantation.adcovermuteskip.DetectorService.SurfaceContentType

/*
 * The Surface takes forever to initialise.
 * If you quickly remove it then re-add it to the windowManager, it will cause a nullpointexception
 * when the OverlaySurfaceView tries to access its parent node.
 * The goal of the OverlaySurfaceViewManager is to only allow the removal once the initialisation is complete
 *
 * Also, it'll prevent fluff events from com.sec.android.app.launcher from being considered as exiting the app
 * and triggering the removal of the OverlaySurfaceView. Configuration change (screen rotation) triggers 2x fluff
 * app.launcher events. We'll require 3x app.launcher events to accept the removal of the OverlaySurfaceView
 *
 * Overall, the OverlaySurfaceView can only be removed once the initialisation is complete
 * and after 3 calls of app.launcher (one call from other packages is enough)
 * Events from packages that do want the ad blocking active will reset the app.launcher counter
 *
 * In the future, I may try a smarter approach where I truly look at the app.launcher events to see
 * if I can differentiate app switching events to the desktop vs the fluff events
 *
 * The initialisation process of the OverlaySurfaceView is complete when
 * creationCounter > 0 && creationCounter == completedCreationCounter
 */
class OverlaySurfaceViewManager(private val context: Context): SurfaceHolder.Callback, SharedPreferences.OnSharedPreferenceChangeListener {
    private var overlaySurfaceView: OverlaySurfaceView? = null
    private var dataOpacity: Int = DataStorage.loadOpacity(context)
    private var initialisationComplete = false
    private var appLauncherCounter = 0

    init {
        DataStorage.getSharedPreferences(context).registerOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == DataStorage.KEY_OPACITY) {
            val newOpacity = DataStorage.loadOpacity(context)
            if (newOpacity != dataOpacity) { // Only update if it's actually different
                dataOpacity = newOpacity
                overlaySurfaceView?.updateOpacity(dataOpacity)
                safeRedraw()
            }
        }
    }

    fun createOrUpdateCover(targetNode: AccessibilityNodeInfo?, coverRectIndexContainer: CoverRectIndex, boundAdjustment: Rect = Rect()) {
        targetNode?.isVisibleToUser.let { if(it == false) {
            Log.w("DetectorService_OverlaySurfaceViewManager", "INVISIBLE NODE, removing cover")
            removeCover(coverRectIndexContainer)
            return
        } }
        createOrUpdateOverlaySurface(targetNode, SurfaceContentType.COVER, coverRectIndexContainer.index, boundAdjustment)
    }
    fun createOrUpdateUncover(targetNode: AccessibilityNodeInfo?, uncoverRectIndexContainer: UncoverRectIndex, boundAdjustment: Rect = Rect()) {
        targetNode?.isVisibleToUser.let { if(it == false) {
            Log.w("DetectorService_OverlaySurfaceViewManager", "INVISIBLE NODE, removing uncover")
            removeUncover(uncoverRectIndexContainer)
            return
        } }
        createOrUpdateOverlaySurface(targetNode, SurfaceContentType.UNCOVER, uncoverRectIndexContainer.index, boundAdjustment)
    }

    private fun createOrUpdateOverlaySurface(targetNode: AccessibilityNodeInfo?, contentType: SurfaceContentType, rectIndex: Int, boundAdjustment: Rect = Rect()) {

        appLauncherCounter = 0 // Reset the counter every time we actually want the overlay to be active

        /*
         * Get the overlay bounds
         */
        var bounds: Rect? = null
        if (targetNode != null) {
            bounds = Rect()
            targetNode.getBoundsInScreen(bounds)

            if (bounds.isEmpty) {
                Log.w("DetectorService_OverlaySurfaceViewManager", "Target node bounds are empty, removing overlay.")
                bounds = null
            } else {
                bounds.top += boundAdjustment.top
                bounds.bottom += boundAdjustment.bottom
            }
        }

        /*
         * Update the SurfaceView if it already exists
         */
        if (overlaySurfaceView != null) {
            when(contentType) {
                SurfaceContentType.COVER -> {
                    Log.i("DetectorService_OverlaySurfaceViewManager", "Updating cover $rectIndex")
                    overlaySurfaceView?.updateCoverRect(rectIndex, bounds)
                }
                SurfaceContentType.UNCOVER -> {
                    Log.i("DetectorService_OverlaySurfaceViewManager", "Updating uncover $rectIndex")
                    overlaySurfaceView?.updateUncoverRect(rectIndex, bounds)
                }
            }
            return
        }


        /*
         * Create the SurfaceView
         */
        // Get screen dimensions to make the SurfaceView full screen
        val windowManager = context.getSystemService(WINDOW_SERVICE) as WindowManager
        val fullScreenRect = Tools.getFullScreenRect(windowManager, context.resources)
        var layoutParams = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY // Or TYPE_APPLICATION_OVERLAY for general overlays
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or // This uses screen space coordinates instead of the default app space
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            /*
            This flag allows the view to draw outside of the normal screen bounds.
            It's typically used for special cases where you need to draw outside the visible screen area, which is not usually necessary for standard overlays.
             */
            gravity = Gravity.TOP or Gravity.START
            width = fullScreenRect.width()
            height = fullScreenRect.height()
            x = 0
            y = 0
        }

        overlaySurfaceView = OverlaySurfaceView(context)
        overlaySurfaceView?.updateOpacity(dataOpacity) // I didn't add this in the constructor before there was an issue with inheritance, it made the syntax complex

        try {
            val windowManager = context.getSystemService(WINDOW_SERVICE) as WindowManager
            windowManager.addView(overlaySurfaceView, layoutParams)
            Log.d("DetectorService_OverlaySurfaceViewManager", "SurfaceView CREATED and ADDED to WindowManager.")
            when(contentType) {
                SurfaceContentType.COVER -> {
                    overlaySurfaceView?.updateCoverRect(rectIndex, bounds)
                }
                SurfaceContentType.UNCOVER -> {
                    overlaySurfaceView?.updateUncoverRect(rectIndex, bounds)
                }
            }
            safeRedraw() // It most likely won't draw because it's not finished initialising
        } catch (e: Exception) {
            Log.e("DetectorService_OverlaySurfaceViewManager", "Error adding overlay SurfaceView: ${e.message}", e)
            overlaySurfaceView?.stopDrawingThread()
            overlaySurfaceView = null // Clear reference on failure
        }
    }

    fun updateCover(coverRectIndexContainer: CoverRectIndex, newRect: Rect?) {
        overlaySurfaceView?.updateCoverRect(coverRectIndexContainer.index, newRect)
    }

    fun updateUncover(uncoverRectIndexContainer: UncoverRectIndex, newRect: Rect?) {
        overlaySurfaceView?.updateUncoverRect(uncoverRectIndexContainer.index, newRect)
    }

    fun removeCover(coverRectIndexContainer: CoverRectIndex) {
        overlaySurfaceView?.updateCoverRect(coverRectIndexContainer.index, null)
    }

    fun removeUncover(uncoverRectIndexContainer: UncoverRectIndex) {
        overlaySurfaceView?.updateUncoverRect(uncoverRectIndexContainer.index, null)
    }

    fun safeRedraw() {
        if(initialisationComplete) {
            Log.d("DetectorService_OverlaySurfaceViewManager", "Initialisation ok, request possible REDRAW overlay surface.")
            overlaySurfaceView?.redraw()
        } else {
            overlaySurfaceView?.let {
                initialisationComplete = it.getCreationCounter() > 0 && it.getCreationCounter() == it.getCompletedCreationCounter()
                if(initialisationComplete) {
                    overlaySurfaceView?.redraw()
                }
            }
        }
    }

    /*fun clearAndRedrawOverlaySurface() {
        overlaySurfaceView?.clearAllCoversAndUncovers()
        safeRedraw()
    }*/

    fun removeOverlaySurfaceView(appLauncherEvent: Boolean = false) {
        overlaySurfaceView?.let {
            initialisationComplete = it.getCreationCounter() > 0 && it.getCreationCounter() == it.getCompletedCreationCounter()
        } ?: return
        if(!initialisationComplete) return // We're not allowed to remove partially initialised surfaces because it can cause a crash
        if(appLauncherEvent) {
            appLauncherCounter++
            if(appLauncherCounter < 3) {
                Log.d("DetectorService_OverlaySurfaceViewManager", "App launcher removal attempt. Counter: $appLauncherCounter / 3")
                return
            }
        }

        overlaySurfaceView?.let {
            it.stopDrawingThread() // Crucial: First stop the drawing thread to ensure it cleans up gracefully.
            try {
                val windowManager = context.getSystemService(WINDOW_SERVICE) as WindowManager
                windowManager.removeView(it)
                Log.d("DetectorService_OverlaySurfaceViewManager", "Overlay SurfaceView REMOVED from WindowManager.")
            } catch (e: Exception) {
                Log.e("DetectorService_OverlaySurfaceViewManager", "Error removing overlay SurfaceView: ${e.message}", e)
            } finally {
                overlaySurfaceView = null
                appLauncherCounter = 0
                initialisationComplete = false
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        safeRedraw() // This will often be the first successful post-initialisation drawing call
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
    }

}