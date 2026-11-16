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
 * Local black-hole VPN. Android routes every app through this VPN except packages
 * explicitly added to the allowed list. Packets from blocked apps are discarded.
 * No traffic is decrypted, logged, or forwarded.
 */
class BlockVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    private val channelId = "kum_protection"

    private fun prefs() = getSharedPreferences("kum_local", MODE_PRIVATE)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(42, makeNotification())
        if (!prefs().getBoolean("protection_on", false)) {
            shutdown()
            stopSelf()
            return START_NOT_STICKY
        }
        if (running.get()) shutdown()\n        startBlackholeVpn()
        return START_STICKY
    }

    private fun startBlackholeVpn() {
        try {
            val builder = Builder()
                .setSession("KUM parental controls")
                .setBlocking(true)
                .addAddress("10.111.0.1", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("10.111.0.2")
                .addAddress("fd00:1111:1111::1", 128)
                .addRoute("::", 0)

            val allowed = prefs().getStringSet("allowed_pkgs", emptySet()) ?: emptySet()
            for (pkg in allowed) {
                try {
                    // Excluded packages use the normal network directly.
                    builder.addDisallowedApplication(pkg)
                } catch (_: Exception) {
                    // A package may have been uninstalled since the parent selected it.
                }
            }

            val pfd = builder.establish() ?: throw IllegalStateException("VPN permission was not granted")
            tun = pfd
            running.set(true)
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
                            val revents = poll.revents.toInt()
                            if ((revents and OsConstants.POLLIN) != 0) {
                                // Discard packets: no app other than the explicit allow-list
                                // can send or receive internet traffic while KUM is active.
                                if (Os.read(fd, buffer, 0, buffer.size) < 0) break
                            } else if ((revents and (OsConstants.POLLERR or OsConstants.POLLHUP or OsConstants.POLLNVAL)) != 0) {
                                break
                            }
                        }
                    } catch (e: ErrnoException) {
                        if (e.errno == OsConstants.EINTR || e.errno == OsConstants.EAGAIN) continue
                        break
                    } catch (_: Exception) {
                        break
                    }
                }
            }.apply { name = "KUM-VPN-Packet-Dropper"; start() }
        } catch (_: Exception) {
            running.set(false)
            prefs().edit().putBoolean("protection_on", false).apply()
            stopSelf()
        }
    }

    private fun shutdown() {
        running.set(false)
        try { worker?.join(1200) } catch (_: InterruptedException) { }
        worker = null
        try { tun?.close() } catch (_: Exception) { }
        tun = null
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
        prefs().edit().putBoolean("protection_on", false).apply()
        shutdown()
        stopSelf()
        super.onRevoke()
    }
}
