package com.brainrotdecantation.adcovermuteskip

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.core.view.isVisible
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.content.ComponentName
import android.net.Uri
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.widget.SeekBar
import com.brainrotdecantation.adcovermuteskip.databinding.ActivityMainBinding
import com.brainrotdecantation.adcovermuteskip.DetectorService
import android.view.View

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var accessibilityManager: AccessibilityManager

    private val accessibilityStateChangeListener = object : AccessibilityManager.AccessibilityStateChangeListener {
        override fun onAccessibilityStateChanged(enabled: Boolean) {
            // This callback is triggered when any accessibility service's state changes.
            // We need to re-check the status of *our* specific service.
            updateServiceStatusDisplay()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        accessibilityManager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

        updateServiceStatusDisplay()
        binding.buttonOpenAccessibilitySettings.setOnClickListener {
            // Open accessibility settings
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
        binding.buttonOpenAppSettings.setOnClickListener {
            // Open app settings
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
            }
            startActivity(intent)
        }

        val dataOpacity: Int = DataStorage.loadOpacity(this).coerceIn(0, 255)
        binding.seekBarOpacity.progress = dataOpacity
        binding.tvOpacityValue.text = dataOpacity.toString()
        binding.seekBarOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvOpacityValue.text = progress.coerceIn(0, 255).toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Not needed for this use case, but part of the interface
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // User has finished adjusting the slider, save the new volume
                seekBar?.let {
                    DataStorage.saveOpacity(this@MainActivity, it.progress.coerceIn(0, 255))
                }
            }
        })

        binding.switchMonitorVolumeUp.isChecked = DataStorage.loadMonitorVolumeClick(this)
        binding.switchMonitorVolumeUp.setOnCheckedChangeListener { _, isChecked ->
            DataStorage.saveMonitorVolumeClick(this, isChecked)
        }
    }

    override fun onResume() {
        super.onResume()
        // 2. Register the listener when the activity comes to the foreground (resumes)
        accessibilityManager.addAccessibilityStateChangeListener(accessibilityStateChangeListener)
        // Also call update on resume, in case the user navigates back from settings
        // without necessarily toggling the service (e.g., they just backed out).
        updateServiceStatusDisplay()
    }

    override fun onPause() {
        super.onPause()
        // 3. Unregister the listener when the activity goes into the background (pauses)
        // This is crucial to prevent memory leaks and unnecessary checks when your app is not active.
        accessibilityManager.removeAccessibilityStateChangeListener(accessibilityStateChangeListener)
    }

    private fun updateServiceStatusDisplay() {
        val isEnabled = isMyAccessibilityServiceEnabled()
        if (isEnabled) {
            binding.serviceStatusTextView.text = "Service Status: ENABLED"
            binding.serviceStatusTextView.setTextColor(resources.getColor(android.R.color.holo_green_dark, theme))
        } else {
            binding.serviceStatusTextView.text = "Service Status: DISABLED"
            binding.serviceStatusTextView.setTextColor(resources.getColor(android.R.color.holo_red_dark, theme))
        }

        val memoryUsageMb = getAppMemoryUsageMb()
        binding.memoryUseTextView.text = "RAM used: ${memoryUsageMb} MB"
    }

    private fun isMyAccessibilityServiceEnabled(): Boolean {
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        val expectedComponentName = ComponentName(this, DetectorService::class.java)

        for (serviceInfo in enabledServices) {
            val resolvedComponentName = ComponentName(serviceInfo.resolveInfo.serviceInfo.packageName, serviceInfo.resolveInfo.serviceInfo.name)
            if (expectedComponentName == resolvedComponentName) {
                return true
            }
        }
        return false
    }

    private fun getAppMemoryUsageMb(): Long {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val myProcessInfo = activityManager.runningAppProcesses?.find {
            it.processName == applicationContext.packageName // Find our app's process
        }

        return if (myProcessInfo != null) {
            val pids = intArrayOf(myProcessInfo.pid)
            val memoryInfoArray = activityManager.getProcessMemoryInfo(pids)
            if (memoryInfoArray != null && memoryInfoArray.isNotEmpty()) {
                // totalPss provides a good estimate of real memory usage, in KiloBytes
                // Convert KiloBytes to MegaBytes
                memoryInfoArray[0].totalPss.toLong() / 1024L
            } else {
                0L
            }
        } else {
            0L // Process not found or not running
        }
    }
}