package com.ivy.ai.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ivy.ai.data.ProcessedMessageTracker
import com.ivy.base.model.TransactionType
import com.ivy.data.model.AccountId
import com.ivy.data.model.CategoryId
import com.ivy.data.model.Expense
import com.ivy.data.model.Income
import com.ivy.data.model.PositiveValue
import com.ivy.data.model.TransactionId
import com.ivy.data.model.TransactionMetadata
import com.ivy.data.model.primitive.AssetCode
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.model.primitive.PositiveDouble
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.CategoryRepository
import com.ivy.data.repository.TransactionRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var transactionRepository: TransactionRepository

    @Inject
    lateinit var accountRepository: AccountRepository

    @Inject
    lateinit var categoryRepository: CategoryRepository

    @Inject
    lateinit var dedupeTracker: ProcessedMessageTracker

    @Inject
    lateinit var notificationManager: BankNotificationManager

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val notifId = intent.getIntExtra(BankNotificationManager.EXTRA_NOTIF_ID, 0)

        if (action == BankNotificationManager.ACTION_DISMISS) {
            notificationManager.dismissNotification(notifId)
            return
        }

        if (action == BankNotificationManager.ACTION_QUICK_ADD) {
            val amount = intent.getDoubleExtra(BankNotificationManager.EXTRA_AMOUNT, 0.0)
            val currency = intent.getStringExtra(BankNotificationManager.EXTRA_CURRENCY) ?: "USD"
            val typeStr = intent.getStringExtra(BankNotificationManager.EXTRA_TYPE) ?: "EXPENSE"
            val merchant = intent.getStringExtra(BankNotificationManager.EXTRA_MERCHANT) ?: "Bank Transaction"
            val categoryName = intent.getStringExtra(BankNotificationManager.EXTRA_CATEGORY) ?: "General"
            val rawText = intent.getStringExtra(BankNotificationManager.EXTRA_RAW_TEXT) ?: ""
            val mappedAccIdStr = intent.getStringExtra(BankNotificationManager.EXTRA_MAPPED_ACCOUNT_ID)

            if (amount <= 0.0) {
                notificationManager.dismissNotification(notifId)
                return
            }

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val accounts = accountRepository.findAll()
                    val targetAccount = mappedAccIdStr?.let { idStr ->
                        try {
                            val uuid = java.util.UUID.fromString(idStr)
                            accounts.firstOrNull { it.id.value == uuid }
                        } catch (e: Exception) { null }
                    } ?: accounts.firstOrNull() ?: run {
                        Timber.w("No account found to assign detected transaction")
                        pendingResult.finish()
                        return@launch
                    }

                    val categories = categoryRepository.findAll()
                    val targetCategory = categories.firstOrNull {
                        it.name.value.contains(categoryName, ignoreCase = true) ||
                            categoryName.contains(it.name.value, ignoreCase = true)
                    }

                    val titleStr = NotBlankTrimmedString.unsafe(merchant.ifBlank { "Bank Transaction" })
                    val descStr = NotBlankTrimmedString.unsafe(rawText.take(120))
                    val positiveDouble = PositiveDouble.unsafe(amount)
                    val assetCode = AssetCode.unsafe(currency.ifBlank { "USD" })
                    val positiveValue = PositiveValue(amount = positiveDouble, asset = assetCode)
                    val metadata = TransactionMetadata(
                        recurringRuleId = null,
                        paidForDateTime = null,
                        loanRecordId = null
                    )

                    val isIncome = typeStr.equals("INCOME", ignoreCase = true)
                    val transaction = if (isIncome) {
                        Income(
                            id = TransactionId(UUID.randomUUID()),
                            title = titleStr,
                            description = descStr,
                            category = targetCategory?.id?.let { CategoryId(it.value) },
                            time = Instant.now(),
                            settled = true,
                            metadata = metadata,
                            tags = emptyList(),
                            value = positiveValue,
                            account = AccountId(targetAccount.id.value)
                        )
                    } else {
                        Expense(
                            id = TransactionId(UUID.randomUUID()),
                            title = titleStr,
                            description = descStr,
                            category = targetCategory?.id?.let { CategoryId(it.value) },
                            time = Instant.now(),
                            settled = true,
                            metadata = metadata,
                            tags = emptyList(),
                            value = positiveValue,
                            account = AccountId(targetAccount.id.value)
                        )
                    }

                    transactionRepository.save(transaction)
                    dedupeTracker.recordProcessedMessage(
                        rawText = rawText,
                        summary = "$merchant • $currency $amount",
                        amount = amount,
                        currency = currency
                    )

                    notificationManager.showAddedSuccessNotification(
                        notifId = notifId,
                        summary = "$currency ${"%,.2f".format(amount)} at $merchant ($categoryName)"
                    )
                    Timber.d("1-Tap added transaction successfully from notification!")
                } catch (e: Exception) {
                    Timber.e(e, "Error saving transaction from notification action")
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
