package com.example.tricount

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Central helper for showing local Android system notifications (the popup that
 * appears even when the app is in the background / closed).
 *
 * Usage
 * ─────
 * 1. Call [createNotificationChannel] once from Application.onCreate() or
 *    MainActivity.onCreate() — safe to call repeatedly.
 * 2. Call [showPaymentNotification] after a settlement is confirmed.
 *
 * AndroidManifest requirements (add these if not present)
 * ────────────────────────────────────────────────────────
 *   <!-- inside <manifest> -->
 *   <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
 *
 *   <!-- inside <application> -->
 *   <!-- no extra entries needed; channel is created at runtime -->
 */
object TriCountNotificationHelper {

    const val CHANNEL_ID   = "tricount_payments"
    const val CHANNEL_NAME = "Payments & Settlements"
    const val CHANNEL_DESC = "Notifications when a payment is confirmed or received"

    /**
     * Must be called once before any notification is shown.
     * Safe to call from onCreate() every time — Android is idempotent about it.
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH          // shows as a heads-up popup
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                enableLights(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Shows a system popup notification for a payment event.
     *
     * @param context   Any context (application context preferred).
     * @param title     Notification title  e.g. "Payment Sent ✓"
     * @param message   Body text           e.g. "Alice paid Bob ₹500.00"
     * @param notifId   Unique int ID — different IDs keep multiple notifications
     *                  visible in the drawer at the same time.
     */
    fun showPaymentNotification(
        context : Context,
        title   : String,
        message : String,
        notifId : Int = System.currentTimeMillis().toInt()
    ) {
        // Android 13+ requires POST_NOTIFICATIONS permission at runtime.
        // If the user hasn't granted it yet, skip silently — don't crash.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        // Tapping the notification opens NotificationsActivity directly.
        val tapIntent = Intent(context, NotificationsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)  // replace with R.drawable.ic_notification if you have one
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))  // expand for long notes
            .setPriority(NotificationCompat.PRIORITY_HIGH)                 // heads-up popup
            .setAutoCancel(true)                                           // dismiss on tap
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notifId, notification)
    }
}