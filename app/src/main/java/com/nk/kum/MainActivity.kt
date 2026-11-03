package com.nk.kum

import android.content.ComponentName
import android.content.Intent
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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
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
    private val prefs by lazy { getSharedPreferences(KumGuardService.PREFS, MODE_PRIVATE) }

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
            text = "Enable KUM Guard (Accessibility)"
            setTextColor(darkText)
            background = rounded(Color.rgb(255, 213, 79), 12)
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
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
            text = "Choose apps to block"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(darkText)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(smallButton("Block all") { setAll(true) })
        actions.addView(smallButton("Allow all") { setAll(false) })
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

    private fun loadApps(): List<AppItem> {
        val pm = packageManager
        val homes = HashSet<String>()
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            for (ri in pm.queryIntentActivities(homeIntent, 0)) homes.add(ri.activityInfo.packageName)
        } catch (_: Exception) {
        }
        val skip = HashSet<String>(homes)
        skip.add(packageName)
        skip.add("com.android.incallui")
        skip.add("com.android.phone")
        skip.add("com.android.server.telecom")
        try {
            val dialer = (getSystemService(TELECOM_SERVICE) as TelecomManager).defaultDialerPackage
            if (dialer != null) skip.add(dialer)
        } catch (_: Exception) {
        }
        val seen = HashSet<String>()
        val out = ArrayList<AppItem>()
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        for (ri in pm.queryIntentActivities(launcherIntent, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg in skip || !seen.add(pkg)) continue
            out.add(AppItem(pkg, ri.loadLabel(pm).toString(), ri.loadIcon(pm)))
        }
        out.sortBy { it.label.lowercase() }
        return out
    }

    private fun applyFilter() {
        shown = if (query.isBlank()) allApps.toList()
        else allApps.filter { it.label.contains(query.trim(), ignoreCase = true) }
        adapter?.notifyDataSetChanged()
    }

    private fun blockedSet(): MutableSet<String> =
        HashSet(prefs.getStringSet(KumGuardService.KEY_BLOCKED, emptySet()) ?: emptySet())

    private fun setBlocked(pkg: String, blocked: Boolean) {
        val s = blockedSet()
        if (blocked) s.add(pkg) else s.remove(pkg)
        prefs.edit().putStringSet(KumGuardService.KEY_BLOCKED, s).commit()
    }

    private fun setAll(blocked: Boolean) {
        val s = blockedSet()
        for (a in shown) {
            if (blocked) s.add(a.pkg) else s.remove(a.pkg)
        }
        prefs.edit().putStringSet(KumGuardService.KEY_BLOCKED, s).commit()
        adapter?.notifyDataSetChanged()
        refreshState()
    }

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount(): Int = shown.size
        override fun getItem(position: Int): Any = shown[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val item = shown[position]
            val ctx = this@MainActivity
            val isBlocked = blockedSet().contains(item.pkg)
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
            applyStateText(state, isBlocked)
            texts.addView(name)
            texts.addView(state)
            row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            val sw = SwitchCompat(ctx).apply {
                isChecked = isBlocked
                setOnCheckedChangeListener { _, checked ->
                    setBlocked(item.pkg, checked)
                    applyStateText(state, checked)
                    refreshState()
                    if (checked && item.pkg == "com.android.settings") {
                        Toast.makeText(
                            ctx,
                            "Settings will be blocked while protection is ON. Turn protection OFF in KUM to open it.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            row.addView(sw)
            return row
        }
    }

    private fun applyStateText(tv: TextView, blocked: Boolean) {
        tv.text = if (blocked) "Blocked" else "Allowed"
        tv.setTextColor(if (blocked) Color.rgb(198, 40, 40) else Color.rgb(46, 125, 50))
    }

    // ---------- Protection ----------

    private fun isGuardEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val cn = ComponentName(this, KumGuardService::class.java)
        return enabled.split(':').any {
            it.equals(cn.flattenToString(), true) || it.equals(cn.flattenToShortString(), true)
        }
    }

    private fun requestProtectionChange() {
        if (!unlocked) {
            authenticate()
            return
        }
        val isOn = prefs.getBoolean(KumGuardService.KEY_ON, false)
        if (isOn) {
            prefs.edit().putBoolean(KumGuardService.KEY_ON, false).commit()
        } else {
            if (!isGuardEnabled()) {
                Toast.makeText(this, "First enable KUM Guard in Accessibility settings (yellow button).", Toast.LENGTH_LONG).show()
                return
            }
            if (blockedSet().isEmpty()) {
                Toast.makeText(this, "Choose at least one app to block first.", Toast.LENGTH_LONG).show()
                return
            }
            prefs.edit().putBoolean(KumGuardService.KEY_ON, true).commit()
        }
        refreshState()
    }

    private fun refreshState() {
        if (!::dashStatus.isInitialized || !::toggleBtn.isInitialized || !::guardBtn.isInitialized) return
        val on = prefs.getBoolean(KumGuardService.KEY_ON, false)
        val blocked = blockedSet()
        val guard = isGuardEnabled()
        val sb = StringBuilder()
        if (on) sb.append("Protection is ON\n${blocked.size} app(s) blocked")
        else sb.append("Protection is OFF\nAll apps work normally")
        if (on && !guard) sb.append("\nKUM Guard is switched off in Accessibility settings")
        if (on && blocked.contains("com.android.settings")) {
            sb.append("\nSettings is blocked. Turn protection OFF here to open it.")
        }
        dashStatus.text = sb.toString()
        toggleBtn.text = if (on) "Turn Protection OFF" else "Turn Protection ON"
        toggleBtn.background = rounded(if (on) Color.rgb(198, 40, 40) else Color.rgb(46, 125, 50), 12)
        guardBtn.visibility = if (guard) View.GONE else View.VISIBLE
    }
}
