package com.ivy.ai.engine

import com.ivy.ai.data.BankTemplateRepository
import com.ivy.ai.model.BankFewShotTemplate
import com.ivy.ai.model.BankParsedTransaction
import com.ivy.base.model.TransactionType
import com.ivy.data.repository.CategoryRepository
import kotlinx.coroutines.flow.first
import java.util.Locale
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BankAiEngine @Inject constructor(
    private val templateRepository: BankTemplateRepository,
    private val categoryRepository: CategoryRepository
) {

    /**
     * Builds the complete prompt for On-Device SLM (e.g. MediaPipe / Gemma / Qwen)
     * containing dynamic active categories and user's few-shot bank templates.
     */
    suspend fun buildPrompt(rawMessage: String): String {
        val categories = categoryRepository.findAll().map { it.name.value }
        val categoryListStr = if (categories.isNotEmpty()) {
            categories.joinToString(", ")
        } else {
            "Food & Dining, Groceries, Shopping, Entertainment, Utilities, Transport, Health, Salary, Transfer"
        }

        val templates = templateRepository.getTemplates().first()

        val promptBuilder = StringBuilder()
        promptBuilder.append("You are a financial assistant. Extract transaction details from the given bank message and output ONLY valid JSON without markdown formatting.\n\n")
        promptBuilder.append("Available Categories: [$categoryListStr]\n\n")

        promptBuilder.append("Few-shot examples:\n")
        templates.take(4).forEach { template ->
            promptBuilder.append("- Message: \"${template.sampleMessage}\"\n")
            promptBuilder.append("  Output: {\"amount\": ${template.expectedAmount}, \"currency\": \"${template.expectedCurrency}\", \"type\": \"${template.expectedType.name}\", \"merchant\": \"${template.expectedMerchant}\", \"category\": \"${template.expectedCategory}\"}\n")
        }

        promptBuilder.append("\nInput Message:\n\"$rawMessage\"\n\n")
        promptBuilder.append("Output strictly valid JSON with keys: amount (number), currency (string), type (EXPENSE or INCOME), merchant (string), category (string):")

        return promptBuilder.toString()
    }

    /**
     * Parses the incoming bank text. First attempts exact pattern/NLP extraction,
     * matching against few-shot templates and active categories.
     */
    suspend fun parse(rawMessage: String): BankParsedTransaction {
        val cleanText = rawMessage.trim()
        if (cleanText.isBlank()) {
            return BankParsedTransaction(rawText = rawMessage)
        }

        val categories = categoryRepository.findAll().map { it.name.value }
        val templates = templateRepository.getTemplates().first()

        // Check if matching any custom user template pattern
        templates.firstOrNull { cleanText.contains(it.bankName, ignoreCase = true) }

        // 1. Detect Transaction Type (Expense vs Income)
        val isIncome = containsAny(cleanText, "credited", "received", "deposited", "salary", "refund", "added to a/c", "credit of")
        val type = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE

        // 2. Extract Amount & Currency
        val (amount, currency) = extractAmountAndCurrency(cleanText)

        // 3. Extract Merchant / Entity
        val merchant = extractMerchant(cleanText, type)

        // 4. Auto-categorize based on merchant & context
        val category = guessCategory(cleanText, merchant, categories)

        // 5. Extract Card/Account last digits if present
        val account = extractAccountDigits(cleanText)

        return BankParsedTransaction(
            amount = amount,
            currency = currency,
            type = type,
            merchant = merchant,
            categoryName = category,
            accountIdentifier = account,
            rawText = rawMessage,
            confidence = if (amount > 0.0) 0.95f else 0.50f
        )
    }

    private fun extractAmountAndCurrency(text: String): Pair<Double, String> {
        // Match patterns like: NPR 1,500.00, Rs. 500, $45.99, USD 100, EUR 12.50, ₹ 499
        val amountPattern = Pattern.compile(
            """(?i)(?:(NPR|Rs\.?|INR|USD|\$|EUR|€|GBP|£|AUD|CAD)\s*([0-9]+(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)|([0-9]+(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)\s*(NPR|Rs\.?|INR|USD|\$|EUR|€|GBP|£|AUD|CAD))"""
        )
        val matcher = amountPattern.matcher(text)

        if (matcher.find()) {
            val curr1 = matcher.group(1)
            val amt1 = matcher.group(2)
            val amt2 = matcher.group(3)
            val curr2 = matcher.group(4)

            val rawCurr = (curr1 ?: curr2 ?: "USD").trim()
            val rawAmt = (amt1 ?: amt2 ?: "0").replace(",", "").trim()

            val normalizedCurrency = when (rawCurr.uppercase(Locale.ROOT)) {
                "$", "USD" -> "USD"
                "NPR", "RS.", "RS" -> "NPR"
                "INR", "₹" -> "INR"
                "EUR", "€" -> "EUR"
                "GBP", "£" -> "GBP"
                else -> rawCurr
            }

            val parsedAmount = rawAmt.toDoubleOrNull() ?: 0.0
            return Pair(parsedAmount, normalizedCurrency)
        }

        // Generic number extraction fallback
        val genericNumber = Pattern.compile("""(?i)(?:debited|credited|spent|paid|amount|txn|charge)[\s:]*(?:of\s*)?([0-9]+(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)""")
        val numMatcher = genericNumber.matcher(text)
        if (numMatcher.find()) {
            val amt = numMatcher.group(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
            return Pair(amt, "USD")
        }

        return Pair(0.0, "USD")
    }

    private fun extractMerchant(text: String, type: TransactionType): String {
        // Look for: "at MERCHANT", "to MERCHANT", "for payment to MERCHANT", "towards MERCHANT", "from SENDER"
        val merchantPattern = Pattern.compile(
            """(?i)(?:at|to|towards|for payment to|paid to|info:|from)\s+([A-Za-z0-9\s&'-]{3,35}?)(?:\s+on|\s+via|\s+ref|\s+avl|\s+bal|\.|\band\b|$)"""
        )
        val matcher = merchantPattern.matcher(text)
        if (matcher.find()) {
            val candidate = matcher.group(1)?.trim()?.replace(Regex("""(?i)\b(via|ref|bal|on|the|a|ac|acc|a/c)\b.*"""), "")?.trim()
            if (!candidate.isNullOrBlank() && candidate.length in 3..35 && !candidate.equals("your", true)) {
                return formatMerchantName(candidate)
            }
        }

        return if (type == TransactionType.EXPENSE) "Card / POS Payment" else "Bank Transfer"
    }

    private fun guessCategory(text: String, merchant: String, activeCategories: List<String>): String {
        val combined = "$text $merchant".lowercase(Locale.ROOT)

        val foodKeywords = listOf("restaurant", "cafe", "coffee", "java", "bakery", "mcdonald", "burger", "pizza", "swiggy", "zomato", "ubereats", "doordash", "food", "dining", "bar", "pub")
        val groceryKeywords = listOf("bhatbhateni", "supermarket", "mart", "grocery", "groceries", "walmart", "target", "costco", "fresh", "store", "market")
        val transportKeywords = listOf("uber", "lyft", "pathao", "indrive", "taxi", "petrol", "gas", "fuel", "airlines", "flight", "railway", "transit", "metro")
        val shoppingKeywords = listOf("amazon", "daraz", "flipkart", "ebay", "clothing", "apparel", "zara", "h&m", "retail", "mall")
        val utilityKeywords = listOf("electricity", "water", "internet", "nea", "wifi", "telecom", "mobile", "recharge", "bill", "subscription", "netflix", "spotify")
        val salaryKeywords = listOf("salary", "payroll", "dividend", "bonus", "interest", "stipend")

        val targetCategory = when {
            foodKeywords.any { combined.contains(it) } -> "Food & Drinks"
            groceryKeywords.any { combined.contains(it) } -> "Groceries"
            transportKeywords.any { combined.contains(it) } -> "Transport"
            shoppingKeywords.any { combined.contains(it) } -> "Shopping"
            utilityKeywords.any { combined.contains(it) } -> "Utilities"
            salaryKeywords.any { combined.contains(it) } -> "Income"
            else -> null
        }

        if (targetCategory != null) {
            val matchedActive = activeCategories.firstOrNull { it.contains(targetCategory, ignoreCase = true) || targetCategory.contains(it, ignoreCase = true) }
            if (matchedActive != null) return matchedActive
            return targetCategory
        }

        return activeCategories.firstOrNull() ?: "General"
    }

    private fun extractAccountDigits(text: String): String? {
        val accPattern = Pattern.compile("""(?i)(?:a/c|ac|account|card|ending|ending in)\s*[*xX]*([0-9]{3,4})""")
        val matcher = accPattern.matcher(text)
        if (matcher.find()) {
            return "A/C *" + matcher.group(1)
        }
        return null
    }

    private fun formatMerchantName(name: String): String {
        return name.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.lowercase(Locale.ROOT).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return keywords.any { lower.contains(it) }
    }
}
