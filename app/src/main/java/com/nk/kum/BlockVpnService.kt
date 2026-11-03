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

class BlockVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    private val channelId = "kum_protection_quiet"

    private fun prefs() = getSharedPreferences("kum_local", MODE_PRIVATE)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must always call startForeground quickly after startForegroundService().
        startForeground(42, makeNotification())
        if (!prefs().getBoolean("protection_on", false)) {
            // Protection was switched OFF (or service was restarted by the system after OFF).
            shutdown()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running.get()) startBlackholeVpn()
        return START_STICKY
    }

    private fun startBlackholeVpn() {
        try {
            val builder = Builder()
                .setSession("KUM")
                .setBlocking(true)
                .addAddress("10.111.0.1", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("10.111.0.2")
                .addAddress("fd00:1111:1111::1", 128)
                .addRoute("::", 0)
            val pfd = builder.establish() ?: throw IllegalStateException("Could not establish local VPN")
            tun = pfd
            running.set(true)
            worker = Thread {
                val fd = pfd.fileDescriptor
                val p = StructPollfd()
                p.fd = fd
                p.events = OsConstants.POLLIN.toShort()
                val arr = arrayOf(p)
                val buffer = ByteArray(32767)
                // poll() with a short timeout so the thread can always exit quickly when
                // protection is switched OFF (a plain blocking read() would keep the VPN alive).
                while (running.get()) {
                    try {
                        p.revents = 0
                        val ready = Os.poll(arr, 300)
                        if (ready > 0) {
                            val rev = p.revents.toInt()
                            if ((rev and OsConstants.POLLIN) != 0) {
                                // Deliberately discard every packet: no traffic is forwarded.
                                if (Os.read(fd, buffer, 0, buffer.size) < 0) break
                            } else if ((rev and (OsConstants.POLLERR or OsConstants.POLLHUP or OsConstants.POLLNVAL)) != 0) {
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
            }.apply { name = "KUM-Packet-Dropper"; start() }
        } catch (_: Exception) {
            running.set(false)
            prefs().edit().putBoolean("protection_on", false).apply()
            stopSelf()
        }
    }

    // Stops the packet thread and CLOSES the VPN interface (this removes the key icon).
    private fun shutdown() {
        running.set(false)
        try { worker?.join(1500) } catch (_: InterruptedException) { }
        worker = null
        try { tun?.close() } catch (_: Exception) { }
        tun = null
    }

    private fun makeNotification(): Notification {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(channelId, "KUM", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            manager.createNotificationChannel(channel)
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, channelId) else Notification.Builder(this)
        return builder.setContentTitle("KUM")
            .setContentText("Running")
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
