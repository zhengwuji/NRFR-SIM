package com.github.nrfr.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.github.nrfr.R
import com.github.nrfr.data.CountryPresets
import com.github.nrfr.data.PresetCarriers
import com.github.nrfr.manager.CarrierConfigManager
import com.github.nrfr.model.SimCardInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 写入配置后轮询读取的间隔与超时,替代固定 sleep。 */
private const val REFRESH_INTERVAL_MS = 200L
private const val REFRESH_TIMEOUT_MS = 3000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onShowAbout: () -> Unit) {
    val context = LocalContext.current
    var selectedSimCard by remember { mutableStateOf<SimCardInfo?>(null) }
    var selectedCountryCode by remember { mutableStateOf("") }
    var customCountryCode by remember { mutableStateOf("") }
    var isCustomCountryCode by remember { mutableStateOf(false) }
    var selectedCarrier by remember { mutableStateOf<PresetCarriers.CarrierPreset?>(null) }
    var customCarrierName by remember { mutableStateOf("") }
    var isSimCardMenuExpanded by remember { mutableStateOf(false) }
    var isCountryCodeMenuExpanded by remember { mutableStateOf(false) }
    var isCarrierMenuExpanded by remember { mutableStateOf(false) }
    var isWorking by remember { mutableStateOf(false) }

    // 配置读取全部走 IO 线程,refreshTick 变化时自动重读
    var refreshTick by remember { mutableStateOf(0) }
    var simCards by remember { mutableStateOf<List<SimCardInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    suspend fun reload() {
        isLoading = true
        simCards = withContext(Dispatchers.IO) { CarrierConfigManager.getSimCards(context) }
        isLoading = false
    }

    LaunchedEffect(Unit) { reload() }
    LaunchedEffect(refreshTick) { if (refreshTick > 0) reload() }

    // 写入后轮询直到配置读回变化或超时,替代固定 800ms sleep
    suspend fun refreshAfterWrite(check: (List<SimCardInfo>) -> Boolean) {
        withContext(Dispatchers.IO) {
            val deadline = System.currentTimeMillis() + REFRESH_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                delay(REFRESH_INTERVAL_MS)
                val cards = CarrierConfigManager.getSimCards(context)
                if (check(cards)) {
                    simCards = cards
                    return@withContext
                }
            }
            simCards = CarrierConfigManager.getSimCards(context)
        }
    }

    // simCards 更新时同步选中卡的信息
    LaunchedEffect(simCards, selectedSimCard) {
        if (selectedSimCard != null) {
            selectedSimCard = simCards.find { it.slot == selectedSimCard?.slot }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            modifier = Modifier.size(48.dp),
                            contentDescription = stringResource(R.string.app_icon_desc),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nrfr")
                    }
                },
                actions = {
                    IconButton(onClick = onShowAbout) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.about_title))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // SIM卡选择
            SimCardSelector(
                simCards = simCards,
                selectedSimCard = selectedSimCard,
                isLoading = isLoading,
                isExpanded = isSimCardMenuExpanded,
                onExpandedChange = { isSimCardMenuExpanded = it },
                onSimCardSelected = { selectedSimCard = it }
            )

            // 显示当前选中的 SIM 卡的配置信息
            selectedSimCard?.let { simCard ->
                CurrentConfigCard(simCard = simCard)
            }

            // 国家码选择
            CountryCodeSelector(
                selectedCountryCode = selectedCountryCode,
                isCustomCountryCode = isCustomCountryCode,
                customCountryCode = customCountryCode,
                isExpanded = isCountryCodeMenuExpanded,
                onExpandedChange = { isCountryCodeMenuExpanded = it },
                onCountryCodeSelected = { code ->
                    selectedCountryCode = code
                    isCustomCountryCode = false
                },
                onCustomSelected = {
                    isCustomCountryCode = true
                    selectedCountryCode = customCountryCode
                }
            )

            // 自定义国家码输入框
            if (isCustomCountryCode) {
                CustomCountryCodeInput(
                    value = customCountryCode,
                    onValueChange = {
                        if (it.length <= 2 && it.all { char -> char.isLetter() }) {
                            customCountryCode = it.uppercase()
                            selectedCountryCode = it.uppercase()
                        }
                    }
                )
            }

            // 运营商选择
            CarrierSelector(
                selectedCarrier = selectedCarrier,
                isExpanded = isCarrierMenuExpanded,
                onExpandedChange = { isCarrierMenuExpanded = it },
                onCarrierSelected = { carrier ->
                    selectedCarrier = carrier
                    customCarrierName = carrier.displayName
                }
            )

            // 自定义运营商名称输入框
            if (selectedCarrier?.isCustom == true) {
                CustomCarrierNameInput(
                    value = customCarrierName,
                    onValueChange = { customCarrierName = it }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // 按钮行
            ActionButtons(
                selectedSimCard = selectedSimCard,
                isWorking = isWorking,
                enabled = selectedSimCard != null,
                selectedCountryCode = selectedCountryCode,
                isCustomCountryCode = isCustomCountryCode,
                customCountryCode = customCountryCode,
                selectedCarrier = selectedCarrier,
                customCarrierName = customCarrierName,
                onReset = { simCard ->
                    isWorking = true
                    try {
                        withContext(Dispatchers.IO) {
                            CarrierConfigManager.resetCarrierConfig(context, simCard.subId)
                        }
                        val before = simCard.currentConfig
                        refreshAfterWrite { cards ->
                            cards.find { it.subId == simCard.subId }?.currentConfig != before
                        }
                        Toast.makeText(context, R.string.config_reset, Toast.LENGTH_SHORT).show()
                        selectedCountryCode = ""
                        selectedCarrier = null
                        customCarrierName = ""
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.reset_failed, e.message ?: ""),
                            Toast.LENGTH_SHORT
                        ).show()
                    } finally {
                        isWorking = false
                    }
                },
                onSave = { simCard ->
                    isWorking = true
                    try {
                        val carrierName = if (selectedCarrier?.isCustom == true) {
                            customCarrierName.takeIf { it.isNotEmpty() }
                        } else {
                            selectedCarrier?.displayName
                        }
                        val countryCode = if (isCustomCountryCode) {
                            customCountryCode.takeIf { it.length == 2 }
                        } else {
                            selectedCountryCode
                        }
                        val before = simCard.currentConfig
                        withContext(Dispatchers.IO) {
                            CarrierConfigManager.setCarrierConfig(
                                context,
                                simCard.subId,
                                countryCode,
                                carrierName
                            )
                        }
                        refreshAfterWrite { cards ->
                            cards.find { it.subId == simCard.subId }?.currentConfig != before
                        }
                        Toast.makeText(context, R.string.config_saved, Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.save_failed, e.message ?: ""),
                            Toast.LENGTH_SHORT
                        ).show()
                    } finally {
                        isWorking = false
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimCardSelector(
    simCards: List<SimCardInfo>,
    selectedSimCard: SimCardInfo?,
    isLoading: Boolean,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSimCardSelected: (SimCardInfo) -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = onExpandedChange
    ) {
        OutlinedTextField(
            value = selectedSimCard?.let { "SIM ${it.slot} (${it.carrierName})" } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.sim_card_select_label)) },
            supportingText = {
                when {
                    isLoading -> Text(stringResource(R.string.loading))
                    simCards.isEmpty() -> Text(stringResource(R.string.no_sim_detected))
                }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            simCards.forEach { simCard ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("SIM ${simCard.slot} (${simCard.carrierName})")
                            if (simCard.hasError) {
                                Text(
                                    stringResource(R.string.config_read_error),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else if (simCard.currentConfig.isEmpty()) {
                                Text(
                                    stringResource(R.string.no_override_config),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                simCard.currentConfig.forEach { (key, value) ->
                                    Text(
                                        configEntryLabel(key, value),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    onClick = {
                        onSimCardSelected(simCard)
                        onExpandedChange(false)
                    }
                )
            }
        }
    }
}

@Composable
private fun CurrentConfigCard(simCard: SimCardInfo) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                stringResource(R.string.current_config_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            when {
                simCard.hasError -> Text(
                    stringResource(R.string.config_read_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                simCard.currentConfig.isEmpty() -> Text(
                    stringResource(R.string.no_override_config),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> simCard.currentConfig.forEach { (key, value) ->
                    Text(
                        configEntryLabel(key, value),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun configEntryLabel(key: String, value: String): String {
    val label = when (key) {
        SimCardInfo.Key.COUNTRY_CODE -> stringResource(R.string.entry_country_code)
        SimCardInfo.Key.CARRIER_NAME -> stringResource(R.string.entry_carrier_name)
        else -> key
    }
    return "$label: $value"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryCodeSelector(
    selectedCountryCode: String,
    isCustomCountryCode: Boolean,
    customCountryCode: String,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCountryCodeSelected: (String) -> Unit,
    onCustomSelected: () -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = onExpandedChange
    ) {
        OutlinedTextField(
            value = when {
                isCustomCountryCode -> stringResource(R.string.custom)
                selectedCountryCode.isEmpty() -> ""
                else -> CountryPresets.countries.find { it.code == selectedCountryCode }
                    ?.let { "${it.name} (${it.code})" }
                    ?: selectedCountryCode
            },
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.country_code_select_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            // 预设国家码列表
            CountryPresets.countries.forEach { countryInfo ->
                DropdownMenuItem(
                    text = { Text("${countryInfo.name} (${countryInfo.code})") },
                    onClick = {
                        onCountryCodeSelected(countryInfo.code)
                        onExpandedChange(false)
                    }
                )
            }
            // 自定义选项
            DropdownMenuItem(
                text = { Text(stringResource(R.string.custom)) },
                onClick = {
                    onCustomSelected()
                    onExpandedChange(false)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomCountryCodeInput(
    value: String,
    onValueChange: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.custom_country_code_label)) },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                focusManager.clearFocus()
            }
        ),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarrierSelector(
    selectedCarrier: PresetCarriers.CarrierPreset?,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCarrierSelected: (PresetCarriers.CarrierPreset) -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = onExpandedChange
    ) {
        OutlinedTextField(
            value = selectedCarrier?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.carrier_select_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            // 分组显示运营商
            PresetCarriers.presets
                .filter { !it.isCustom }
                .groupBy { it.region }
                .forEach { (region, carriers) ->
                    if (region.isNotEmpty()) {
                        val regionName = CountryPresets.countries.find { it.code == region }?.name ?: region
                        Text(
                            regionName,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        carriers.forEach { carrier ->
                            DropdownMenuItem(
                                text = { Text(carrier.name) },
                                onClick = {
                                    onCarrierSelected(carrier)
                                    onExpandedChange(false)
                                }
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }

            // 自定义选项
            PresetCarriers.presets
                .filter { it.isCustom }
                .forEach { carrier ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.custom)) },
                        onClick = {
                            onCarrierSelected(carrier)
                            onExpandedChange(false)
                        }
                    )
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomCarrierNameInput(
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.custom_carrier_name_label)) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ActionButtons(
    selectedSimCard: SimCardInfo?,
    isWorking: Boolean,
    enabled: Boolean,
    selectedCountryCode: String,
    isCustomCountryCode: Boolean,
    customCountryCode: String,
    selectedCarrier: PresetCarriers.CarrierPreset?,
    customCarrierName: String,
    onReset: suspend (SimCardInfo) -> Unit,
    onSave: suspend (SimCardInfo) -> Unit
) {
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 还原按钮
        OutlinedButton(
            onClick = {
                selectedSimCard?.let { sim ->
                    scope.launch { onReset(sim) }
                }
            },
            modifier = Modifier.weight(1f),
            enabled = enabled && !isWorking
        ) {
            if (isWorking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(stringResource(R.string.reset_button))
            }
        }

        // 保存按钮
        Button(
            onClick = {
                selectedSimCard?.let { sim ->
                    scope.launch { onSave(sim) }
                }
            },
            modifier = Modifier.weight(1f),
            enabled = enabled && !isWorking && (
                    (isCustomCountryCode && customCountryCode.length == 2) ||
                            (!isCustomCountryCode && selectedCountryCode.isNotEmpty()) ||
                            (selectedCarrier != null && (!selectedCarrier.isCustom || customCarrierName.isNotEmpty()))
                    )
        ) {
            Text(stringResource(R.string.save_button))
        }
    }
}
