package com.ivy.ai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.ai.data.BankSenderRule
import com.ivy.ai.data.BankSenderRuleRepository
import com.ivy.ai.data.BankTemplateRepository
import com.ivy.ai.downloader.DownloadProgress
import com.ivy.ai.downloader.DownloadableModel
import com.ivy.ai.downloader.ModelDownloadManager
import com.ivy.ai.engine.BankAiEngine
import com.ivy.ai.model.BankFewShotTemplate
import com.ivy.ai.model.BankParsedTransaction
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class AccountItem(
    val id: UUID,
    val name: String,
    val currency: String
)

data class AiBankParserUiState(
    val inputText: String = "",
    val isAnalyzing: Boolean = false,
    val parsedResult: BankParsedTransaction? = null,
    val statusMessage: String? = null,
    val isSuccess: Boolean = false,
    val isSlmLoaded: Boolean = false,
    val loadedModelName: String? = null,
    val availableModels: List<String> = emptyList(),
    val accounts: List<AccountItem> = emptyList(),
    val selectedAccountId: UUID? = null
)

@HiltViewModel
class AiBankParserViewModel @Inject constructor(
    private val aiEngine: BankAiEngine,
    private val templateRepository: BankTemplateRepository,
    private val senderRuleRepository: BankSenderRuleRepository,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    val downloadManager: ModelDownloadManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiBankParserUiState())
    val uiState: StateFlow<AiBankParserUiState> = _uiState.asStateFlow()

    val downloadProgress: StateFlow<DownloadProgress> = downloadManager.downloadProgress

    val templates: StateFlow<List<BankFewShotTemplate>> = templateRepository.getTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val senderRules: StateFlow<List<BankSenderRule>> = senderRuleRepository.getRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val modelCatalog: List<DownloadableModel> = downloadManager.availableCatalog

    fun saveSenderRule(senderPattern: String, isBlacklisted: Boolean, mappedAccountId: UUID?) {
        viewModelScope.launch {
            val accountName = _uiState.value.accounts.firstOrNull { it.id == mappedAccountId }?.name
            val rule = BankSenderRule(
                senderPattern = senderPattern.trim().uppercase(),
                isBlacklisted = isBlacklisted,
                mappedAccountId = mappedAccountId,
                mappedAccountName = accountName
            )
            senderRuleRepository.saveRule(rule)
            _uiState.update { it.copy(statusMessage = "Saved rule for sender '$senderPattern'!") }
        }
    }

    fun deleteSenderRule(ruleId: String) {
        viewModelScope.launch {
            senderRuleRepository.deleteRule(ruleId)
            _uiState.update { it.copy(statusMessage = "Removed sender rule") }
        }
    }

    fun startDownload(model: DownloadableModel) {
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = "Starting download for ${model.name}...") }
            val res = downloadManager.downloadModel(model)
            if (res.isSuccess) {
                refreshModelStatus()
                // Auto load newly downloaded model
                loadModel(model.fileName)
            } else {
                _uiState.update { it.copy(statusMessage = "Download failed: ${res.exceptionOrNull()?.message}") }
            }
        }
    }

    init {
        refreshModelStatus()
        loadAccounts()
    }

    private fun loadAccounts() {
        viewModelScope.launch {
            val accs = accountRepository.findAll().map {
                AccountItem(id = it.id.value, name = it.name.value, currency = it.asset.code)
            }
            _uiState.update {
                it.copy(
                    accounts = accs,
                    selectedAccountId = it.selectedAccountId ?: accs.firstOrNull()?.id
                )
            }
        }
    }

    fun onAccountSelected(id: UUID) {
        _uiState.update { it.copy(selectedAccountId = id) }
    }

    fun refreshModelStatus() {
        val models = aiEngine.mediaPipeLlmEngine.findAvailableModels().map { it.name }
        _uiState.update {
            it.copy(
                isSlmLoaded = aiEngine.mediaPipeLlmEngine.isModelLoaded(),
                loadedModelName = aiEngine.mediaPipeLlmEngine.getLoadedModelName(),
                availableModels = models
            )
        }
    }

    fun loadModel(modelFileName: String) {
        viewModelScope.launch {
            val file = java.io.File(aiEngine.mediaPipeLlmEngine.defaultModelDir, modelFileName)
            _uiState.update { it.copy(statusMessage = "Loading SLM model into GPU/CPU memory...") }
            val res = aiEngine.mediaPipeLlmEngine.loadModel(file.absolutePath)
            if (res.isSuccess) {
                refreshModelStatus()
                _uiState.update { it.copy(statusMessage = "Loaded model '$modelFileName' successfully!") }
            } else {
                _uiState.update { it.copy(statusMessage = "Failed to load model: ${res.exceptionOrNull()?.message}") }
            }
        }
    }

    fun onInputTextChanged(newText: String) {
        _uiState.update { it.copy(inputText = newText, statusMessage = null) }
    }

    fun analyzeInput() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank()) {
            _uiState.update { it.copy(statusMessage = "Please paste or type a bank message first") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true, statusMessage = null) }
            val result = aiEngine.parse(text)

            // Auto-match best account
            val currentAccounts = _uiState.value.accounts
            val matchedAccount = currentAccounts.firstOrNull { acc ->
                val accIdDigits = result.accountIdentifier?.replace(Regex("""[^0-9]"""), "") ?: ""
                val accNameLower = acc.name.lowercase()
                (accIdDigits.isNotEmpty() && accNameLower.contains(accIdDigits)) ||
                    (result.merchant.isNotEmpty() && accNameLower.contains(result.merchant.lowercase())) ||
                    (result.rawText.contains(acc.name, ignoreCase = true))
            } ?: currentAccounts.firstOrNull()

            _uiState.update {
                it.copy(
                    isAnalyzing = false,
                    parsedResult = result,
                    selectedAccountId = matchedAccount?.id ?: it.selectedAccountId,
                    statusMessage = "Transaction details extracted successfully!"
                )
            }
        }
    }

    fun loadSampleTemplate(template: BankFewShotTemplate) {
        _uiState.update {
            it.copy(
                inputText = template.sampleMessage,
                statusMessage = "Loaded sample for ${template.bankName}"
            )
        }
        analyzeInput()
    }

    fun saveAsTransaction() {
        val result = _uiState.value.parsedResult ?: return
        if (result.amount <= 0.0) {
            _uiState.update { it.copy(statusMessage = "Invalid amount: ${result.amount}") }
            return
        }

        viewModelScope.launch {
            try {
                // Find account or fallback to selected/first account
                val accounts = accountRepository.findAll()
                val targetAccount = accounts.firstOrNull { it.id.value == _uiState.value.selectedAccountId }
                    ?: accounts.firstOrNull() ?: run {
                        _uiState.update { it.copy(statusMessage = "Please create an Account in Ivy Wallet first!") }
                        return@launch
                    }

                // Find category or fallback
                val categories = categoryRepository.findAll()
                val targetCategory = categories.firstOrNull {
                    it.name.value.contains(result.categoryName, ignoreCase = true) ||
                        result.categoryName.contains(it.name.value, ignoreCase = true)
                }

                val titleStr = NotBlankTrimmedString.unsafe(result.merchant.ifBlank { "Bank Transaction" })
                val descStr = NotBlankTrimmedString.unsafe(result.rawText.take(120))
                val positiveDouble = PositiveDouble.unsafe(result.amount)
                val assetCode = AssetCode.unsafe(result.currency.ifBlank { "USD" })
                val positiveValue = PositiveValue(amount = positiveDouble, asset = assetCode)
                val metadata = TransactionMetadata(
                    recurringRuleId = null,
                    paidForDateTime = null,
                    loanRecordId = null
                )

                val transaction = if (result.type == TransactionType.INCOME) {
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
                _uiState.update {
                    it.copy(
                        statusMessage = "Saved ${result.type.name.lowercase()} of ${result.currency} ${result.amount} to Ivy Wallet!",
                        isSuccess = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(statusMessage = "Error saving transaction: ${e.message}") }
            }
        }
    }

    fun saveCustomFewShotRule(bankName: String) {
        val result = _uiState.value.parsedResult ?: return
        val raw = _uiState.value.inputText.trim()
        if (raw.isBlank() || bankName.isBlank()) return

        viewModelScope.launch {
            val template = BankFewShotTemplate(
                id = UUID.randomUUID().toString(),
                bankName = bankName.trim(),
                sampleMessage = raw,
                expectedAmount = result.amount,
                expectedCurrency = result.currency,
                expectedType = result.type,
                expectedMerchant = result.merchant,
                expectedCategory = result.categoryName
            )
            templateRepository.saveTemplate(template)
            _uiState.update {
                it.copy(statusMessage = "Taught AI rule for '$bankName'! Future messages will match this format.")
            }
        }
    }

    fun deleteTemplate(id: String) {
        viewModelScope.launch {
            templateRepository.deleteTemplate(id)
            _uiState.update { it.copy(statusMessage = "Removed template rule") }
        }
    }
}
