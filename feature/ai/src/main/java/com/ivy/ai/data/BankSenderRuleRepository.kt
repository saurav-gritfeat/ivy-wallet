package com.ivy.ai.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.senderRulesDataStore by preferencesDataStore(name = "ivy_ai_sender_rules")

data class BankSenderRule(
    val id: String = UUID.randomUUID().toString(),
    val senderPattern: String, // e.g. "NABIL", "FONEPAY", "CHASE", "GLOBAL"
    val isBlacklisted: Boolean = false,
    val mappedAccountId: UUID? = null,
    val mappedAccountName: String? = null
)

@Singleton
class BankSenderRuleRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val rulesKey = stringPreferencesKey("sender_rules_json")

    private val defaultRules = listOf(
        BankSenderRule(
            senderPattern = "NABIL",
            isBlacklisted = false,
            mappedAccountName = "Nabil Bank"
        ),
        BankSenderRule(
            senderPattern = "FONEPAY",
            isBlacklisted = false,
            mappedAccountName = "Fonepay / Wallet"
        ),
        BankSenderRule(
            senderPattern = "CHASE",
            isBlacklisted = false,
            mappedAccountName = "Chase Card"
        ),
        BankSenderRule(
            senderPattern = "PROMO",
            isBlacklisted = true,
            mappedAccountName = null
        )
    )

    fun getRules(): Flow<List<BankSenderRule>> {
        return context.senderRulesDataStore.data.map { prefs ->
            val jsonString = prefs[rulesKey]
            if (jsonString.isNullOrBlank()) {
                defaultRules
            } else {
                parseRules(jsonString)
            }
        }
    }

    suspend fun saveRule(rule: BankSenderRule) {
        context.senderRulesDataStore.edit { prefs ->
            val currentList = parseRules(prefs[rulesKey]).toMutableList()
            val index = currentList.indexOfFirst { it.id == rule.id || it.senderPattern.equals(rule.senderPattern, ignoreCase = true) }
            if (index >= 0) {
                currentList[index] = rule
            } else {
                currentList.add(0, rule)
            }
            prefs[rulesKey] = serializeRules(currentList)
        }
    }

    suspend fun deleteRule(ruleId: String) {
        context.senderRulesDataStore.edit { prefs ->
            val currentList = parseRules(prefs[rulesKey]).filter { it.id != ruleId }
            prefs[rulesKey] = serializeRules(currentList)
        }
    }

    private fun parseRules(jsonString: String?): List<BankSenderRule> {
        if (jsonString.isNullOrBlank()) return defaultRules
        return try {
            val list = mutableListOf<BankSenderRule>()
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    BankSenderRule(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        senderPattern = obj.getString("senderPattern"),
                        isBlacklisted = obj.optBoolean("isBlacklisted", false),
                        mappedAccountId = if (obj.has("mappedAccountId") && !obj.isNull("mappedAccountId")) {
                            try { UUID.fromString(obj.getString("mappedAccountId")) } catch (e: Exception) { null }
                        } else null,
                        mappedAccountName = if (obj.has("mappedAccountName") && !obj.isNull("mappedAccountName")) obj.getString("mappedAccountName") else null
                    )
                )
            }
            if (list.isEmpty()) defaultRules else list
        } catch (e: Exception) {
            defaultRules
        }
    }

    private fun serializeRules(rules: List<BankSenderRule>): String {
        val array = JSONArray()
        rules.forEach {
            val obj = JSONObject()
            obj.put("id", it.id)
            obj.put("senderPattern", it.senderPattern)
            obj.put("isBlacklisted", it.isBlacklisted)
            obj.put("mappedAccountId", it.mappedAccountId?.toString())
            obj.put("mappedAccountName", it.mappedAccountName)
            array.put(obj)
        }
        return array.toString()
    }
}
