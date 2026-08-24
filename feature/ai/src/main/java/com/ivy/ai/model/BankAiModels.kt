package com.ivy.ai.model

import com.ivy.base.model.TransactionType
import java.util.UUID

data class BankParsedTransaction(
    val amount: Double = 0.0,
    val currency: String = "USD",
    val type: TransactionType = TransactionType.EXPENSE,
    val merchant: String = "",
    val categoryName: String = "Uncategorized",
    val accountIdentifier: String? = null,
    val rawText: String = "",
    val confidence: Float = 0.95f
)

data class BankFewShotTemplate(
    val id: String = UUID.randomUUID().toString(),
    val bankName: String,
    val sampleMessage: String,
    val expectedAmount: Double,
    val expectedCurrency: String,
    val expectedType: TransactionType,
    val expectedMerchant: String,
    val expectedCategory: String
)
