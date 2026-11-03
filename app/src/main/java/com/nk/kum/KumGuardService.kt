package com.nk.kum

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ProgressBar

/**
 * No VPN (no key icon). Only the apps the parent chose to block are covered by a plain
 * loading spinner. Everything else (Home, Settings, KUM, ...) is never touched, so the phone
 * can never get stuck behind the cover. A watchdog re-checks the foreground app every
 * second and removes the cover as soon as the blocked app is no longer on screen.
 */
class KumGuardService : AccessibilityService() {
    companion object {
        const val PREFS = "kum_local"
        const val KEY_ON = "protection_on"
        const val KEY_BLOCKED = "blocked_pkgs"
    }

    private var overlay: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        handler.post { check() }
    }

    private val tick = object : Runnable {
        override fun run() {
            check()
            handler.postDelayed(this, 800)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return // KUM itself is handled by the watchdog tick
        evaluate(pkg)
    }

    private fun check() {
        if (!prefs.getBoolean(KEY_ON, false)) {
            hideOverlay()
            return
        }
        evaluate(currentPackage())
    }

    private fun currentPackage(): String? {
        return try {
            val root = rootInActiveWindow ?: return null
            val pkg = root.packageName?.toString()
            if (Build.VERSION.SDK_INT < 33) {
                @Suppress("DEPRECATION")
                root.recycle()
            }
            pkg
        } catch (_: Exception) {
            null
        }
    }

    private fun evaluate(pkg: String?) {
        if (!prefs.getBoolean(KEY_ON, false)) {
            hideOverlay()
            return
        }
        if (pkg == null || isNeutral(pkg)) return
        val blocked = prefs.getStringSet(KEY_BLOCKED, emptySet()) ?: emptySet()
        if (blocked.contains(pkg)) showOverlay() else hideOverlay()
    }

    // Windows that float on top of apps (notification shade, keyboard, system dialogs)
    // must not change whether the cover is showing.
    private fun isNeutral(pkg: String): Boolean {
        if (pkg == "com.android.systemui" || pkg == "android" ||
            pkg == "com.google.android.permissioncontroller" ||
            pkg == "com.android.permissioncontroller") return true
        return try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.enabledInputMethodList.any { it.packageName == pkg }
        } catch (_: Exception) {
            false
        }
    }

    private fun showOverlay() {
        if (overlay != null) return
        val size = (56 * resources.displayMetrics.density).toInt()
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(18, 18, 18))
            isClickable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            val spinner = ProgressBar(context, null, android.R.attr.progressBarStyleLarge).apply {
                isIndeterminate = true
                indeterminateTintList = ColorStateList.valueOf(Color.WHITE)
            }
            addView(spinner, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        )
        try {
            wm.addView(root, lp)
            overlay = root
        } catch (_: Exception) {
        }
    }

    private fun hideOverlay() {
        val v = overlay ?: return
        overlay = null
        try { wm.removeView(v) } catch (_: Exception) { }
    }

    override fun onInterrupt() { }

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacks(tick)
        try { prefs.unregisterOnSharedPreferenceChangeListener(prefListener) } catch (_: Exception) { }
        hideOverlay()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        hideOverlay()
        super.onDestroy()
    }
}
