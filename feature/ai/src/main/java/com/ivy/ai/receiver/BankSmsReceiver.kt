package com.ivy.ai.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.ivy.ai.engine.BankAiEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class BankSmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var aiEngine: BankAiEngine

    @Inject
    lateinit var notificationManager: BankNotificationManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val fullBody = messages.joinToString(" ") { it.messageBody ?: "" }.trim()
        if (fullBody.isBlank()) return

        // 1. Rapid financial keyword pre-filter (<0.1ms)
        if (!isLikelyFinancialMessage(fullBody)) {
            return
        }

        // 2. Run BankAiEngine in background coroutine
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val parsed = aiEngine.parse(fullBody)
                if (parsed.amount > 0.0) {
                    notificationManager.showDetectedTransactionNotification(parsed)
                    Timber.d("Detected financial SMS: ${parsed.currency} ${parsed.amount} at ${parsed.merchant}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error parsing incoming bank SMS")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun isLikelyFinancialMessage(text: String): Boolean {
        val lower = text.lowercase()
        val financialKeywords = listOf(
            "debited", "credited", "paid", "spent", "withdrawn", "txn", "transaction",
            "a/c", "acct", "card", "fonepay", "upi", "pos", "charge", "alert", "salary",
            "npr", "rs.", "usd", "eur", "inr", "bal"
        )
        return financialKeywords.any { lower.contains(it) }
    }
}
