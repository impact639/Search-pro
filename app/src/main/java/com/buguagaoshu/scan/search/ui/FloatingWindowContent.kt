package com.buguagaoshu.scan.search.ui

import android.annotation.SuppressLint
import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buguagaoshu.scan.search.config.StaticVariableConfig
import com.buguagaoshu.scan.search.control.FxComposeControl
import com.buguagaoshu.scan.search.control.HttpControl
import com.buguagaoshu.scan.search.data.ChatResponse
import com.buguagaoshu.scan.search.data.ConfigData
import com.buguagaoshu.scan.search.data.ScanSearchData
import com.buguagaoshu.scan.search.data.SendData
import com.buguagaoshu.scan.search.data.SendMessage

import com.petterp.floatingx.util.FxInputHelper
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.serialization.json.Json
import com.buguagaoshu.scan.search.R.string as TextValue

@Preview
@Composable
fun FloatingWindowContentPreview() {
    val configModel = remember { CommonViewModel() }
    FloatingWindowContent(configModel)
}

@SuppressLint("UnrememberedMutableState")
@Composable
fun FloatingWindowContent(commonViewModel: CommonViewModel) {
    // 使用 mutableStateListOf 来管理可变列表，自动跟踪列表变化
    val closeKeyBoard = stringResource(TextValue.close_keyboard)
    val openKeyBoard = stringResource(TextValue.open_keyboard)
    val streamText = stringResource(TextValue.stream)
    val closeStreamText = stringResource(TextValue.close_stream)
    val requestFailedText = stringResource(TextValue.request_failed)
    val modeText = stringResource(TextValue.mode)
    val explainText = stringResource(TextValue.explain)
    val solveText = stringResource(TextValue.solve)
    val noConfigTips = stringResource(TextValue.no_config_tips)

    val textList = remember { mutableStateListOf<ScanSearchData>() }
    val textMap = remember { mutableStateMapOf<String, ScanSearchData>() }

    // 控制数据列表的显示与不显示
    val isListVisible = remember { mutableStateOf(true) }
    val showResultVisible = remember { mutableStateOf(false) }
    val showWebView = remember { mutableStateOf(false) }
    val aiResultText = remember { mutableStateOf("") } // 新增AI结果状态
    val keyboardText =  remember { mutableStateOf(openKeyBoard) }


    val showKeyboard = remember { mutableStateOf(false) }

    val streamBtnText =  remember { mutableStateOf(streamText) }
    val streamInfo = remember { mutableStateOf(true) }

    val questionText by commonViewModel.questionText.collectAsState()

    val view = LocalView.current
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val alphabet = listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K")
    val configMap = commonViewModel.apiMap



    val prompt by commonViewModel.promptText.collectAsState()
    val explainMode by commonViewModel.explainMode.collectAsState()
    val isLocked by StaticVariableConfig.isLockedFlow.collectAsState()

    // 划词：监听无障碍捕获的选中文字，自动填入问题框
    val selectedText by StaticVariableConfig.selectedTextFlow.collectAsState()
    LaunchedEffect(selectedText) {
        if (selectedText.isNotBlank()) {
            commonViewModel.updateQuestionText(selectedText)
        }
    }

    // 轻量聊天记录（内存态，可回看上次回答）
    val history = remember { mutableStateListOf<ChatItem>() }
    var lastQuestion by remember { mutableStateOf("") }


    // 找到cheek为true的ConfigData，如果没有则使用第一个
    // 记录当前选中的 ConfigData
    var selectedConfigData by remember { mutableStateOf<ConfigData?>(null) }
    for (entry in configMap.values) {
        if (entry.cheek) {
            selectedConfigData = entry
            break
        }
    }
    if (selectedConfigData == null && configMap.isNotEmpty()) {
        selectedConfigData = configMap.values.first()
    }

    ElevatedCard(
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), // 增加阴影高度
        shape = RoundedCornerShape(16.dp), // 添加圆角
        modifier = Modifier
            .size(width = 280.dp, height = 560.dp), // 调整高度以显示更多内容
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface // 跟随主题，不再死白
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                //.verticalScroll(scrollState)
                .padding(4.dp), // 增加内边距
            verticalArrangement = Arrangement.spacedBy(4.dp) // 增加元素间距
        ) {
            // 顶部拖动手柄：下滑收起回气泡
            val density = LocalDensity.current
            var dragAccum by remember { mutableFloatStateOf(0f) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = { dragAccum = 0f },
                            onVerticalDrag = { _, dragAmount ->
                                dragAccum += dragAmount
                                if (dragAccum > with(density) { 60.dp.toPx() }) {
                                    dragAccum = 0f
                                    FxComposeControl.hideSearch(application)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }

            // 顶部标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(TextValue.float_title),
                    style = MaterialTheme.typography.titleMedium, // 使用预设样式
                    modifier = Modifier.weight(1f), // 占据剩余空间
                )

                // 图标按钮组
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp) // 按钮间添加间距
                ) {
                    IconButton(
                        onClick = {
                            FxComposeControl.hideSearch(application)
                        },
                        modifier = Modifier.size(32.dp) // 固定按钮大小
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "收起",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            FxComposeControl.updateDisplayMode()
                        },
                        modifier = Modifier.size(32.dp) // 固定按钮大小
                    ) {
                        Icon(
                            imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            contentDescription = if (isLocked) "锁定" else "解锁",
                            tint = if (isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { FxComposeControl.cancel() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.error // 使用错误色
                        )
                    }
                }
            }

            // AI 服务商切换
            Row(
                modifier = Modifier
                    .padding(0.dp)
                    .horizontalScroll(rememberScrollState()), // 添加水平滚动条
            ) {
                configMap.forEach { (key, value) ->
                    ConfigMiniFilterChip(
                        configData = value,
                        commonViewModel = commonViewModel,
                        isSelected = value == selectedConfigData,
                        onSelect = { newSelected ->
                            // 如果点击的是已经选中的 ConfigFilterChip，则取消选中
                            if (newSelected == selectedConfigData) {
                                selectedConfigData = null
                                newSelected.cheek = false
                            } else {
                                // 取消之前选中的 ConfigFilterChip
                                selectedConfigData?.cheek = false
                                // 选中新的 ConfigFilterChip
                                selectedConfigData = newSelected
                                newSelected.cheek = true
                                commonViewModel.showUserSelectConfig(newSelected)
                            }
                            commonViewModel.updateConfig(context)
                        }
                    )
                    Spacer(modifier = Modifier.width(1.dp))
                }
            }


            // 解释/解题模式切换
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = modeText,
                    style = MaterialTheme.typography.labelMedium,
                )
                FilterChip(
                    selected = explainMode,
                    onClick = { commonViewModel.setPromptMode(true) },
                    label = { Text(explainText, style = TextStyle(fontSize = 12.sp)) },
                    modifier = Modifier.height(28.dp)
                )
                FilterChip(
                    selected = !explainMode,
                    onClick = { commonViewModel.setPromptMode(false) },
                    label = { Text(solveText, style = TextStyle(fontSize = 12.sp)) },
                    modifier = Modifier.height(28.dp)
                )
            }

            // 读取的数据列表
            AnimatedVisibility(
                visible = isListVisible.value,
                modifier = Modifier.weight(1f)
            ) {
                // 内容列表
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .height(305.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(textList) { item ->
                        Surface(
                            shape = RoundedCornerShape(8.dp), // 添加圆角
                            color = MaterialTheme.colorScheme.surfaceVariant, // 使用表面变体色
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.text,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp), // 添加右边距
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Checkbox(
                                    checked = textMap[item.id]?.cheek ?: false,
                                    onCheckedChange = {
                                        // 创建一个新的 ScanSearchData 对象
                                        val newItem = item.copy(cheek = it)
                                        textMap[item.id] = newItem
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary // 使用主题色
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 结果框：聊天记录 + 当前答案
            AnimatedVisibility(
                visible = showResultVisible.value,
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(285.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(history) { item ->
                        Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                            if (item.question.isNotBlank()) {
                                Text(
                                    text = item.question,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            MarkdownText(
                                markdown = item.answer,
                                isTextSelectable = true,
                            )
                        }
                    }
                    if (aiResultText.value.isNotBlank()) {
                        item {
                            MarkdownText(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                markdown = aiResultText.value,
                                isTextSelectable = true,
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showWebView.value
            ) {
                MyWebView()
            }


            // 输入框
            OutlinedTextField(
                value = questionText,
                onValueChange = { commonViewModel.updateQuestionText(it) },
                label = { Text(stringResource(TextValue.search_tips)) },
                maxLines = 2,
                shape = RoundedCornerShape(8.dp), // 添加圆角
                modifier = Modifier.fillMaxWidth()
            )

            // 操作按钮组
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 键盘操作行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ActionButton(
                        modifier = Modifier.width(64.dp).height(40.dp),
                        text = streamBtnText.value,
                        onClick = {
                            if (!streamInfo.value) {
                                streamBtnText.value = streamText
                                streamInfo.value = true
                            } else {
                                streamBtnText.value = closeStreamText
                                streamInfo.value = false
                            }

                        },
                    )
                    ActionButton(
                        text = keyboardText.value,
                        onClick = {
                            if (!showKeyboard.value) {
                                keyboardText.value = closeKeyBoard
                                showKeyboard.value = true
                                FxInputHelper.showKeyBoard("ScanSearch", view)
                            } else {
                                keyboardText.value = openKeyBoard
                                showKeyboard.value = false
                                FxInputHelper.hideKeyBoard("ScanSearch", view)
                            }

                        },
                        modifier = Modifier.weight(1f).height(40.dp)
                    )


                    ActionButton(
                        text = stringResource(TextValue.load_data),
                        onClick = {
                            println("点击读取屏幕按钮")
                            // 清除历史数据
                            textList.clear()
                            textMap.clear()

                            var i = 0;

                            while (i < StaticVariableConfig.screenTextList.size) {
                                if (alphabet.contains(StaticVariableConfig.screenTextList[i].text)) {
                                    if (i + 1 >= StaticVariableConfig.screenTextList.size) {
                                        textList.add(StaticVariableConfig.screenTextList[i])
                                    } else {
                                        // 处理只有的情况ABCD
                                        textList.add(
                                            ScanSearchData(
                                                StaticVariableConfig.screenTextList[i].id,
                                                "${StaticVariableConfig.screenTextList[i].text}.${StaticVariableConfig.screenTextList[i + 1].text}"
                                                )
                                        )
                                        i++
                                    }
                                } else {
                                    textList.add(StaticVariableConfig.screenTextList[i])
                                }
                                i++
                            }

                            textList.forEach { key ->
                                textMap[key.id] = key
                            }
                            // 显示列表
                            isListVisible.value = true
                            showWebView.value = false
                            showResultVisible.value = false
                        },
                        modifier = Modifier.weight(1f).height(40.dp),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer // 使用次要容器色
                    )

                    ActionButton(
                        text = stringResource(TextValue.confirm),
                        onClick = {
                            val stringQuestion = StringBuilder()
                            for (item : ScanSearchData in textList) {
                                if (textMap[item.id]?.cheek == true) {
                                    stringQuestion.append("${item.text}\n")
                                }
                            }
                            commonViewModel.updateQuestionText(stringQuestion.toString())
                            // 新增隐藏列表逻辑
                            isListVisible.value = false
                            showResultVisible.value = true
                        },
                        modifier = Modifier.weight(1f).height(40.dp)
                    )
                }

                // 确认操作行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {


                    ActionButton(
                        text = stringResource(TextValue.open_web),
                        onClick = {
                            isListVisible.value = false
                            showResultVisible.value = false
                            showWebView.value = true
                        },
                        modifier = Modifier.weight(2f).height(40.dp)
                    )

                    Button(
                        onClick = {
                            // 空值校验：无可用配置或问题为空时安全退出，避免空指针
                            val config = selectedConfigData
                            if (config == null) {
                                android.widget.Toast.makeText(context, noConfigTips, android.widget.Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (questionText.isBlank()) {
                                return@Button
                            }

                            // 新增隐藏列表逻辑
                            isListVisible.value = false
                            showWebView.value = false
                            showResultVisible.value = true
                            // 归档上一条问答
                            if (lastQuestion.isNotBlank() && aiResultText.value.isNotBlank()) {
                                history.add(ChatItem(lastQuestion, aiResultText.value))
                            }
                            lastQuestion = questionText
                            aiResultText.value = ""

                            val sendData = SendData(
                                stream = streamInfo.value,
                                config.modelName,
                                listOf(
                                    SendMessage(
                                        "system",
                                       prompt
                                    ),
                                    SendMessage(
                                        "user",
                                        questionText
                                    )
                                )
                            )
                            val jsonConfig = Json {
                                ignoreUnknownKeys = true
                                isLenient = true
                                coerceInputValues = true // 允许将 null 转换为默认值
                            }

                            if (streamInfo.value) {
                                HttpControl.sendStream(
                                    sendData,
                                    config.api,
                                    config.apiKey,
                                    onChunkReceived = { chunk ->
                                        val jsonLine = if (chunk.startsWith("data: ")) {
                                            chunk.substring("data: ".length)
                                        } else {
                                            chunk
                                        }
                                        if (chunk == ": keep-alive") {
                                            return@sendStream
                                        }

                                        try {
                                            val str = jsonConfig.decodeFromString<ChatResponse>(jsonLine)
                                            if (str.choices.isNotEmpty()) {
                                                if (str.choices[0].delta?.reasoning_content?.isNotEmpty() == true) {
                                                    aiResultText.value += str.choices[0].delta?.reasoning_content
                                                }
                                                aiResultText.value += str.choices[0].delta?.content
                                            }
                                        } catch (e: Exception) {
                                            println(e.message)
                                        }
                                    },
                                    onComplete = {
                                        println("Streaming completed.")
                                    },
                                    onError = { error ->
                                        aiResultText.value = "$requestFailedText$error"
                                        println("Error: $error")
                                    }
                                )
                            } else {
                                aiResultText.value = "当前使用非流式输出，等待时间可能较长，请耐心等待！\n"
                                HttpControl.send(
                                    sendData,
                                    config.api,
                                    config.apiKey,
                                    onSuccess = { str ->
                                        try {
                                            val jsstr = jsonConfig.decodeFromString<ChatResponse>(str)

                                            if (jsstr.choices.isNotEmpty()) {
                                                // 显示思维链
                                                if (jsstr.choices[0].message?.reasoning_content?.isNotEmpty() == true) {
                                                    aiResultText.value += "思考内容：\n"
                                                    aiResultText.value += jsstr.choices[0].message?.reasoning_content
                                                }
                                                aiResultText.value += "回答：\n"
                                                aiResultText.value += jsstr.choices[0].message?.content
                                            }
                                        } catch (e: Exception) {
                                            println(e.message)
                                        }
                                    },
                                    onError = { str ->
                                        aiResultText.value = "$requestFailedText$str"
                                    }
                                )
                            }



                        },
                        modifier = Modifier.weight(2f).height(40.dp), // 占据更多空间
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary // 使用主题色
                        )
                    ) {
                        Text(if (explainMode) explainText else solveText, style = TextStyle(fontSize = 14.sp))
                    }
                }
            }
        }
    }
}


// 自定义操作按钮组件
@Composable
private fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            containerColor = containerColor,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        shape = RoundedCornerShape(8.dp) // 统一圆角
    ) {
        Text(text, style = TextStyle(fontSize = 13.sp))
    }
}


@Composable
fun ConfigMiniFilterChip(
    configData: ConfigData,
    commonViewModel: CommonViewModel,
    isSelected: Boolean,
    onSelect: (ConfigData) -> Unit
) {
    FilterChip(
        modifier = Modifier.height(30.dp),
        onClick = {
            onSelect(configData)
        },
        label = {
            Text(configData.name, style = TextStyle(fontSize = 12.sp))
        },
        selected = isSelected,
        leadingIcon = if (isSelected) {
            {
                Icon(
                    imageVector = Icons.Filled.Done,
                    contentDescription = "Done icon",
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        } else {
            null
        },
    )
}

// 聊天记录条目
private data class ChatItem(val question: String, val answer: String)
