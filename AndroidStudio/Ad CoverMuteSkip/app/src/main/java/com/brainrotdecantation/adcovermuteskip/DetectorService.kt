package com.brainrotdecantation.adcovermuteskip

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.Gravity;
import android.view.KeyEvent
import android.view.View;
import android.view.WindowInsets
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout
import org.json.JSONArray
import org.json.JSONObject
import kotlin.reflect.KMutableProperty0
import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.Configuration
import android.os.Handler
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.Int
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowMetrics

class DetectorService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {
    // Data from the DataStorage SharedPreferences
    private var dataMonitorVolumeClick = false

    enum class SurfaceContentType {
        COVER,    // Represents content that covers (e.g., semi-transparent overlay)
        UNCOVER   // Represents content that uncovers (e.g., holes in the overlay)
    }
    interface CoverOrUncoverContainer {
        val index: Int
    }
    enum class CoverRectIndex(val index: Int) {
        MINIMISED_AD(0),
        FEED_LIST_AD(1),
        RECOMMENDATION_LIST_AD(2),
        WATCH_PLAYER(3),
        WATCH_PLAYER_FLOATING(4),
    }
    enum class UncoverRectIndex(val index: Int) {
        WATCH_PLAYER_FLOATING(0)
    }
    private var overlaySurfaceViewManager: OverlaySurfaceViewManager? = null

    private val clickTimestamps = mutableMapOf(
        "skip" to 0L,
        "removeExpandedAd" to 0L)
    private val CLICK_DEBOUNCE_INTERVAL_MS = 1000L // 1 second debounce interval

    private var previousMusicVolume: Int = -1



    private fun muteMusicChannel() {
        if (previousMusicVolume == -1) {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if(currentVolume == 0) {
                Log.d("AudioControl", "stream_music ALREADY MUTED")
                return
            }
            previousMusicVolume = currentVolume
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                0, // Volume index 0
                AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE // Suppress UI volume changes/sounds
            )
            Log.d("AudioControl", "stream_music MUTED. Previous volume: $previousMusicVolume")
        }
    }
    private fun unmuteMusicChannel() {
        if (previousMusicVolume != -1) {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                previousMusicVolume,
                AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE // Suppress UI volume changes/sounds
            )
            previousMusicVolume = -1
            Log.d("AudioControl", "stream_music UNMUTED. Previous volume: $previousMusicVolume")
        }
    }



    /*
     * Secondary receivers
     */
    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if(!dataMonitorVolumeClick) return super.onKeyEvent(event)

        Log.d("DetectorService", "ONKEY EVENT CALLED")
        if (event?.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    Log.d("DetectorService", "VOLUME UP button pressed.")
                    try {
                        val serializedTree = Tools.serializeDOMTree(rootInActiveWindow, 0)
                        val jsonSummary = JSONObject()
                        jsonSummary.put("creationDate", SimpleDateFormat("yyyy-MM-dd__HH-mm-ss", Locale.getDefault()).format(Date()))
                        jsonSummary.put("tree", serializedTree)
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("App Layout Tree", jsonSummary.toString(2))
                        clipboard.setPrimaryClip(clip)
                    } catch (e: Exception) {
                        Log.e("DetectorService_TREE", "Error serializing node tree: ${e.message}", e)
                    }
                }
            }
        }
        return super.onKeyEvent(event) // Let other key events be handled normally
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // Remove the overlay when the screen turns off (device goes to sleep).
                    Log.d("DetectorService", "Screen OFF, removing overlay surface.")
                    overlaySurfaceViewManager?.removeOverlaySurfaceView()
                }
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == DataStorage.KEY_MONITOR_VOLUME_CLICK) {
            val newMonitorVolumeClick = DataStorage.loadMonitorVolumeClick(this)
            if (newMonitorVolumeClick != dataMonitorVolumeClick) { // Only update if it's actually different
                dataMonitorVolumeClick = newMonitorVolumeClick
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) { // Screen orientation change
        super.onConfigurationChanged(newConfig)
        Log.d("DetectorService", "Configuration changed. New orientation: ${newConfig.orientation}")
        overlaySurfaceViewManager?.removeOverlaySurfaceView() // Let's be safe, I used to have some memory leak issues when I tried to just resize everything
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        val filter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF) } // To remove the overlays when the screen turns off
        registerReceiver(screenStateReceiver, filter)

        val info = serviceInfo
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS // Normally it's in the XML config file, but it seems I needed to write it manually for it to work
        serviceInfo = info

        DataStorage.getSharedPreferences(this).registerOnSharedPreferenceChangeListener(this)
        dataMonitorVolumeClick = DataStorage.loadMonitorVolumeClick(this)

        overlaySurfaceViewManager = OverlaySurfaceViewManager(this)
    }

    // The main receiver, an AccessibilityEvent is received every time the UI changes
    // The first step is to ignore the useless events before calling handleValidEvent()
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) {
            Log.d("DetectorService", "Received null accessibility event.")
            return
        }

        val currentPackageName = event.packageName?.toString()
        Log.v("DetectorService", "Event received: Type=${event.eventType}, Package=$currentPackageName")

        /*
         * Ignore events from fluff packages (like system UI, but we may also need to include the keyboard opening)
         */
        val fluffPackagesToIgnore = arrayOf("com.android.systemui")
        if (currentPackageName != null && fluffPackagesToIgnore.contains(currentPackageName)) {
            Log.d("DetectorService", "Ignoring event from fluff package: $currentPackageName")
            return
        }

        /*
         * Ignore events from my own service if it's the overlay updating fluff, but remove all if it's the MainActivity (aka app switching)
         */
        val myAppPackageName = this.packageName
        if (currentPackageName == myAppPackageName) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val eventClassName = event.className?.toString()
                Log.d("DetectorService", "---Event class name: $eventClassName")
                if(eventClassName != null && eventClassName.startsWith(myAppPackageName) && eventClassName.endsWith("MainActivity")) {
                    Log.d("DetectorService", "My app's main UI is active ($eventClassName). Removing overlay.")
                    overlaySurfaceViewManager?.removeOverlaySurfaceView()
                } else {
                    Log.d("DetectorService", "Ignore event from my own service overlay")
                    return
                }
            } else {
                Log.d("DetectorService", "Ignore event from my own service overlay")
                return
            }
        }

        if (currentPackageName != RulePaths.YoutubePaths.PACKAGE_NAME) {
            if(currentPackageName == "com.sec.android.app.launcher") {
                overlaySurfaceViewManager?.removeOverlaySurfaceView(appLauncherEvent = true) // The removal will only happen after 3 app.launcher events
            } else {
                overlaySurfaceViewManager?.removeOverlaySurfaceView()
            }
            return
        }

        // We are interested in changes to the window content or window state.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val rootNode = rootInActiveWindow
            if (rootNode == null) {
                Log.w("DetectorService", "rootInActiveWindow is null. Cannot find node.")
                overlaySurfaceViewManager?.removeOverlaySurfaceView()
                return
            }

            //if (rootNode.packageName?.toString() == BluetoothMidiRecorderPaths.PACKAGE_NAME) {
            if (rootNode.packageName?.toString() == RulePaths.YoutubePaths.PACKAGE_NAME) {
                /*if(captureNextEventDetails) {
                    try {
                        val serializedTree = Tools.serializeDOMTree(rootNode, 0)
                        val jsonSummary = JSONObject()
                        jsonSummary.put("creationDate", SimpleDateFormat("yyyy-MM-dd__HH-mm-ss", Locale.getDefault()).format(Date()))
                        jsonSummary.put("tree", serializedTree)
                        lastJsonSummary = jsonSummary.toString(2) // 2 means spacing
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("App Layout Tree", lastJsonSummary)
                        clipboard.setPrimaryClip(clip)
                    } catch (e: Exception) {
                        Log.e("DetectorService_TREE", "Error serializing node tree: ${e.message}", e)
                    }
                    captureNextEventDetails = false
                }*/

                Log.d("DetectorService", "Processing event for target app: ${rootNode.packageName}")
                handleValidEvent()
            } else {
                // If the root node package changed unexpectedly, remove the overlay.
                Log.d("DetectorService", "Root node package mismatch (${rootNode.packageName}). Removing overlay.")
                overlaySurfaceViewManager?.removeOverlaySurfaceView()
            }
        }
    }

    // Currently, only target node.contentDescription.startsWith(startStr)
    // Only add the overlay to the elemRoot, not at the current depth element
    private fun searchChildren(listRoot: AccessibilityNodeInfo, depths: Array<Int>, startStr: String, coverRectIndexContainer: CoverRectIndex, boundAdjustment: Rect = Rect()): Boolean {
        val maxDepth = depths.max()
        try { // RecyclerViews can magically made nodes disappear at any moment, let's be safe
            for(i in 0 until listRoot.childCount) {
                val elemRoot = listRoot.getChild(i)

                RulePathNodeTools.getNode(elemRoot, RulePaths.YoutubePaths.MINIMISED_AD)?.let { // The minimised ad is a child of the recommendation list, with process it outside of it
                } ?: run {
                    var currentNode = elemRoot
                    for (currentDepth in 0 until maxDepth+1) {
                        if(currentDepth in depths) {
                            val description = currentNode?.contentDescription?.toString()
                            if(description != null && description.startsWith(startStr)) {
                                Log.i("DetectorService_IdentifyNodes", "Found Sponsor child node, covering it")
                                overlaySurfaceViewManager?.createOrUpdateCover(elemRoot, coverRectIndexContainer, boundAdjustment)
                                return true
                            }
                        }
                        if(currentNode != null && currentNode.childCount > 0) {
                            currentNode = currentNode.getChild(0)
                        } else {
                            break
                        }
                    }
                }
            }
            Log.i("DetectorService_IdentifyNodes", "no child ad found, removing cover")
            overlaySurfaceViewManager?.removeCover(coverRectIndexContainer)
        } catch (e: Exception) {
            Log.e("DetectorService_IdentifyNodes", "Error in searchChildren: ${e.message}", e)
        }
        return false
    }

    private fun handleValidEvent() {
        val defaultRoot = rootInActiveWindow
        /*
         * Clicks
         */
        RulePathNodeTools.getNode(defaultRoot, RulePaths.YoutubePaths.EXPANDED_AD_PORTRAIT_BUTTON)?.let {
            clickOnNode(it, "removeExpandedAd")
        } ?: run {
            RulePathNodeTools.getNode(defaultRoot, RulePaths.YoutubePaths.EXPANDED_AD_PORTRAIT_BUTTON_PAUSE)?.let {
                clickOnNode(it, "removeExpandedAd")
            } ?: run {
                RulePathNodeTools.getNode(defaultRoot, RulePaths.YoutubePaths.EXPANDED_AD_LANDSCAPE_BUTTON_PAUSE)?.let {
                    clickOnNode(it, "removeExpandedAd")
                }
            }
        }
        RulePathNodeTools.getNode(defaultRoot, RulePaths.YoutubePaths.SKIP_BUTTON_FLOATING)?.let {
            clickOnNode(it, "skip")
            Log.i("DetectorService_IdentifyNodes", "Found SKIP_BUTTON_FLOATING")
        } ?: run {
            RulePathNodeTools.getNode(defaultRoot, RulePaths.YoutubePaths.SKIP_BUTTON)?.let {
                clickOnNode(it, "skip")
                Log.i("DetectorService_IdentifyNodes", "Found SKIP_BUTTON")
            }
        }

        /*
         * Simple overlays
         */
        RulePathNodeTools.getNode(defaultRoot, RulePaths.YoutubePaths.MINIMISED_AD)?.let {
            val boundAdjustment = Rect()
            boundAdjustment.top = Tools.dpToPx(3.0f, resources)
            Log.i("DetectorService_IdentifyNodes", "Found MINIMISED_AD")
            overlaySurfaceViewManager?.createOrUpdateCover(it, CoverRectIndex.MINIMISED_AD, boundAdjustment)
        } ?: run {
            Log.i("DetectorService_IdentifyNodes", "removeCover MINIMISED_AD")
            overlaySurfaceViewManager?.removeCover(CoverRectIndex.MINIMISED_AD)
        }

        /*
         * Overlays for the floating / normal video players
         */
        var floatingAd = false
        var normalAd = false
        RulePathNodeTools.getNode(defaultRoot, RulePaths.YoutubePaths.AD_PRESENCE_FLOATING)?.let {
            floatingAd = true
            muteMusicChannel()
            RulePathNodeTools.getNode(defaultRoot, RulePaths.YoutubePaths.WATCH_PLAYER_FLOATING)?.let {
                val boundAdjustment = Rect()
                boundAdjustment.bottom = Tools.dpToPx(-4.0f, resources)
                Log.i("DetectorService_IdentifyNodes", "Found AD_PRESENCE_FLOATING, covering WATCH_PLAYER_FLOATING")
                overlaySurfaceViewManager?.createOrUpdateCover(it, CoverRectIndex.WATCH_PLAYER_FLOATING, boundAdjustment)
                overlaySurfaceViewManager?.removeUncover(UncoverRectIndex.WATCH_PLAYER_FLOATING)
            }
        } ?: run {
            RulePathNodeTools.getNode(defaultRoot, RulePaths.YoutubePaths.WATCH_PLAYER_FLOATING)?.let {
                Log.i("DetectorService_IdentifyNodes", "Found WATCH_PLAYER_FLOATING no ad, uncovering the player")
                overlaySurfaceViewManager?.createOrUpdateUncover(it, UncoverRectIndex.WATCH_PLAYER_FLOATING)
                overlaySurfaceViewManager?.removeCover(CoverRectIndex.WATCH_PLAYER_FLOATING)
            } ?: run { // Neither floating ad nor floating player
                Log.i("DetectorService_IdentifyNodes", "No WATCH_PLAYER_FLOATING, remove cover+uncover")
                overlaySurfaceViewManager?.removeCover(CoverRectIndex.WATCH_PLAYER_FLOATING)
                overlaySurfaceViewManager?.removeUncover(UncoverRectIndex.WATCH_PLAYER_FLOATING)
            }
        }
        RulePathNodeTools.getNode(defaultRoot, RulePaths.YoutubePaths.AD_PRESENCE)?.let {
            normalAd = true
            muteMusicChannel()
            RulePathNodeTools.getNode(defaultRoot, RulePaths.YoutubePaths.WATCH_PLAYER)?.let {
                val boundAdjustment = Rect()
                boundAdjustment.bottom = Tools.dpToPx(-4.0f, resources)
                Log.i("DetectorService_IdentifyNodes", "Found AD_PRESENCE, covering WATCH_PLAYER")
                overlaySurfaceViewManager?.createOrUpdateCover(it, CoverRectIndex.WATCH_PLAYER, boundAdjustment)
            }
        } ?: run {
            Log.i("DetectorService_IdentifyNodes", "No ad in player space, remove cover WATCH_PLAYER")
            overlaySurfaceViewManager?.removeCover(CoverRectIndex.WATCH_PLAYER)
        }
        if(!floatingAd && !normalAd) {
            unmuteMusicChannel()
        }

        /*
         * Complex overlays
         */
        RulePathNodeTools.getNode(defaultRoot, RulePaths.YoutubePaths.RECOMMENDATION_LIST_ROOT)?.let {
            val boundAdjustment = Rect()
            boundAdjustment.top = Tools.dpToPx(3.0f, resources)
            Log.i("DetectorService_IdentifyNodes", "Search children RECOMMENDATION_LIST_ROOT")
            searchChildren(it, arrayOf<Int>(0, 1, 2, 3), "Sponsor", CoverRectIndex.RECOMMENDATION_LIST_AD, boundAdjustment)
        } ?: run {
            Log.i("DetectorService_IdentifyNodes", "removeCover RECOMMENDATION_LIST_AD")
            overlaySurfaceViewManager?.removeCover(CoverRectIndex.RECOMMENDATION_LIST_AD)
        }
        RulePathNodeTools.getNode(defaultRoot, RulePaths.YoutubePaths.FEED_LIST_ROOT)?.let {
            Log.i("DetectorService_IdentifyNodes", "Search children FEED_LIST_ROOT")
            searchChildren(it, arrayOf<Int>(0, 1, 2, 3), "Sponsor", CoverRectIndex.FEED_LIST_AD)
        } ?: run {
            Log.i("DetectorService_IdentifyNodes", "removeCover FEED_LIST_AD")
            overlaySurfaceViewManager?.removeCover(CoverRectIndex.FEED_LIST_AD)
        }

        /*
         * It's time to redraw if something changed
         */
        Log.i("DetectorService_IdentifyNodes", "End of search, request REDRAW")
        overlaySurfaceViewManager?.safeRedraw()
    }

    private fun clickOnNode(node: AccessibilityNodeInfo, timestampStr: String) {
        if(timestampStr !in clickTimestamps) {
            Log.d("DetectorService", "Invalid timestampStr: $timestampStr")
            return
        }
        if (node.isClickable) {
            // We check the anti-spam timers and update them
            val currentTime = System.currentTimeMillis()
            val lastClickTimestamp = clickTimestamps[timestampStr] ?: 0L
            if(currentTime - lastClickTimestamp < CLICK_DEBOUNCE_INTERVAL_MS)  {
                return
            } else {
                clickTimestamps[timestampStr] = currentTime
            }

            val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK) // The part that performs the click
            if (clicked) {
                Log.d("DetectorService", "Successfully clicked on node: ${node.viewIdResourceName ?: node.className}")
            } else {
                Log.w("DetectorService", "Failed to click on node: ${node.viewIdResourceName ?: node.className}")
                // This can happen if the node is no longer valid, not visible, or some other reason
            }
        } else {
            Log.w("DetectorService", "Node is not clickable: ${node.viewIdResourceName ?: node.className}")
        }
    }



    override fun onInterrupt() {
        Log.d("DetectorService", "Accessibility service interrupted.")
        overlaySurfaceViewManager?.removeOverlaySurfaceView()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d("DetectorService", "Accessibility service unbound.")
        overlaySurfaceViewManager?.removeOverlaySurfaceView()

        try {
            unregisterReceiver(screenStateReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver might not be registered if service failed to start properly
            Log.e("OverlayService", "Receiver not registered: ${e.message}")
        }
        return super.onUnbind(intent)
    }


}