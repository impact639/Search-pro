package com.buguagaoshu.scan.search.ui

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.buguagaoshu.scan.search.control.HttpControl
import com.buguagaoshu.scan.search.data.ConfigData
import com.buguagaoshu.scan.search.data.SendData
import com.buguagaoshu.scan.search.data.SendMessage
import com.buguagaoshu.scan.search.service.ScreenReaderService
import com.buguagaoshu.scan.search.ui.theme.Dimens
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import com.buguagaoshu.scan.search.R.string as TextValue

@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)
@Composable
fun HomePage(commonViewModel: CommonViewModel, openFloatingWindow: () -> Unit) {
    val configMap = commonViewModel.apiMap
    val context = LocalContext.current

    // 当前选中（高亮）的配置 id；与 map 中的 cheek 标志保持同步
    var selectedConfigId by remember { mutableStateOf<String?>(null) }
    // 正在编辑的配置 id；null 表示新增
    var editingConfigId by remember { mutableStateOf<String?>(null) }
    var showForm by remember { mutableStateOf(false) }
    // 待删除的配置（触发确认对话框）
    var pendingDelete by remember { mutableStateOf<ConfigData?>(null) }

    val tipsText = stringResource(TextValue.please_complete_fields)
    val saveSuccessText = stringResource(TextValue.save_success)

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(TextValue.app_title)) })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.SpaceLg)
                .padding(bottom = Dimens.SpaceXl),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLg),
        ) {
            // 头部：用途说明 + 配置状态
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
                Text(
                    text = stringResource(TextValue.app_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(TextValue.config_count, configMap.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // 主操作：打开浮窗
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = openFloatingWindow,
            ) {
                Text(stringResource(TextValue.open_float))
            }

            // 划词启用入口：划词依赖无障碍服务，重装 APK 后会被系统关闭，需手动重新开启。
            // 不再在 onCreate 里强制跳转无障碍设置页（那样会打断第三方分享流程），改为这里手动进入。
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    try {
                        // 尝试直达本应用的无障碍详情页。该 action 是 Android 15+ 才有的隐藏系统 action，
                        // 未进公开 SDK（用字符串常量绕过编译检查），且要求用 EXTRA_COMPONENT_NAME 指定服务。
                        val intent = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").apply {
                            putExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName(context, ScreenReaderService::class.java))
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // 设备不支持详情页 action 时回退到通用无障碍列表
                        try {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        } catch (e2: Exception) {
                            android.widget.Toast.makeText(
                                context,
                                context.getString(TextValue.open_accessibility_failed),
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                },
            ) {
                Text(stringResource(TextValue.enable_selection))
            }
            Text(
                text = stringResource(TextValue.enable_selection_tips),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 配置列表
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                Text(
                    text = stringResource(TextValue.config_section),
                    style = MaterialTheme.typography.titleMedium,
                )

                if (configMap.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Dimens.RadiusMd),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Text(
                            text = stringResource(TextValue.no_config_yet),
                            modifier = Modifier.padding(Dimens.SpaceLg),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    configMap.values.forEach { config ->
                        ConfigCard(
                            config = config,
                            isSelected = config.id == selectedConfigId,
                            onSelect = {
                                val wasSelected = config.id == selectedConfigId
                                configMap.values.forEach { it.cheek = false }
                                if (wasSelected) {
                                    selectedConfigId = null
                                } else {
                                    config.cheek = true
                                    selectedConfigId = config.id
                                    commonViewModel.showUserSelectConfig(config)
                                }
                                commonViewModel.updateConfig(context)
                            },
                            onEdit = {
                                configMap.values.forEach { it.cheek = false }
                                config.cheek = true
                                selectedConfigId = config.id
                                commonViewModel.showUserSelectConfig(config)
                                commonViewModel.updateConfig(context)
                                editingConfigId = config.id
                                showForm = true
                            },
                            onDelete = { pendingDelete = config },
                        )
                    }
                }

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        commonViewModel.prepareNewConfig()
                        editingConfigId = null
                        showForm = true
                    },
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(TextValue.add_config))
                }
            }

            // 新增 / 编辑表单
            if (showForm) {
                ConfigEditCard(
                    commonViewModel = commonViewModel,
                    isEditing = editingConfigId != null,
                    onTestConnection = {
                        val url = commonViewModel.aiServerBaseUrl.value.trim()
                        val key = commonViewModel.apiKeyText.value.trim()
                        val model = commonViewModel.modelName.value.trim()
                        if (url.isEmpty() || key.isEmpty()) {
                            android.widget.Toast.makeText(context, tipsText, android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            HttpControl.send(
                                SendData(
                                    stream = false,
                                    model = model,
                                    messages = listOf(SendMessage("user", "ping")),
                                ),
                                url,
                                key,
                                onSuccess = {
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(TextValue.test_connection_success),
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                },
                                onError = {
                                    android.widget.Toast.makeText(
                                        context,
                                        "${context.getString(TextValue.test_connection_failed)}：$it",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            )
                        }
                    },
                    onCancel = {
                        showForm = false
                        editingConfigId = null
                    },
                    onSave = {
                        val name = commonViewModel.apiName.value.trim()
                        val url = commonViewModel.aiServerBaseUrl.value.trim()
                        val model = commonViewModel.modelName.value.trim()
                        val key = commonViewModel.apiKeyText.value.trim()
                        when {
                            name.isEmpty() || url.isEmpty() || key.isEmpty() -> {
                                android.widget.Toast.makeText(context, tipsText, android.widget.Toast.LENGTH_SHORT).show()
                            }
                            !url.startsWith("http://") && !url.startsWith("https://") -> {
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(TextValue.invalid_url),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                            else -> {
                                val config = ConfigData(
                                    Uuid.random().toString(),
                                    name,
                                    url,
                                    model,
                                    key,
                                    commonViewModel.promptText.value,
                                )
                                editingConfigId?.let { config.id = it }
                                commonViewModel.addToApiMap(config.id, config)
                                selectedConfigId = null
                                commonViewModel.updateConfig(context)
                                showForm = false
                                editingConfigId = null
                                android.widget.Toast.makeText(context, saveSuccessText, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
            }

            // 使用指南
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val url = "https://gitee.com/puzhiweizuishuai/ScanSearch"
                    val customTabsIntent = CustomTabsIntent.Builder().build()
                    try {
                        customTabsIntent.launchUrl(context, Uri.parse(url))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
            ) {
                Text(stringResource(TextValue.usage_guide))
            }
        }
    }

    // 删除确认对话框
    pendingDelete?.let { config ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(TextValue.confirm_deletion)) },
            text = { Text(stringResource(TextValue.config_delete_tips)) },
            confirmButton = {
                Button(
                    onClick = {
                        commonViewModel.removeFromApiMap(config.id)
                        commonViewModel.updateConfig(context)
                        if (config.id == selectedConfigId) selectedConfigId = null
                        if (config.id == editingConfigId) {
                            editingConfigId = null
                            showForm = false
                        }
                        pendingDelete = null
                    },
                ) {
                    Text(stringResource(TextValue.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(TextValue.cancel))
                }
            },
        )
    }
}

// 单个配置卡片：可选中、可编辑、可删除
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigCard(
    config: ConfigData,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.RadiusMd),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.SpaceSm, vertical = Dimens.SpaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = isSelected, onClick = onSelect)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Dimens.SpaceSm),
            ) {
                Text(
                    text = config.name,
                    style = MaterialTheme.typography.titleSmall,
                )
                val subtitle = config.modelName.ifBlank { config.api }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            if (isSelected) {
                Text(
                    text = stringResource(TextValue.in_use),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(TextValue.edit_config),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(TextValue.delete_config),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// 新增 / 编辑配置表单
@Composable
private fun ConfigEditCard(
    commonViewModel: CommonViewModel,
    isEditing: Boolean,
    onTestConnection: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val apiName by commonViewModel.apiName.collectAsState()
    val baseUrl by commonViewModel.aiServerBaseUrl.collectAsState()
    val modelName by commonViewModel.modelName.collectAsState()
    val apiKeyText by commonViewModel.apiKeyText.collectAsState()
    val prompt by commonViewModel.promptText.collectAsState()

    var showApiKey by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.RadiusLg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.SpaceLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
        ) {
            Text(
                text = stringResource(if (isEditing) TextValue.edit_config else TextValue.add_config),
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = apiName,
                onValueChange = { commonViewModel.updateApiName(it) },
                label = { Text(stringResource(TextValue.config_file_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { commonViewModel.updateBaseUrl(it) },
                label = { Text(stringResource(TextValue.please_enter_server_address)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = modelName,
                onValueChange = { commonViewModel.updateModelName(it) },
                label = { Text(stringResource(TextValue.please_enter_model_name)) },
                placeholder = { Text(stringResource(TextValue.model_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            ApiKeyField(
                value = apiKeyText,
                onValueChange = { commonViewModel.updateApiKeyText(it) },
                show = showApiKey,
                onToggleShow = { showApiKey = !showApiKey },
            )

            // 高级选项：自定义提示词，默认收起
            TextButton(
                onClick = { showAdvanced = !showAdvanced },
            ) {
                Text(stringResource(TextValue.advanced_settings))
            }
            if (showAdvanced) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { commonViewModel.updatePrompt(it) },
                    label = { Text(stringResource(TextValue.customize_the_prompt)) },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                OutlinedButton(
                    onClick = onTestConnection,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(TextValue.test_connection))
                }
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(TextValue.cancel))
                }
            }

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(TextValue.save_config))
            }
        }
    }
}

// API Key 输入框：默认隐藏，带显示/隐藏切换
@Composable
private fun ApiKeyField(
    value: String,
    onValueChange: (String) -> Unit,
    show: Boolean,
    onToggleShow: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(TextValue.please_enter_API_Key)) },
        singleLine = true,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = onToggleShow) {
                Icon(
                    imageVector = if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = stringResource(if (show) TextValue.hide else TextValue.show),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
