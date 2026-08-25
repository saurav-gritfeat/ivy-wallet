package com.ivy.ai.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.component.BackButton
import com.ivy.base.model.TransactionType
import com.ivy.ai.model.BankFewShotTemplate

@Composable
fun AiBankParserScreen() {
    val viewModel: AiBankParserViewModel = screenScopedViewModel()
    val nav = navigation()
    val uiState by viewModel.uiState.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    var teachBankName by remember { mutableStateOf("") }
    var showTeachDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Top Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                BackButton(onClick = { nav.back() })
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "AI Bank Message Lab",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF6200EE).copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "On-Device",
                                color = Color(0xFF9D46FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Text(
                        text = "Auto-extract transactions from bank SMS & push alerts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // SLM Model Status & Persistent Storage Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.isSlmLoaded) Color(0xFF6200EE).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (uiState.isSlmLoaded) Color(0xFF00E676) else Color(0xFFFFB300))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (uiState.isSlmLoaded) "SLM Active: ${uiState.loadedModelName}" else "MediaPipe SLM: Standby",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (uiState.isSlmLoaded) Color(0xFF6200EE) else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        TextButton(onClick = { viewModel.refreshModelStatus() }) {
                            Text("Scan Storage", fontSize = 12.sp)
                        }
                    }

                    if (uiState.availableModels.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Installed models in Download/IvyWallet/models/:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        uiState.availableModels.forEach { modelName ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(modelName, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Button(
                                    onClick = { viewModel.loadModel(modelName) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE))
                                ) {
                                    Text("Load into Memory", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "1-Tap Download SLM Models (Direct to persistent storage):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Download Progress Bar
                    val downloadProgress by viewModel.downloadProgress.collectAsState()
                    if (downloadProgress.isDownloading) {
                        Spacer(modifier = Modifier.height(6.dp))
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { downloadProgress.progressPercent },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFF6200EE),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Downloading model...",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${(downloadProgress.progressPercent * 100).toInt()}% (${downloadProgress.bytesDownloaded / (1024 * 1024)} MB)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6200EE)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    viewModel.modelCatalog.forEach { model ->
                        val isInstalled = uiState.availableModels.any { it.contains(model.fileName, ignoreCase = true) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(model.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(model.sizeText, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Text(model.description, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            if (isInstalled) {
                                Text("✅ Ready", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00C853))
                            } else {
                                Button(
                                    onClick = { viewModel.startDownload(model) },
                                    enabled = !downloadProgress.isDownloading,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Download", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Sample Templates Quick Picker
            Text(
                text = "Quick Sample Presets",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                templates.forEach { template ->
                    SampleChip(template = template) {
                        viewModel.loadSampleTemplate(template)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Input Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Bank SMS / Notification Text",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(
                            onClick = {
                                clipboardManager.getText()?.text?.let {
                                    viewModel.onInputTextChanged(it)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Paste", fontSize = 13.sp)
                        }
                    }

                    OutlinedTextField(
                        value = uiState.inputText,
                        onValueChange = { viewModel.onInputTextChanged(it) },
                        placeholder = {
                            Text(
                                "Paste an SMS, e.g.: 'Your A/C *1234 debited by NPR 1,200.00 at Starbucks via Fonepay...'",
                                fontSize = 13.sp
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.analyzeInput() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !uiState.isAnalyzing && uiState.inputText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6200EE)
                        )
                    ) {
                        if (uiState.isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Extracting with AI...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Extract Transaction Details")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Extracted Result
            AnimatedVisibility(
                visible = uiState.parsedResult != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                uiState.parsedResult?.let { result ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (result.type == TransactionType.INCOME) {
                                Color(0xFF00C853).copy(alpha = 0.10f)
                            } else {
                                Color(0xFFFF3D00).copy(alpha = 0.10f)
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (result.type == TransactionType.INCOME) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                        contentDescription = null,
                                        tint = if (result.type == TransactionType.INCOME) Color(0xFF00C853) else Color(0xFFFF3D00),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = result.type.name,
                                        fontWeight = FontWeight.Bold,
                                        color = if (result.type == TransactionType.INCOME) Color(0xFF00C853) else Color(0xFFFF3D00)
                                    )
                                }
                                Text(
                                    text = "${(result.confidence * 100).toInt()}% match",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "${result.currency} ${"%,.2f".format(result.amount)}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Merchant", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(result.merchant.ifBlank { "Unknown" }, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Auto-Category", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(result.categoryName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }

                            // Account Selector Dropdown
                            var accountDropdownExpanded by remember { mutableStateOf(false) }
                            val selectedAccount = uiState.accounts.firstOrNull { it.id == uiState.selectedAccountId }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Assign to Account", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Box {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                                        .clickable { accountDropdownExpanded = true }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = selectedAccount?.let { "💳 ${it.name} (${it.currency})" } ?: "Select Account",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Text("Change ▾", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                }

                                androidx.compose.material3.DropdownMenu(
                                    expanded = accountDropdownExpanded,
                                    onDismissRequest = { accountDropdownExpanded = false }
                                ) {
                                    uiState.accounts.forEach { acc ->
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("💳 ${acc.name} (${acc.currency})", fontSize = 13.sp) },
                                            onClick = {
                                                viewModel.onAccountSelected(acc.id)
                                                accountDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.saveAsTransaction() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (result.type == TransactionType.INCOME) Color(0xFF00C853) else Color(0xFF1E88E5)
                                    )
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Save Transaction", fontSize = 13.sp)
                                }

                                Button(
                                    onClick = { showTeachDialog = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                ) {
                                    Icon(imageVector = Icons.Default.School, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Teach AI Rule", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Teach AI Dialog / Input
            if (showTeachDialog) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Teach AI this format", fontWeight = FontWeight.Bold)
                        Text("Enter the bank or service name to save this format as a few-shot rule:", fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = teachBankName,
                            onValueChange = { teachBankName = it },
                            placeholder = { Text("e.g., Nabil Bank, Chase, Fonepay...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { showTeachDialog = false }) { Text("Cancel") }
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = {
                                    viewModel.saveCustomFewShotRule(teachBankName)
                                    showTeachDialog = false
                                    teachBankName = ""
                                },
                                enabled = teachBankName.isNotBlank()
                            ) {
                                Text("Save Rule")
                            }
                        }
                    }
                }
            }

            // Status message
            uiState.statusMessage?.let { msg ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = msg,
                    color = if (uiState.isSuccess) Color(0xFF00C853) else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Saved Rules Section
            Text(
                text = "Active AI Knowledge & Bank Rules (${templates.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "These examples are injected as context to teach the model how to parse your bank's messages:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            templates.forEach { template ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(template.bankName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = template.expectedCategory,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = template.sampleMessage,
                                fontSize = 12.sp,
                                maxLines = 2,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.deleteTemplate(template.id) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sender Whitelist / Blacklist & Account Mappings Section
            val senderRules by viewModel.senderRules.collectAsState()
            var showAddRuleModal by remember { mutableStateOf(false) }
            var newSenderPattern by remember { mutableStateOf("") }
            var newIsBlacklisted by remember { mutableStateOf(false) }
            var newMappedAccountId by remember { mutableStateOf<java.util.UUID?>(null) }
            var senderAccountDropdownExpanded by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Sender Rules & Account Mappings (${senderRules.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Map SMS sender IDs (e.g., NABIL, CHASE) directly to your accounts or blacklist spam.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    newSenderPattern = ""
                    newIsBlacklisted = false
                    newMappedAccountId = uiState.accounts.firstOrNull()?.id
                    showAddRuleModal = true
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
            ) {
                Text("+ Add Sender Rule / Mapping", fontSize = 12.sp)
            }

            if (showAddRuleModal) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Add Sender Rule", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = newSenderPattern,
                            onValueChange = { newSenderPattern = it },
                            placeholder = { Text("Sender ID / Keyword (e.g. NABIL, FONEPAY, CHASE)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(
                                checked = newIsBlacklisted,
                                onCheckedChange = { newIsBlacklisted = it }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("⛔ Blacklist (Ignore and never process this sender)", fontSize = 12.sp)
                        }

                        if (!newIsBlacklisted) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Route to Account:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            val chosenAcc = uiState.accounts.firstOrNull { it.id == newMappedAccountId }

                            Box {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .clickable { senderAccountDropdownExpanded = true }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(chosenAcc?.let { "💳 ${it.name} (${it.currency})" } ?: "Select Account", fontSize = 12.sp)
                                    Text("Change ▾", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                }

                                androidx.compose.material3.DropdownMenu(
                                    expanded = senderAccountDropdownExpanded,
                                    onDismissRequest = { senderAccountDropdownExpanded = false }
                                ) {
                                    uiState.accounts.forEach { acc ->
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("💳 ${acc.name} (${acc.currency})", fontSize = 12.sp) },
                                            onClick = {
                                                newMappedAccountId = acc.id
                                                senderAccountDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { showAddRuleModal = false }) { Text("Cancel") }
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = {
                                    viewModel.saveSenderRule(newSenderPattern, newIsBlacklisted, if (newIsBlacklisted) null else newMappedAccountId)
                                    showAddRuleModal = false
                                },
                                enabled = newSenderPattern.isNotBlank()
                            ) {
                                Text("Save Rule")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            senderRules.forEach { rule ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (rule.isBlacklisted) Color(0xFFFF3D00).copy(alpha = 0.08f) else Color(0xFF00C853).copy(alpha = 0.08f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(rule.senderPattern, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (rule.isBlacklisted) "⛔ Blacklisted" else "➔ ${rule.mappedAccountName ?: "Default Account"}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (rule.isBlacklisted) Color(0xFFFF3D00) else Color(0xFF00C853)
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.deleteSenderRule(rule.id) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SampleChip(
    template: BankFewShotTemplate,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = template.bankName,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
