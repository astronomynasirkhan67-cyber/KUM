package com.nk.kum

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.SystemClock
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ProgressBar

/**
 * No VPN, so no key icon. While protection is ON, any app other than KUM, the Home screen
 * and the Phone app is covered by a plain loading spinner that never finishes.
 */
class KumGuardService : AccessibilityService() {
    private var overlay: View? = null
    private var shownAt = 0L
    private val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val prefs by lazy { getSharedPreferences("kum_local", MODE_PRIVATE) }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (!prefs.getBoolean("protection_on", false)) {
            hideOverlay()
            return
        }
        when {
            isNeutral(pkg) -> return
            isAllowed(pkg) -> {
                // Ignore any event our own overlay might produce right after it appears.
                if (pkg == packageName && SystemClock.uptimeMillis() - shownAt < 600) return
                hideOverlay()
            }
            else -> showOverlay()
        }
    }

    // Windows that appear on top of apps (notification shade, keyboard, system dialogs):
    // they must not change whether the cover is showing.
    private fun isNeutral(pkg: String): Boolean {
        if (pkg == "com.android.systemui" || pkg == "android" ||
            pkg == "com.google.android.permissioncontroller" ||
            pkg == "com.android.permissioncontroller") return true
        return try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.enabledInputMethodList.any { it.packageName == pkg }
        } catch (_: Exception) { false }
    }

    private fun isAllowed(pkg: String): Boolean {
        if (pkg == packageName) return true
        if (pkg == "com.android.incallui" || pkg == "com.android.phone" ||
            pkg == "com.android.server.telecom") return true
        try {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            if (packageManager.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
                    .any { it.activityInfo?.packageName == pkg }) return true
        } catch (_: Exception) { }
        try {
            val tm = getSystemService(TELECOM_SERVICE) as TelecomManager
            if (tm.defaultDialerPackage == pkg) return true
        } catch (_: Exception) { }
        return false
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
            shownAt = SystemClock.uptimeMillis()
        } catch (_: Exception) { }
    }

    private fun hideOverlay() {
        val v = overlay ?: return
        overlay = null
        try { wm.removeView(v) } catch (_: Exception) { }
    }

    override fun onInterrupt() { }

    override fun onUnbind(intent: Intent?): Boolean {
        hideOverlay()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }
}
