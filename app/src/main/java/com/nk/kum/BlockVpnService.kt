package com.nk.kum

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local black-hole VPN. Apps in allowed_pkgs are excluded from the VPN and use the
 * normal network. Every other app is routed into this TUN interface and its packets
 * are deliberately discarded. No traffic is decrypted, logged, or forwarded.
 */
class BlockVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    private val channelId = "kum_protection"

    private fun prefs() = getSharedPreferences(PREFS, MODE_PRIVATE)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(NOTIFICATION_ID, makeNotification())
            if (!prefs().getBoolean(KEY_ON, false)) {
                shutdown()
                stopSelf()
                return START_NOT_STICKY
            }
            // Rebuild the tunnel whenever the app allow-list changes.
            shutdown()
            startBlackholeVpn()
        } catch (e: Exception) {
            markFailure(e)
            shutdown()
            stopSelf()
            return START_NOT_STICKY
        }
        return if (running.get()) START_STICKY else START_NOT_STICKY
    }

    private fun startBlackholeVpn() {
        val allowed = prefs().getStringSet(KEY_ALLOWED, emptySet())?.toSet() ?: emptySet()
        val builder = Builder()
            .setSession("KUM parental controls")
            .setBlocking(true)
            .addAddress("10.111.0.1", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("10.111.0.2")
            .addAddress("fd00:1111:1111::1", 128)
            .addRoute("::", 0)

        // addDisallowedApplication means the selected app bypasses this VPN and
        // therefore retains normal internet access. All other apps enter the TUN.
        for (pkg in allowed) {
            try {
                builder.addDisallowedApplication(pkg)
            } catch (_: Exception) {
                // Ignore packages that were uninstalled after being selected.
            }
        }

        val pfd = builder.establish()
            ?: throw IllegalStateException("Android did not establish the KUM VPN. Check VPN permission and any other active VPN.")
        tun = pfd
        running.set(true)
        prefs().edit()
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_ERROR, "")
            .apply()

        worker = Thread {
            val fd = pfd.fileDescriptor
            val poll = StructPollfd().apply {
                this.fd = fd
                events = OsConstants.POLLIN.toShort()
            }
            val pollArray = arrayOf(poll)
            val buffer = ByteArray(32767)
            while (running.get()) {
                try {
                    poll.revents = 0
                    val ready = Os.poll(pollArray, 300)
                    if (ready > 0) {
                        val events = poll.revents.toInt()
                        if ((events and OsConstants.POLLIN) != 0) {
                            // Read and discard the packet. Nothing is forwarded.
                            try {
                                if (Os.read(fd, buffer, 0, buffer.size) < 0) break
                            } catch (e: ErrnoException) {
                                if (e.errno != OsConstants.EAGAIN && e.errno != OsConstants.EINTR) throw e
                            }
                        } else if ((events and (OsConstants.POLLERR or OsConstants.POLLHUP or OsConstants.POLLNVAL)) != 0) {
                            break
                        }
                    }
                } catch (e: Exception) {
                    if (running.get()) markFailure(e)
                    break
                }
            }
            if (running.get()) {
                markFailure(IllegalStateException("VPN packet tunnel stopped unexpectedly"))
            }
            running.set(false)
            prefs().edit().putBoolean(KEY_ACTIVE, false).apply()
        }.apply {
            name = "KUM-VPN-Packet-Dropper"
            isDaemon = true
            start()
        }
    }

    private fun markFailure(error: Exception) {
        prefs().edit()
            .putBoolean(KEY_ACTIVE, false)
            .putBoolean(KEY_ON, false)
            .putString(KEY_ERROR, error.message ?: error.javaClass.simpleName)
            .apply()
    }

    private fun shutdown() {
        running.set(false)
        try { tun?.close() } catch (_: Exception) { }
        tun = null
        try { worker?.join(800) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        worker = null
        prefs().edit().putBoolean(KEY_ACTIVE, false).apply()
    }

    private fun makeNotification(): Notification {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "KUM protection", NotificationManager.IMPORTANCE_MIN).apply {
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_SECRET
                }
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channelId)
            else Notification.Builder(this)
        return builder.setContentTitle("KUM protection")
            .setContentText("App internet filtering is active")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .build()
    }

    override fun onDestroy() {
        shutdown()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onRevoke() {
        prefs().edit().putBoolean(KEY_ON, false).putBoolean(KEY_ACTIVE, false).apply()
        shutdown()
        stopSelf()
        super.onRevoke()
    }

    companion object {
        private const val PREFS = "kum_local"
        private const val KEY_ON = "protection_on"
        private const val KEY_ALLOWED = "allowed_pkgs"
        private const val KEY_ACTIVE = "vpn_active"
        private const val KEY_ERROR = "vpn_error"
        private const val NOTIFICATION_ID = 42
    }
}
