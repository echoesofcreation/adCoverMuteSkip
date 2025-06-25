package com.brainrotdecantation.adcovermuteskip

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.res.Resources
import android.graphics.Rect
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.Display
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.WindowMetrics // Requires API 30+
import org.json.JSONArray
import org.json.JSONObject


object Tools {
    fun dpToPx(dp: Float, resources: Resources): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()
    }

    fun getFullScreenRect(windowManager: WindowManager?, resources: Resources): Rect {
        var screenRect = Rect()
        screenRect.top = 0
        screenRect.left = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For API 30 (Android 11) and higher: Use WindowMetrics
            val windowMetrics: WindowMetrics? = windowManager?.currentWindowMetrics
            if (windowMetrics != null) {
                screenRect.right = windowMetrics.bounds.width()
                screenRect.bottom = windowMetrics.bounds.height()
            } else {
                screenRect.right = resources.displayMetrics.widthPixels // This doesn't include the status bars
                screenRect.bottom = resources.displayMetrics.heightPixels
            }
        } else {
            // For API 17 (Android 4.2) to API 29 (Android 10): Use Display.getRealMetrics()
            @Suppress("DEPRECATION") // getRealMetrics is deprecated in API 30+ but needed for older versions
            val display: Display? = windowManager?.defaultDisplay
            val realMetrics = DisplayMetrics()
            if (display != null) {
                display.getRealMetrics(realMetrics)
                screenRect.right = realMetrics.widthPixels
                screenRect.bottom = realMetrics.heightPixels
            } else {
                screenRect.right = resources.displayMetrics.widthPixels
                screenRect.bottom = resources.displayMetrics.heightPixels
            }
        }
        return screenRect
    }

    fun serializeDOMTree(node: AccessibilityNodeInfo?, depth: Int): JSONObject {
        val jsonNode = JSONObject()
        if (node == null) {
            jsonNode.put("error", "Null Node")
            return jsonNode
        }

        try {
            jsonNode.put("className", node.className?.toString())
            jsonNode.put("viewIdResourceName", node.viewIdResourceName) // The ID
            jsonNode.put("text", node.text?.toString())
            jsonNode.put("contentDescription", node.contentDescription?.toString())
            val boundsScreen = Rect()
            node.getBoundsInScreen(boundsScreen)
            jsonNode.put("getBoundsInScreen", boundsScreen.toString())
            //jsonNode.put("rawToString", node.toString()) // Not bad to check all info
            //jsonNode.put("packageName", node.packageName?.toString())
            jsonNode.put("childCount", node.childCount)

            val children = JSONArray()
            for (i in 0 until node.childCount) {
                val childNode = node.getChild(i)
                if (childNode != null) {
                    children.put(serializeDOMTree(childNode, depth + 1))
                }
            }
            if (children.length() > 0) {
                jsonNode.put("children", children)
            }

        } catch (e: Exception) {
            Log.e("DetectorService_TREE", "Error serializing node: ${e.message}", e)
            jsonNode.put("serializationError", e.message)
        }
        return jsonNode
    }
}