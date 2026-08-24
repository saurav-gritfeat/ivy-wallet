package com.ivy.ai.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ivy.ai.model.BankFewShotTemplate
import com.ivy.base.model.TransactionType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.aiDataStore by preferencesDataStore(name = "ivy_ai_bank_templates")

@Singleton
class BankTemplateRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val templatesKey = stringPreferencesKey("custom_few_shot_templates")

    private val defaultTemplates = listOf(
        BankFewShotTemplate(
            bankName = "Fonepay / QR Merchant",
            sampleMessage = "Your A/C *1234 debited by NPR 450.00 on 2026-08-20 for payment to HIMALAYAN JAVA COFFEE via Fonepay. Ref: 981245.",
            expectedAmount = 450.0,
            expectedCurrency = "NPR",
            expectedType = TransactionType.EXPENSE,
            expectedMerchant = "Himalayan Java Coffee",
            expectedCategory = "Food & Drinks"
        ),
        BankFewShotTemplate(
            bankName = "Nabil / Global Bank Debit",
            sampleMessage = "Dear Customer, NPR 2,500.00 was debited from your A/C ending 5678 at BHATBHATENI SUPERMARKET on 24-Aug. Avl Bal: NPR 45,200.",
            expectedAmount = 2500.0,
            expectedCurrency = "NPR",
            expectedType = TransactionType.EXPENSE,
            expectedMerchant = "Bhatbhateni Supermarket",
            expectedCategory = "Groceries"
        ),
        BankFewShotTemplate(
            bankName = "Chase / US Card Alert",
            sampleMessage = "Chase Alert: Your card ending in 4921 was charged $38.75 at UBER EATS on Aug 24. Reply HELP for info.",
            expectedAmount = 38.75,
            expectedCurrency = "USD",
            expectedType = TransactionType.EXPENSE,
            expectedMerchant = "Uber Eats",
            expectedCategory = "Food & Drinks"
        ),
        BankFewShotTemplate(
            bankName = "Salary / Direct Deposit",
            sampleMessage = "Your Account *9901 has been CREDITED by NPR 85,000.00 on 24/08/2026 for SALARY AUGUST from ACME CORP. Ref: SAL778.",
            expectedAmount = 85000.0,
            expectedCurrency = "NPR",
            expectedType = TransactionType.INCOME,
            expectedMerchant = "Acme Corp (Salary)",
            expectedCategory = "Income"
        ),
        BankFewShotTemplate(
            bankName = "Apple Pay / Revolut",
            sampleMessage = "Revolut: You spent €14.99 at NETFLIX.COM. Remaining balance: €320.50.",
            expectedAmount = 14.99,
            expectedCurrency = "EUR",
            expectedType = TransactionType.EXPENSE,
            expectedMerchant = "Netflix",
            expectedCategory = "Entertainment"
        )
    )

    fun getTemplates(): Flow<List<BankFewShotTemplate>> {
        return context.aiDataStore.data.map { prefs ->
            val jsonString = prefs[templatesKey]
            if (jsonString.isNullOrBlank()) {
                defaultTemplates
            } else {
                try {
                    val list = mutableListOf<BankFewShotTemplate>()
                    val array = JSONArray(jsonString)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        list.add(
                            BankFewShotTemplate(
                                id = obj.optString("id"),
                                bankName = obj.getString("bankName"),
                                sampleMessage = obj.getString("sampleMessage"),
                                expectedAmount = obj.getDouble("expectedAmount"),
                                expectedCurrency = obj.getString("expectedCurrency"),
                                expectedType = if (obj.getString("expectedType") == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE,
                                expectedMerchant = obj.getString("expectedMerchant"),
                                expectedCategory = obj.getString("expectedCategory")
                            )
                        )
                    }
                    if (list.isEmpty()) defaultTemplates else list
                } catch (e: Exception) {
                    defaultTemplates
                }
            }
        }
    }

    suspend fun saveTemplate(template: BankFewShotTemplate) {
        context.aiDataStore.edit { prefs ->
            val currentList = getTemplatesDirect(prefs[templatesKey]).toMutableList()
            // Replace if existing, or prepend
            val index = currentList.indexOfFirst { it.id == template.id }
            if (index >= 0) {
                currentList[index] = template
            } else {
                currentList.add(0, template)
            }
            prefs[templatesKey] = serializeTemplates(currentList)
        }
    }

    suspend fun deleteTemplate(templateId: String) {
        context.aiDataStore.edit { prefs ->
            val currentList = getTemplatesDirect(prefs[templatesKey]).filter { it.id != templateId }
            prefs[templatesKey] = serializeTemplates(currentList)
        }
    }

    suspend fun resetToDefaults() {
        context.aiDataStore.edit { prefs ->
            prefs[templatesKey] = serializeTemplates(defaultTemplates)
        }
    }

    private fun getTemplatesDirect(jsonString: String?): List<BankFewShotTemplate> {
        if (jsonString.isNullOrBlank()) return defaultTemplates
        return try {
            val list = mutableListOf<BankFewShotTemplate>()
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    BankFewShotTemplate(
                        id = obj.optString("id"),
                        bankName = obj.getString("bankName"),
                        sampleMessage = obj.getString("sampleMessage"),
                        expectedAmount = obj.getDouble("expectedAmount"),
                        expectedCurrency = obj.getString("expectedCurrency"),
                        expectedType = if (obj.getString("expectedType") == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE,
                        expectedMerchant = obj.getString("expectedMerchant"),
                        expectedCategory = obj.getString("expectedCategory")
                    )
                )
            }
            if (list.isEmpty()) defaultTemplates else list
        } catch (e: Exception) {
            defaultTemplates
        }
    }

    private fun serializeTemplates(templates: List<BankFewShotTemplate>): String {
        val array = JSONArray()
        templates.forEach {
            val obj = JSONObject()
            obj.put("id", it.id)
            obj.put("bankName", it.bankName)
            obj.put("sampleMessage", it.sampleMessage)
            obj.put("expectedAmount", it.expectedAmount)
            obj.put("expectedCurrency", it.expectedCurrency)
            obj.put("expectedType", it.expectedType.name)
            obj.put("expectedMerchant", it.expectedMerchant)
            obj.put("expectedCategory", it.expectedCategory)
            array.put(obj)
        }
        return array.toString()
    }
}
