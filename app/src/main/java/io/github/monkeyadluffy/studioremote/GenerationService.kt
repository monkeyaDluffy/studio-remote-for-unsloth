package io.github.monkeyadluffy.studioremote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager

/**
 * Foreground service held while a reply is being generated. Android (and especially MIUI/HyperOS)
 * cuts network access for background apps once the screen goes off; a foreground service with a
 * partial wake lock and a Wi-Fi lock keeps the stream alive until the reply finishes.
 */
class GenerationService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLanguage.wrap(newBase))

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannels(this)
        val notification = Notification.Builder(this, CHANNEL_WORKING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_writing))
            .setOngoing(true)
            .setContentIntent(openAppIntent(this))
            .build()
        startForeground(NOTIFICATION_WORKING, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        acquireLocks()
        return START_NOT_STICKY
    }

    private fun acquireLocks() {
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StudioRemote:generation")
                .apply {
                    setReferenceCounted(false)
                    acquire(2 * 60 * 60 * 1000L) // safety cap; released as soon as the reply ends
                }
        }
        if (wifiLock == null) {
            wifiLock = applicationContext.getSystemService(WifiManager::class.java)
                .createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "StudioRemote:generation")
                .apply {
                    setReferenceCounted(false)
                    acquire()
                }
        }
    }

    /**
     * Android 15+ caps dataSync services (~6 h a day) and calls this at the limit; not stopping
     * within seconds crashes the app. A durable chat run keeps going on Studio and is picked up
     * again when the chat is reopened (recoverBlankReplies), so giving up the service is safe.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        wifiLock = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_WORKING = "generation"
        private const val CHANNEL_DONE = "replies"
        private const val NOTIFICATION_WORKING = 1
        private const val NOTIFICATION_DONE = 2

        fun start(context: Context) {
            // Only ever called from a user action, so the foreground-start restriction doesn't apply;
            // guarded anyway because a refused start must never break the chat itself.
            runCatching { context.startForegroundService(Intent(context, GenerationService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GenerationService::class.java))
        }

        /** Posts a "reply ready" notification; used when the reply finished off-screen. */
        fun notifyDone(appContext: Context, preview: String) {
            val context = AppLanguage.wrap(appContext)
            ensureChannels(context)
            val notification = Notification.Builder(context, CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.notification_ready))
                .setContentText(preview.take(120))
                .setStyle(Notification.BigTextStyle().bigText(preview.take(400)))
                .setContentIntent(openAppIntent(context))
                .setAutoCancel(true)
                .build()
            context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_DONE, notification)
        }

        private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun ensureChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_WORKING, context.getString(R.string.channel_working), NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) }
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_DONE, context.getString(R.string.channel_done), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }
}
