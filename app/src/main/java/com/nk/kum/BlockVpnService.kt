package com.nk.kum

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean

class BlockVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    private val channelId = "kum_protection"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(42, makeNotification())
        if (!running.get()) startBlackholeVpn()
        return START_STICKY
    }

    private fun startBlackholeVpn() {
        try {
            val builder = Builder()
                .setSession("KUM Offline Protection")
                .addAddress("10.111.0.1", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("10.111.0.2")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                builder.addAddress("fd00:1111:1111::1", 128)
                builder.addRoute("::", 0)
            }
            tun = builder.establish()
            val fd = tun ?: throw IllegalStateException("Could not establish local VPN")
            running.set(true)
            worker = Thread {
                try {
                    FileInputStream(fd.fileDescriptor).use { input ->
                        val buffer = ByteArray(32767)
                        while (running.get()) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            // Deliberately discard every packet: no traffic is forwarded.
                        }
                    }
                } catch (_: Exception) { }
            }.apply { name = "KUM-Packet-Dropper"; start() }
        } catch (_: Exception) {
            running.set(false)
            stopSelf()
        }
    }

    private fun makeNotification(): Notification {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "KUM Protection", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, channelId) else Notification.Builder(this)
        return builder.setContentTitle("KUM Protection is ON")
            .setContentText("Wi-Fi and mobile data traffic are being blocked")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        running.set(false)
        try { tun?.close() } catch (_: Exception) { }
        tun = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onRevoke() {
        running.set(false)
        stopSelf()
        super.onRevoke()
    }
}
