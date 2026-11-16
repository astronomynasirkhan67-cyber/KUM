package com.nk.kum

import android.content.ComponentName
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.telecom.TelecomManager
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.concurrent.Executor

private data class AppItem(val pkg: String, val label: String, val icon: Drawable)

class MainActivity : AppCompatActivity() {
    private lateinit var lockStatus: TextView
    private lateinit var dashStatus: TextView
    private lateinit var toggleBtn: Button
    private lateinit var guardBtn: Button
    private var unlocked = false
    private var lockPending = false
    private var dashboardShown = false
    private val allApps = ArrayList<AppItem>()
    private var shown: List<AppItem> = emptyList()
    private var query = ""
    private var adapter: AppAdapter? = null
    private val prefs by lazy { getSharedPreferences("kum_local", MODE_PRIVATE) }

    private val purple = Color.rgb(103, 85, 220)
    private val pageBg = Color.rgb(244, 243, 251)
    private val darkText = Color.rgb(30, 30, 40)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showLockedScreen()
        authenticate()
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app locks the dashboard again, so a child can't open it later.
        if (unlocked) {
            unlocked = false
            lockPending = true
        }
    }

    override fun onResume() {
        super.onResume()
        if (lockPending) {
            lockPending = false
            showLockedScreen()
            authenticate()
        } else if (unlocked && dashboardShown) {
            refreshState()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    // ---------- Fingerprint ----------

    private fun authenticate() {
        val manager = BiometricManager.from(this)
        val canAuth = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            statusText("Fingerprint security is not available. Enrol a fingerprint in Android Settings.")
            return
        }
        val executor: Executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                unlocked = true
                showDashboard()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                unlocked = false
                statusText("Parent verification required: $errString")
            }

            override fun onAuthenticationFailed() {
                statusText("Fingerprint not recognized. Try again.")
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("KUM Parent Verification")
            .setSubtitle("Use the parent's enrolled fingerprint")
            .setNegativeButtonText("Cancel")
            .build()
        prompt.authenticate(info)
    }

    private fun statusText(message: String) {
        if (::lockStatus.isInitialized) lockStatus.text = message
    }

    // ---------- Screens ----------

    private fun showLockedScreen() {
        dashboardShown = false
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(36), dp(28), dp(36))
            setBackgroundColor(purple)
        }
        val title = TextView(this).apply {
            text = "KUM\nKids Usage Manager"
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        lockStatus = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(16))
        }
        val retry = Button(this).apply {
            text = "Verify Parent Fingerprint"
            setOnClickListener { authenticate() }
        }
        layout.addView(title)
        layout.addView(lockStatus)
        layout.addView(retry)
        ViewCompat.setOnApplyWindowInsetsListener(layout) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(dp(28), bars.top + dp(36), dp(28), bars.bottom + dp(36))
            insets
        }
        setContentView(layout)
    }

    private fun showDashboard() {
        dashboardShown = true
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pageBg)
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(purple)
            setPadding(dp(20), dp(34), dp(20), dp(16))
        }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(TextView(this).apply {
            text = "KUM"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        titles.addView(TextView(this).apply {
            text = "Parent Dashboard"
            textSize = 13f
            setTextColor(Color.argb(210, 255, 255, 255))
        })
        header.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(this).apply {
            text = "LOCK"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = rounded(Color.argb(70, 255, 255, 255), 20)
            setOnClickListener {
                unlocked = false
                showLockedScreen()
                authenticate()
            }
        })
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Status card
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE, 16)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        dashStatus = TextView(this).apply {
            textSize = 15f
            setTextColor(darkText)
        }
        toggleBtn = Button(this).apply {
            isAllCaps = false
            textSize = 16f
            setTextColor(Color.WHITE)
            setOnClickListener { requestProtectionChange() }
        }
        guardBtn = Button(this).apply {
            isAllCaps = false
            text = "VPN protection is used — no Accessibility access"
            setTextColor(darkText)
            background = rounded(Color.rgb(255, 213, 79), 12)
            setOnClickListener { }
        }
        card.addView(dashStatus)
        card.addView(toggleBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        card.addView(guardBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        root.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(14), dp(12), dp(14), dp(6))
        })

        // Section title + quick actions
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(14), dp(0))
        }
        actions.addView(TextView(this).apply {
            text = "Choose apps allowed to use internet"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(darkText)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(smallButton("Block all") { setAll(false) })
        actions.addView(smallButton("Allow all") { setAll(true) })
        root.addView(actions)

        val search = EditText(this).apply {
            hint = "Search apps"
            textSize = 15f
            setSingleLine(true)
            setTextColor(darkText)
            setHintTextColor(Color.GRAY)
            background = rounded(Color.WHITE, 12)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString() ?: ""
                    applyFilter()
                }

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
        root.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(14), dp(8), dp(14), dp(4))
        })

        // App list
        val newAdapter = AppAdapter()
        adapter = newAdapter
        val list = ListView(this).apply {
            divider = ColorDrawable(Color.TRANSPARENT)
            dividerHeight = dp(8)
            setPadding(dp(14), dp(6), dp(14), dp(14))
            clipToPadding = false
            selector = ColorDrawable(Color.TRANSPARENT)
            setAdapter(newAdapter)
        }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, 0, bars.right, bars.bottom)
            header.setPadding(dp(20), bars.top + dp(14), dp(20), dp(16))
            insets
        }
        setContentView(root)
        refreshState()

        Thread {
            val items = loadApps()
            runOnUiThread {
                allApps.clear()
                allApps.addAll(items)
                applyFilter()
            }
        }.start()
    }

    private fun smallButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 12f
            setTextColor(purple)
            background = rounded(Color.rgb(233, 230, 252), 18)
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(14), dp(6), dp(14), dp(6))
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)).apply { leftMargin = dp(6) }
        }

    // ---------- Apps ----------

    @Suppress("DEPRECATION")
    private fun loadApps(): List<AppItem> {
        val pm = packageManager
        val out = ArrayList<AppItem>()
        val seen = HashSet<String>()
        try {
            // Show user-installed apps that have a launcher icon. This intentionally
            // excludes Android/system packages and preinstalled system applications.
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            for (activity in pm.queryIntentActivities(launcherIntent, 0)) {
                val info = activity.activityInfo?.applicationInfo ?: continue
                val pkg = info.packageName
                val isSystem = (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystem = (info.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                if (pkg == packageName || isSystem || isUpdatedSystem || !seen.add(pkg)) continue
                try {
                    val label = pm.getApplicationLabel(info).toString()
                    val icon = pm.getApplicationIcon(info)
                    out.add(AppItem(pkg, label, icon))
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
        out.sortBy { it.label.lowercase() }
        return out
    }

    private fun applyFilter() {
        shown = if (query.isBlank()) allApps.toList()
        else allApps.filter { it.label.contains(query.trim(), ignoreCase = true) }
        adapter?.notifyDataSetChanged()
    }

    private fun allowedSet(): MutableSet<String> =
        HashSet(prefs.getStringSet(KEY_ALLOWED, emptySet()) ?: emptySet())

    private fun setAllowed(pkg: String, allowed: Boolean) {
        val set = allowedSet()
        if (allowed) set.add(pkg) else set.remove(pkg)
        prefs.edit().putStringSet(KEY_ALLOWED, set).commit()
        restartVpnIfActive()
    }

    private fun setAll(allowed: Boolean) {
        val set = allowedSet()
        for (app in shown) {
            if (allowed) set.add(app.pkg) else set.remove(app.pkg)
        }
        prefs.edit().putStringSet(KEY_ALLOWED, set).commit()
        restartVpnIfActive()
        adapter?.notifyDataSetChanged()
        refreshState()
    }

    private fun restartVpnIfActive() {
        if (!prefs.getBoolean(KEY_ON, false)) return
        startVpnService()
    }

    private fun startVpnService() {
        val intent = Intent(this, BlockVpnService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
        // Reflect the real tunnel state instead of leaving the dashboard at "Starting".
        Handler(Looper.getMainLooper()).postDelayed({ refreshState() }, 800)
    }

    private fun requestVpnPermissionAndStart() {
        val prepare = VpnService.prepare(this)
        if (prepare != null) {
            startActivityForResult(prepare, VPN_REQUEST)
        } else {
            prefs.edit().putBoolean(KEY_ON, true).commit()
            startVpnService()
            refreshState()
        }
    }

    @Deprecated("Uses Android's VPN consent activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST) {
            if (resultCode == RESULT_OK) {
                prefs.edit().putBoolean(KEY_ON, true).commit()
                startVpnService()
            } else {
                prefs.edit().putBoolean(KEY_ON, false).commit()
                Toast.makeText(this, "VPN permission is required for KUM blocking.", Toast.LENGTH_LONG).show()
            }
            refreshState()
        }
    }

    companion object {
        private const val KEY_ALLOWED = "allowed_pkgs"
        private const val KEY_ON = "protection_on"
        private const val KEY_ACTIVE = "vpn_active"
        private const val KEY_ERROR = "vpn_error"
        private const val VPN_REQUEST = 7104
    }

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount(): Int = shown.size
        override fun getItem(position: Int): Any = shown[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val item = shown[position]
            val ctx = this@MainActivity
            val isAllowed = allowedSet().contains(item.pkg)
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(Color.WHITE, 14)
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            val icon = ImageView(ctx).apply { setImageDrawable(item.icon) }
            row.addView(icon, LinearLayout.LayoutParams(dp(42), dp(42)))

            val texts = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, dp(8), 0)
            }
            val name = TextView(ctx).apply {
                text = item.label
                textSize = 16f
                setTextColor(darkText)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            val state = TextView(ctx).apply { textSize = 12f }
            applyStateText(state, isAllowed)
            texts.addView(name)
            texts.addView(state)
            row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            val sw = SwitchCompat(ctx).apply {
                isChecked = isAllowed
                setOnCheckedChangeListener { _, checked ->
                    setAllowed(item.pkg, checked)
                    applyStateText(state, checked)
                    refreshState()
                }
            }
            row.addView(sw)
            return row
        }
    }

    private fun applyStateText(tv: TextView, allowed: Boolean) {
        tv.text = if (allowed) "Internet allowed" else "Internet blocked"
        tv.setTextColor(if (allowed) Color.rgb(46, 125, 50) else Color.rgb(198, 40, 40))
    }

    // ---------- Protection ----------

    // Prominent disclosure + consent shown BEFORE sending the parent to Accessibility settings.
    private fun requestProtectionChange() {
        if (!unlocked) {
            authenticate()
            return
        }
        if (prefs.getBoolean(KEY_ON, false)) {
            prefs.edit().putBoolean(KEY_ON, false).putBoolean(KEY_ACTIVE, false).commit()
            stopService(Intent(this, BlockVpnService::class.java))
            refreshState()
        } else {
            requestVpnPermissionAndStart()
        }
    }

    private fun refreshState() {
        if (!::dashStatus.isInitialized || !::toggleBtn.isInitialized || !::guardBtn.isInitialized) return
        val on = prefs.getBoolean(KEY_ON, false)
        val active = prefs.getBoolean(KEY_ACTIVE, false)
        val error = prefs.getString(KEY_ERROR, "").orEmpty()
        val allowed = allowedSet()
        dashStatus.text = when {
            on && active -> "VPN protection is ACTIVE\n${allowed.size} app(s) allowed internet\nAll other app traffic is blocked"
            on && error.isNotBlank() -> "VPN could not start\n$error"
            on -> "Starting VPN protection…\nWaiting for Android to establish the VPN"
            else -> "Protection is OFF\nAll apps can use internet"
        }
        toggleBtn.text = if (on) "Turn Protection OFF" else "Turn Protection ON"
        toggleBtn.background = rounded(if (on) Color.rgb(198, 40, 40) else Color.rgb(46, 125, 50), 12)
        guardBtn.visibility = View.GONE
    }
}
