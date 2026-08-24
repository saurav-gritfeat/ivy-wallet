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

import com.ivy.ai.data.BankSenderRuleRepository
import kotlinx.coroutines.flow.first

@AndroidEntryPoint
class BankSmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var aiEngine: BankAiEngine

    @Inject
    lateinit var notificationManager: BankNotificationManager

    @Inject
    lateinit var senderRuleRepository: BankSenderRuleRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages.firstOrNull()?.originatingAddress ?: ""
        val fullBody = messages.joinToString(" ") { it.messageBody ?: "" }.trim()
        if (fullBody.isBlank()) return

        // 1. Rapid financial keyword pre-filter (<0.1ms)
        if (!isLikelyFinancialMessage(fullBody)) {
            return
        }

        // 2. Run BankAiEngine & Sender Rule matching in background coroutine
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Check Sender Rules (Blacklist / Whitelist)
                val rules = senderRuleRepository.getRules().first()
                val matchedRule = rules.firstOrNull { rule ->
                    sender.contains(rule.senderPattern, ignoreCase = true) ||
                        fullBody.contains(rule.senderPattern, ignoreCase = true)
                }

                // If sender is blacklisted, drop immediately
                if (matchedRule != null && matchedRule.isBlacklisted) {
                    Timber.d("Ignoring SMS from blacklisted sender: $sender")
                    pendingResult.finish()
                    return@launch
                }

                val parsed = aiEngine.parse(fullBody)
                if (parsed.amount > 0.0) {
                    val targetAccountId = matchedRule?.mappedAccountId
                    notificationManager.showDetectedTransactionNotification(
                        parsed = parsed,
                        mappedAccountId = targetAccountId
                    )
                    Timber.d("Detected financial SMS from '$sender': ${parsed.currency} ${parsed.amount} (Mapped Account: $targetAccountId)")
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
