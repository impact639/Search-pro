package com.buguagaoshu.scan.search.ui

import android.annotation.SuppressLint
import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.buguagaoshu.scan.search.ui.theme.Dimens
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
    val streamText = stringResource(TextValue.stream)
    val requestFailedText = stringResource(TextValue.request_failed)
    val modeText = stringResource(TextValue.mode)
    val explainText = stringResource(TextValue.explain)
    val solveText = stringResource(TextValue.solve)
    val noConfigTips = stringResource(TextValue.no_config_tips)
    val readScreenText = stringResource(TextValue.read_screen)
    val webText = stringResource(TextValue.web)
    val fillQuestionText = stringResource(TextValue.fill_question)
    val moreText = stringResource(TextValue.more)
    val sizeText = stringResource(TextValue.size)
    val sizeSmallText = stringResource(TextValue.size_small)
    val sizeMediumText = stringResource(TextValue.size_medium)
    val sizeLargeText = stringResource(TextValue.size_large)

    val textList = remember { mutableStateListOf<ScanSearchData>() }
    val textMap = remember { mutableStateMapOf<String, ScanSearchData>() }

    val isListVisible = remember { mutableStateOf(true) }
    val showResultVisible = remember { mutableStateOf(false) }
    val showWebView = remember { mutableStateOf(false) }
    val reasoningText = remember { mutableStateOf("") }
    val answerText = remember { mutableStateOf("") }

    val streamInfo = remember { mutableStateOf(true) }
    val moreExpanded = remember { mutableStateOf(false) }

    // 尺寸预设档位：小 / 中 / 大（改 Card 尺寸后窗口按 WRAP_CONTENT 自动重测）
    val sizePresets = listOf(
        260.dp to 480.dp,
        300.dp to 600.dp,
        340.dp to 700.dp,
    )
    val sizeLabels = listOf(sizeSmallText, sizeMediumText, sizeLargeText)
    var sizeIndex by remember { mutableStateOf(1) }

    val questionText by commonViewModel.questionText.collectAsState()
    val prompt by commonViewModel.promptText.collectAsState()
    val explainMode by commonViewModel.explainMode.collectAsState()
    val isLocked by StaticVariableConfig.isLockedFlow.collectAsState()

    val view = LocalView.current
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val alphabet = listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K")
    val configMap = commonViewModel.apiMap

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

    // 找到 cheek 为 true 的 ConfigData，否则使用第一个
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

    val onSelectConfig: (ConfigData) -> Unit = { newSelected ->
        if (newSelected == selectedConfigData) {
            selectedConfigData = null
            newSelected.cheek = false
        } else {
            selectedConfigData?.cheek = false
            selectedConfigData = newSelected
            newSelected.cheek = true
            commonViewModel.showUserSelectConfig(newSelected)
        }
        commonViewModel.updateConfig(context)
    }

    Card(
        shape = RoundedCornerShape(Dimens.RadiusLg),
        modifier = Modifier.size(
            width = sizePresets[sizeIndex].first,
            height = sizePresets[sizeIndex].second,
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            FloatingTopBar(
                title = stringResource(TextValue.float_title),
                isLocked = isLocked,
                onCollapse = { FxComposeControl.hideSearch(application) },
                onToggleLock = { FxComposeControl.updateDisplayMode() },
                onClose = { FxComposeControl.cancel() },
            )

            // 模式选择（核心，始终可见）+「更多」入口
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.SpaceSm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
            ) {
                Text(text = modeText, style = MaterialTheme.typography.labelMedium)
                FilterChip(
                    selected = explainMode,
                    onClick = { commonViewModel.setPromptMode(true) },
                    label = { Text(explainText, style = TextStyle(fontSize = 12.sp)) },
                    modifier = Modifier.height(28.dp),
                )
                FilterChip(
                    selected = !explainMode,
                    onClick = { commonViewModel.setPromptMode(false) },
                    label = { Text(solveText, style = TextStyle(fontSize = 12.sp)) },
                    modifier = Modifier.height(28.dp),
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = moreText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Dimens.RadiusSm))
                        .clickable { moreExpanded.value = !moreExpanded.value }
                        .padding(horizontal = Dimens.SpaceSm, vertical = Dimens.SpaceXs),
                )
            }

            // 更多：不常用功能（模型切换 / 流式 / 网页）
            AnimatedVisibility(
                visible = moreExpanded.value,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.SpaceSm),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        configMap.forEach { (_, value) ->
                            ConfigMiniFilterChip(
                                configData = value,
                                isSelected = value == selectedConfigData,
                                onSelect = onSelectConfig,
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = streamText, style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = streamInfo.value,
                            onCheckedChange = { streamInfo.value = it },
                            modifier = Modifier.height(28.dp),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
                    ) {
                        Text(text = sizeText, style = MaterialTheme.typography.labelMedium)
                        sizeLabels.forEachIndexed { index, label ->
                            FilterChip(
                                selected = sizeIndex == index,
                                onClick = { sizeIndex = index },
                                label = { Text(label, style = TextStyle(fontSize = 12.sp)) },
                                modifier = Modifier.height(28.dp),
                            )
                        }
                    }
                    ActionButton(
                        text = webText,
                        onClick = {
                            isListVisible.value = false
                            showResultVisible.value = false
                            showWebView.value = true
                            moreExpanded.value = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Dimens.CompactControlHeight),
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = Dimens.SpaceXs),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // 内容区：读屏列表 / 回答结果 / 网页，占满剩余空间
            ContentArea(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                isListVisible = isListVisible.value,
                showResultVisible = showResultVisible.value,
                showWebView = showWebView.value,
                textList = textList,
                textMap = textMap,
                fillQuestionText = fillQuestionText,
                onConfirm = {
                    val stringQuestion = StringBuilder()
                    for (item: ScanSearchData in textList) {
                        if (textMap[item.id]?.cheek == true) {
                            stringQuestion.append("${item.text}\n")
                        }
                    }
                    commonViewModel.updateQuestionText(stringQuestion.toString())
                    isListVisible.value = false
                    showResultVisible.value = false
                    showWebView.value = false
                },
                history = history,
                currentReasoning = reasoningText.value,
                currentAnswer = answerText.value,
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // 输入区
            OutlinedTextField(
                value = questionText,
                onValueChange = { commonViewModel.updateQuestionText(it) },
                label = { Text(stringResource(TextValue.search_tips)) },
                maxLines = 3,
                shape = RoundedCornerShape(Dimens.RadiusSm),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.SpaceSm)
                    .pointerInput(Unit) {
                        // 点击输入框即弹出键盘（不消费事件，不干扰光标/选中）
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            FxInputHelper.showKeyBoard("ScanSearch", view)
                        }
                    },
            )

            // 底部操作区
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.SpaceSm),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionButton(
                    text = readScreenText,
                    onClick = {
                        // 清除历史数据
                        textList.clear()
                        textMap.clear()

                        var i = 0
                        while (i < StaticVariableConfig.screenTextList.size) {
                            if (alphabet.contains(StaticVariableConfig.screenTextList[i].text)) {
                                if (i + 1 >= StaticVariableConfig.screenTextList.size) {
                                    textList.add(StaticVariableConfig.screenTextList[i])
                                } else {
                                    // 处理只有的情况 ABCD
                                    textList.add(
                                        ScanSearchData(
                                            StaticVariableConfig.screenTextList[i].id,
                                            "${StaticVariableConfig.screenTextList[i].text}.${StaticVariableConfig.screenTextList[i + 1].text}",
                                        ),
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
                        isListVisible.value = true
                        showWebView.value = false
                        showResultVisible.value = false
                    },
                    modifier = Modifier.weight(1f).height(Dimens.CompactControlHeight),
                )

                Button(
                    onClick = submit@{
                        // 空值校验：无可用配置或问题为空时安全退出，避免空指针
                        val config = selectedConfigData
                        if (config == null) {
                            android.widget.Toast.makeText(context, noConfigTips, android.widget.Toast.LENGTH_SHORT).show()
                            return@submit
                        }
                        if (questionText.isBlank()) {
                            return@submit
                        }

                        // 新增隐藏列表逻辑
                        isListVisible.value = false
                        showWebView.value = false
                        showResultVisible.value = true
                        // 归档上一条问答（含思考过程与最终答案）
                        if (lastQuestion.isNotBlank() && (reasoningText.value.isNotBlank() || answerText.value.isNotBlank())) {
                            history.add(ChatItem(lastQuestion, reasoningText.value, answerText.value))
                        }
                        lastQuestion = questionText
                        reasoningText.value = ""
                        answerText.value = ""

                        val sendData = SendData(
                            stream = streamInfo.value,
                            config.modelName,
                            listOf(
                                SendMessage("system", prompt),
                                SendMessage("user", questionText),
                            ),
                        )
                        val jsonConfig = Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                            coerceInputValues = true
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
                                            val delta = str.choices[0].delta
                                            val reasoning = delta?.reasoning_content
                                            if (reasoning?.isNotEmpty() == true) {
                                                reasoningText.value += reasoning
                                            }
                                            val content = delta?.content
                                            if (content?.isNotEmpty() == true) {
                                                answerText.value += content
                                            }
                                        }
                                    } catch (e: Exception) {
                                        // 忽略无法解析的行（如 [DONE]）
                                    }
                                },
                                onComplete = {
                                    // 流式结束
                                },
                                onError = { error ->
                                    answerText.value = "$requestFailedText$error"
                                },
                            )
                        } else {
                            answerText.value = "当前使用非流式输出，等待时间可能较长，请耐心等待！\n"
                            HttpControl.send(
                                sendData,
                                config.api,
                                config.apiKey,
                                onSuccess = { str ->
                                    try {
                                        val jsstr = jsonConfig.decodeFromString<ChatResponse>(str)
                                        if (jsstr.choices.isNotEmpty()) {
                                            jsstr.choices[0].message?.reasoning_content?.let {
                                                if (it.isNotBlank()) reasoningText.value = it
                                            }
                                            jsstr.choices[0].message?.content?.let {
                                                answerText.value = it
                                            }
                                        }
                                    } catch (e: Exception) {
                                        // 忽略解析异常
                                    }
                                },
                                onError = { str ->
                                    answerText.value = "$requestFailedText$str"
                                },
                            )
                        }
                    },
                    modifier = Modifier.weight(2f).height(Dimens.CompactControlHeight),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(
                        if (explainMode) explainText else solveText,
                        style = TextStyle(fontSize = 14.sp),
                    )
                }
            }
        }
    }
}

// 内容区：读屏列表 / 回答结果 / 网页 三态切换。
// 独立成 composable（而非内联在 Column 里），避免 AnimatedVisibility 被解析成
// ColumnScope 扩展导致「cannot be called in this context with an implicit receiver」。
@Composable
private fun ContentArea(
    modifier: Modifier,
    isListVisible: Boolean,
    showResultVisible: Boolean,
    showWebView: Boolean,
    textList: List<ScanSearchData>,
    textMap: MutableMap<String, ScanSearchData>,
    fillQuestionText: String,
    onConfirm: () -> Unit,
    history: List<ChatItem>,
    currentReasoning: String,
    currentAnswer: String,
) {
    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = isListVisible,
            modifier = Modifier.fillMaxSize(),
        ) {
            ScreenTextList(
                textList = textList,
                textMap = textMap,
                fillQuestionText = fillQuestionText,
                onConfirm = onConfirm,
            )
        }
        AnimatedVisibility(
            visible = showResultVisible,
            modifier = Modifier.fillMaxSize(),
        ) {
            ResultList(history = history, currentReasoning = currentReasoning, currentAnswer = currentAnswer)
        }
        AnimatedVisibility(
            visible = showWebView,
            modifier = Modifier.fillMaxSize(),
        ) {
            MyWebView()
        }
    }
}

// 顶部栏：拖拽手柄 + 标题 + 收起/锁定/关闭
@Composable
private fun FloatingTopBar(
    title: String,
    isLocked: Boolean,
    onCollapse: () -> Unit,
    onToggleLock: () -> Unit,
    onClose: () -> Unit,
) {
    Column {
        // 拖拽手柄：下滑收起回气泡
        val density = LocalDensity.current
        var dragAccum by remember { mutableFloatStateOf(0f) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { dragAccum = 0f },
                        onVerticalDrag = { _, dragAmount ->
                            dragAccum += dragAmount
                            if (dragAccum > with(density) { 60.dp.toPx() }) {
                                dragAccum = 0f
                                onCollapse()
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.SpaceXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Dimens.SpaceSm),
            )
            IconButton(onClick = onCollapse, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "收起",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onToggleLock, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    contentDescription = if (isLocked) "锁定" else "解锁",
                    tint = if (isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// 读屏结果列表：勾选后填入问题
@Composable
private fun ScreenTextList(
    textList: List<ScanSearchData>,
    textMap: MutableMap<String, ScanSearchData>,
    fillQuestionText: String,
    onConfirm: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
        ) {
            items(textList) { item ->
                Surface(
                    shape = RoundedCornerShape(Dimens.RadiusSm),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.SpaceXs),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimens.SpaceSm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.text,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = Dimens.SpaceSm),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Checkbox(
                            checked = textMap[item.id]?.cheek ?: false,
                            onCheckedChange = {
                                textMap[item.id] = item.copy(cheek = it)
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }
        }
        Button(
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.SpaceSm),
        ) {
            Text(fillQuestionText)
        }
    }
}

// 回答结果列表：历史问答 + 当前答案
@Composable
private fun ResultList(
    history: List<ChatItem>,
    currentReasoning: String,
    currentAnswer: String,
) {
    val reasoningLabel = stringResource(TextValue.reasoning_label)
    val answerLabel = stringResource(TextValue.answer_label)
    val listState = rememberLazyListState()
    // 新内容到达时自动滚动到底部，跟随最新回答（无需手动下滑）
    LaunchedEffect(history.size, currentReasoning.length, currentAnswer.length) {
        val count = listState.layoutInfo.totalItemsCount
        if (count > 0) {
            listState.scrollToItem(count - 1)
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
    ) {
        items(history) { item ->
            AnswerBlock(
                question = item.question,
                reasoning = item.reasoning,
                answer = item.answer,
                reasoningLabel = reasoningLabel,
                answerLabel = answerLabel,
            )
        }
        if (currentAnswer.isNotBlank() || currentReasoning.isNotBlank()) {
            item {
                AnswerBlock(
                    question = "",
                    reasoning = currentReasoning,
                    answer = currentAnswer,
                    reasoningLabel = reasoningLabel,
                    answerLabel = answerLabel,
                )
            }
        }
    }
}

// 单条回答：问题 + 思考过程（可选）+ 最终答案，两部分清晰分开
@Composable
private fun AnswerBlock(
    question: String,
    reasoning: String,
    answer: String,
    reasoningLabel: String,
    answerLabel: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SpaceSm),
    ) {
        if (question.isNotBlank()) {
            Text(
                text = question,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(Dimens.SpaceXs))
        }
        if (reasoning.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dimens.RadiusSm))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(Dimens.SpaceSm),
            ) {
                Text(
                    text = reasoningLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(Dimens.SpaceXs))
                AppMarkdown(markdown = reasoning)
            }
            Spacer(modifier = Modifier.height(Dimens.SpaceSm))
        }
        if (answer.isNotBlank()) {
            if (reasoning.isNotBlank()) {
                Text(
                    text = answerLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(Dimens.SpaceXs))
            }
            AppMarkdown(markdown = answer)
        }
    }
}

// 主题感知的 Markdown 渲染：代码块背景/文字随深浅色主题切换，链接高亮
@Composable
private fun AppMarkdown(markdown: String) {
    MarkdownText(
        markdown = markdown,
        isTextSelectable = true,
        linkColor = MaterialTheme.colorScheme.primary,
        syntaxHighlightColor = MaterialTheme.colorScheme.surfaceVariant,
        syntaxHighlightTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// 自定义操作按钮组件
@Composable
private fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        shape = RoundedCornerShape(Dimens.RadiusSm),
    ) {
        Text(text, style = TextStyle(fontSize = 13.sp))
    }
}

// 浮窗内的模型切换小 Chip
@Composable
fun ConfigMiniFilterChip(
    configData: ConfigData,
    isSelected: Boolean,
    onSelect: (ConfigData) -> Unit,
) {
    FilterChip(
        modifier = Modifier.height(30.dp),
        onClick = { onSelect(configData) },
        label = { Text(configData.name, style = TextStyle(fontSize = 12.sp)) },
        selected = isSelected,
        leadingIcon = if (isSelected) {
            {
                Icon(
                    imageVector = Icons.Filled.Done,
                    contentDescription = "Done icon",
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
    )
}

// 聊天记录条目
private data class ChatItem(val question: String, val reasoning: String, val answer: String)
