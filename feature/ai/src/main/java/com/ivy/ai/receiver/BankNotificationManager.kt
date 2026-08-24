package com.ivy.ai.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ivy.ai.model.BankParsedTransaction
import com.ivy.base.model.TransactionType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Random
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BankNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "ivy_auto_bank_transactions"
        const val CHANNEL_NAME = "Bank & Card Transaction Alerts"

        const val ACTION_QUICK_ADD = "com.ivy.wallet.ACTION_QUICK_ADD_TRANSACTION"
        const val ACTION_DISMISS = "com.ivy.wallet.ACTION_DISMISS_TRANSACTION"

        const val EXTRA_AMOUNT = "extra_amount"
        const val EXTRA_CURRENCY = "extra_currency"
        const val EXTRA_TYPE = "extra_type"
        const val EXTRA_MERCHANT = "extra_merchant"
        const val EXTRA_CATEGORY = "extra_category"
        const val EXTRA_RAW_TEXT = "extra_raw_text"
        const val EXTRA_MAPPED_ACCOUNT_ID = "extra_mapped_account_id"
        const val EXTRA_NOTIF_ID = "extra_notif_id"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Interactive notifications for detected bank SMS and push payments"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showDetectedTransactionNotification(
        parsed: BankParsedTransaction,
        mappedAccountId: java.util.UUID? = null
    ) {
        val notifId = Random().nextInt(100000)

        // 1. Quick Add Action Intent
        val quickAddIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_QUICK_ADD
            putExtra(EXTRA_NOTIF_ID, notifId)
            putExtra(EXTRA_AMOUNT, parsed.amount)
            putExtra(EXTRA_CURRENCY, parsed.currency)
            putExtra(EXTRA_TYPE, parsed.type.name)
            putExtra(EXTRA_MERCHANT, parsed.merchant)
            putExtra(EXTRA_CATEGORY, parsed.categoryName)
            putExtra(EXTRA_RAW_TEXT, parsed.rawText)
            putExtra(EXTRA_MAPPED_ACCOUNT_ID, mappedAccountId?.toString())
        }
        val quickAddPendingIntent = PendingIntent.getBroadcast(
            context,
            notifId,
            quickAddIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Dismiss Intent
        val dismissIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_DISMISS
            putExtra(EXTRA_NOTIF_ID, notifId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            notifId + 1,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedAmount = "${parsed.currency} ${"%,.2f".format(parsed.amount)}"
        val typeBadge = if (parsed.type == TransactionType.INCOME) "💰 Income Detected" else "💳 Expense Detected"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("$typeBadge: $formattedAmount")
            .setContentText("${parsed.merchant.ifBlank { "Bank Transaction" }} • ${parsed.categoryName}")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${parsed.merchant.ifBlank { "Bank Transaction" }} (${parsed.categoryName})\n\n\"${parsed.rawText}\"")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_input_add, "✅ Add to Wallet", quickAddPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "❌ Dismiss", dismissPendingIntent)
            .build()

        notificationManager.notify(notifId, notification)
    }

    fun showAddedSuccessNotification(notifId: Int, summary: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("✅ Added to Ivy Wallet")
            .setContentText(summary)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setTimeoutAfter(4000)
            .build()

        notificationManager.notify(notifId, notification)
    }

    fun dismissNotification(notifId: Int) {
        notificationManager.cancel(notifId)
    }
}
