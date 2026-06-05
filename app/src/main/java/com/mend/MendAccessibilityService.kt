package com.mend

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MendAccessibilityService : AccessibilityService() {

    private val TAG = "MendAccessibilityService"
    private val gson = Gson()

    private var currentLauncherPackage: String? = null
    private lateinit var sharedPreferences: SharedPreferences

    private var capturedLayouts: MutableMap<Int, List<IconInfo>> = mutableMapOf()
    private var currentPage = 0
    companion object {
        const val ACTION_CAPTURE_LAYOUT = "com.mend.ACTION_CAPTURE_LAYOUT"
        const val ACTION_LAYOUT_CAPTURED = "com.mend.ACTION_LAYOUT_CAPTURED"
        const val EXTRA_LAYOUT_JSON = "extra_layout_json"
        const val PREFS_NAME = "mend_accessibility_prefs"
        const val KEY_CAPTURED_LAYOUTS = "captured_layouts"
        const val KEY_CURRENT_PAGE = "current_page"
    }

    private val captureLayoutReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_CAPTURE_LAYOUT) {
                Log.d(TAG, "Received ACTION_CAPTURE_LAYOUT broadcast.")
                val rootNode = rootInActiveWindow
                if (rootNode != null) {
                    Log.d(TAG, "Root node is not null. Traversing...")
                    val icons = traverseAndExtractIcons(rootNode)
                    Log.d(TAG, "Found ${icons.size} icons.")
                    if (icons.isNotEmpty()) {
                        currentPage++
                        capturedLayouts[currentPage] = icons
                        saveCapturedLayouts()
                        Log.d(TAG, "Captured layout for page $currentPage: ${gson.toJson(icons)}")
                    } else {
                        Log.w(TAG, "No icons found on current screen.")
                    }

                    val fullLayoutJson = gson.toJson(capturedLayouts)
                    Log.d(TAG, "Sending full layout JSON: $fullLayoutJson")
                    SharedViewModel.capturedLayoutJson.postValue(fullLayoutJson)
                } else {
                    Log.w(TAG, "Root node is null, cannot capture layout.")
                }
            }
        }
    }

    private val wallpaperScheduler = WallpaperScheduler()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        Log.d(TAG, "onAccessibilityEvent: ${event.eventType} - ${event.packageName}")

        // Only process events from the current launcher application
        if (event.packageName != currentLauncherPackage) {
            Log.d(TAG, "Event from non-launcher app: ${event.packageName}")
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                Log.d(TAG, "Launcher Window State Changed: ${event.className} - ${event.text}")
                // This event can indicate a page change or app launch
                // We might want to trigger a capture here or on user request
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Log.d(TAG, "Launcher Window Content Changed: ${event.className} - ${event.text}")
                // This event is too frequent, only process on explicit request for now
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "onServiceConnected")

        val info = serviceInfo
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        serviceInfo = info

        sharedPreferences = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadCapturedLayouts()
        Log.d(TAG, "Loaded ${capturedLayouts.size} captured layouts. Current page: $currentPage")

        currentLauncherPackage = getLauncherPackageName(applicationContext)
        Log.d(TAG, "Current Launcher Package: $currentLauncherPackage")

        val captureFilter = IntentFilter(ACTION_CAPTURE_LAYOUT)
        registerReceiver(captureLayoutReceiver, captureFilter, RECEIVER_EXPORTED)

        // Register WallpaperScheduler for time ticks and boot (though boot is handled by system if in manifest,
        // we can also trigger a manual check now)
        val wallpaperFilter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        registerReceiver(wallpaperScheduler, wallpaperFilter)
        
        // Trigger initial check
        wallpaperScheduler.onReceive(this, Intent(Intent.ACTION_TIME_TICK))
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind")
        try {
            unregisterReceiver(captureLayoutReceiver)
            unregisterReceiver(wallpaperScheduler)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receivers", e)
        }
        saveCapturedLayouts() // Save on unbind as well
        return super.onUnbind(intent)
    }

    private fun getLauncherPackageName(context: Context): String? {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName
    }

    private fun traverseAndExtractIcons(node: AccessibilityNodeInfo): List<IconInfo> {
        val icons = mutableListOf<IconInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(node)

        while (queue.isNotEmpty()) {
            val currentNode = queue.removeFirst()

            // Check if this node represents an app icon
            // This is a heuristic and might need refinement for different launchers
            if (currentNode.isClickable && currentNode.packageName != null && currentNode.text != null) {
                val bounds = Rect()
                currentNode.getBoundsInScreen(bounds)
                icons.add(IconInfo(currentNode.packageName.toString(), currentNode.text.toString(), bounds))
            }

            for (i in 0 until currentNode.childCount) {
                currentNode.getChild(i)?.let { queue.add(it) }
            }
            currentNode.recycle()
        }
        return icons
    }

    private fun saveCapturedLayouts() {
        val editor = sharedPreferences.edit()
        editor.putString(KEY_CAPTURED_LAYOUTS, gson.toJson(capturedLayouts))
        editor.putInt(KEY_CURRENT_PAGE, currentPage)
        editor.apply()
        Log.d(TAG, "Saved ${capturedLayouts.size} layouts to SharedPreferences. Current page: $currentPage")
    }

    private fun loadCapturedLayouts() {
        val json = sharedPreferences.getString(KEY_CAPTURED_LAYOUTS, null)
        if (json != null) {
            val type = object : TypeToken<MutableMap<Int, List<IconInfo>>>() {}.type
            capturedLayouts = gson.fromJson(json, type)
            Log.d(TAG, "Loaded ${capturedLayouts.size} layouts from SharedPreferences.")
        }
        currentPage = sharedPreferences.getInt(KEY_CURRENT_PAGE, 0)
    }
}