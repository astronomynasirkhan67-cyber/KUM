package com.nk.kum

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor

class MainActivity : AppCompatActivity() {
    private val requestVpn = 701
    private lateinit var status: TextView
    private lateinit var toggle: Button
    private var unlocked = false
    private val prefs by lazy { getSharedPreferences("kum_local", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showLockedScreen()
        authenticate()
    }

    override fun onResume() {
        super.onResume()
        if (unlocked && ::toggle.isInitialized) refreshState()
    }

    private fun authenticate() {
        val manager = BiometricManager.from(this)
        val canAuth = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            statusText("Fingerprint security is not available. Enrol a strong biometric in Android Settings.")
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

    private fun showLockedScreen() {
        val layout = baseLayout()
        val title = TextView(this).apply {
            text = "KUM\nKids Usage Manager"
            textSize = 30f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        }
        status = TextView(this).apply {
            textSize = 15f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        }
        val retry = Button(this).apply { text = "Verify Parent Fingerprint"; setOnClickListener { authenticate() } }
        layout.addView(title); layout.addView(status); layout.addView(retry)
        setContentView(layout)
    }

    private fun showDashboard() {
        val layout = baseLayout()
        val title = TextView(this).apply {
            text = "KUM  •  Parent Dashboard"
            textSize = 24f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        }
        status = TextView(this).apply {
            textSize = 17f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        }
        toggle = Button(this).apply {
            textSize = 18f
            setOnClickListener { requestProtectionChange() }
        }
        val info = TextView(this).apply {
            text = "Protection ON routes device internet into a local VPN and discards packets.\nNo cloud account or remote server is used."
            textSize = 13f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        }
        val lock = Button(this).apply {
            text = "Lock Parent Dashboard"
            setOnClickListener { unlocked = false; showLockedScreen(); authenticate() }
        }
        layout.addView(title); layout.addView(status); layout.addView(toggle)
        layout.addView(info); layout.addView(lock)
        setContentView(layout)
        refreshState()
    }

    private fun requestProtectionChange() {
        if (!unlocked) { authenticate(); return }
        val isOn = prefs.getBoolean("protection_on", false)
        if (isOn) {
            prefs.edit().putBoolean("protection_on", false).commit()
            stopService(Intent(this, BlockVpnService::class.java))
            refreshState()
        } else {
            val prepare = VpnService.prepare(this)
            if (prepare != null) startActivityForResult(prepare, requestVpn)
            else startBlockService()
        }
    }

    @Deprecated("Deprecated by Android; retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestVpn) {
            if (resultCode == RESULT_OK) startBlockService()
            else status.text = "VPN permission was not granted. Protection is OFF."
        }
    }

    private fun startBlockService() {
        prefs.edit().putBoolean("protection_on", true).commit()
        val intent = Intent(this, BlockVpnService::class.java)
        ContextCompat.startForegroundService(this, intent)
        refreshState()
    }

    private fun refreshState() {
        val on = prefs.getBoolean("protection_on", false)
        status.text = if (on) "PROTECTION ON\nWi-Fi + Mobile Data blocked" else "PROTECTION OFF\nInternet access is unchanged"
        toggle.text = if (on) "Turn Protection OFF" else "Turn Protection ON"
    }

    private fun statusText(message: String) {
        if (::status.isInitialized) status.text = message
    }

    private fun baseLayout() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(28, 36, 28, 36)
        setBackgroundColor(Color.rgb(103, 85, 220))
    }
}
