package com.ivy.ai.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dedupeDataStore by preferencesDataStore(name = "ivy_ai_processed_messages")

data class ProcessedMessageRecord(
    val hash: String,
    val summary: String,
    val amount: Double,
    val currency: String,
    val timestampEpochMs: Long = System.currentTimeMillis()
)

@Singleton
class ProcessedMessageTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val recordsKey = stringPreferencesKey("processed_records_json")

    /**
     * Generates a deterministic hash from a message string to identify duplicates
     */
    fun computeMessageHash(rawText: String): String {
        val normalized = rawText.lowercase().replace(Regex("""\s+"""), " ").trim()
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(normalized.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun isAlreadyProcessed(rawText: String): Boolean {
        val hash = computeMessageHash(rawText)
        val records = getRecords().first()
        return records.any { it.hash == hash }
    }

    fun getRecords(): Flow<List<ProcessedMessageRecord>> {
        return context.dedupeDataStore.data.map { prefs ->
            val jsonString = prefs[recordsKey] ?: return@map emptyList()
            try {
                val list = mutableListOf<ProcessedMessageRecord>()
                val array = JSONArray(jsonString)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        ProcessedMessageRecord(
                            hash = obj.getString("hash"),
                            summary = obj.getString("summary"),
                            amount = obj.getDouble("amount"),
                            currency = obj.getString("currency"),
                            timestampEpochMs = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
                list
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun recordProcessedMessage(rawText: String, summary: String, amount: Double, currency: String) {
        val hash = computeMessageHash(rawText)
        context.dedupeDataStore.edit { prefs ->
            val currentList = parseRecords(prefs[recordsKey]).toMutableList()
            // Keep up to latest 200 message hashes
            currentList.removeAll { it.hash == hash }
            currentList.add(
                0,
                ProcessedMessageRecord(
                    hash = hash,
                    summary = summary,
                    amount = amount,
                    currency = currency,
                    timestampEpochMs = System.currentTimeMillis()
                )
            )
            val trimmed = currentList.take(200)

            val array = JSONArray()
            trimmed.forEach {
                val obj = JSONObject()
                obj.put("hash", it.hash)
                obj.put("summary", it.summary)
                obj.put("amount", it.amount)
                obj.put("currency", it.currency)
                obj.put("timestamp", it.timestampEpochMs)
                array.put(obj)
            }
            prefs[recordsKey] = array.toString()
        }
    }

    suspend fun clearHistory() {
        context.dedupeDataStore.edit { it.remove(recordsKey) }
    }

    private fun parseRecords(jsonString: String?): List<ProcessedMessageRecord> {
        if (jsonString.isNullOrBlank()) return emptyList()
        return try {
            val list = mutableListOf<ProcessedMessageRecord>()
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ProcessedMessageRecord(
                        hash = obj.getString("hash"),
                        summary = obj.getString("summary"),
                        amount = obj.getDouble("amount"),
                        currency = obj.getString("currency"),
                        timestampEpochMs = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
}
