package com.buguagaoshu.scan.search.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.buguagaoshu.scan.search.ScreenCaptureActivity
import com.buguagaoshu.scan.search.config.StaticVariableConfig
import com.buguagaoshu.scan.search.control.FxComposeControl
import com.buguagaoshu.scan.search.control.HttpControl
import com.buguagaoshu.scan.search.data.ChatResponse
import com.buguagaoshu.scan.search.data.ConfigData
import com.buguagaoshu.scan.search.data.SendData
import com.buguagaoshu.scan.search.data.SendMessage
import com.buguagaoshu.scan.search.service.ScreenCaptureService
import com.buguagaoshu.scan.search.ui.theme.Dimens
import com.petterp.floatingx.util.FxInputHelper
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
    val noConfigTips = stringResource(TextValue.no_config_tips)
    val captureScreenText = stringResource(TextValue.capture_screen)
    val webText = stringResource(TextValue.web)
    val moreText = stringResource(TextValue.more)
    val sizeText = stringResource(TextValue.size)
    val sizeSmallText = stringResource(TextValue.size_small)
    val sizeMediumText = stringResource(TextValue.size_medium)
    val sizeLargeText = stringResource(TextValue.size_large)

    val showResultVisible = remember { mutableStateOf(false) }
    val showWebView = remember { mutableStateOf(false) }
    val answerText = remember { mutableStateOf("") }
    // 请求进行中（首字到达前显示加载提示）
    var loading by remember { mutableStateOf(false) }

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
    val isLocked by StaticVariableConfig.isLockedFlow.collectAsState()

    val view = LocalView.current
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val configMap = commonViewModel.apiMap

    // 划词：监听无障碍捕获的选中文字，自动填入问题框
    val selectedText by StaticVariableConfig.selectedTextFlow.collectAsState()
    LaunchedEffect(selectedText) {
        if (selectedText.isNotBlank()) {
            // 划词语义是「替换」而非「追加」：每次新选区整体覆盖旧内容。
            // 追加会把手柄拖动过程中的中间选区（he→hello→hello world）拼成乱码，这是回归根因。
            commonViewModel.updateQuestionText(selectedText)
            Log.d("ScanSearchShare", "划词文字已填入输入框 length=${selectedText.length}")
            android.widget.Toast.makeText(context, "已填入选中文字", android.widget.Toast.LENGTH_SHORT).show()
            // 消费后复位：selectedTextFlow 是无障碍服务的原始 StateFlow，不复位的话
            // 关闭浮窗再打开（重新组合）时会把旧选区再次填进去，破坏「重开默认清空」。
            StaticVariableConfig.selectedTextFlow.value = ""
        }
    }

    // 第三方分享文字：自动填入输入框；已有未提交内容时改为追加，不覆盖
    val sharedEvent by StaticVariableConfig.sharedTextFlow.collectAsState()
    LaunchedEffect(sharedEvent) {
        val ev = sharedEvent ?: return@LaunchedEffect
        if (ev.text.isBlank()) return@LaunchedEffect
        val current = commonViewModel.questionText.value
        commonViewModel.updateQuestionText(
            if (current.isBlank()) ev.text
            else current.trimEnd() + "\n" + ev.text
        )
        Log.d("ScanSearchShare", "分享文字已填入输入框 seq=${ev.seq} length=${ev.text.length}")
        android.widget.Toast.makeText(context, "已填入输入框", android.widget.Toast.LENGTH_SHORT).show()
        // 消费后复位为 null：避免关闭重开（重新组合）时把上一次的分享事件再次填入
        StaticVariableConfig.sharedTextFlow.value = null
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
                onClose = {
                    FxComposeControl.cancel()
                    // 关闭（X）时清空待发送输入：下次打开默认空白。
                    // 只清 questionText，不动 answerText/history；也不在这里清 selectedText/sharedText，
                    // 因为那两份已在上方 LaunchedEffect 消费后复位，避免把「刚收到的分享/OCR」误清掉。
                    commonViewModel.clearQuestionText()
                },
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
                // 仅保留「解释」模式（已删除「解题」模式，输入区始终按解释 prompt 处理）
                Text(
                    text = explainText,
                    style = TextStyle(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.primary,
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

            // 内容区：回答结果 / 网页，占满剩余空间
            ContentArea(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                showResultVisible = showResultVisible.value,
                showWebView = showWebView.value,
                history = history,
                currentAnswer = answerText.value,
                loading = loading,
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
                    text = captureScreenText,
                    onClick = {
                        moreExpanded.value = false
                        Log.d("ScanSearchCapture", "[阶段0-用户点击识屏] 收起浮窗并启动前台服务")
                        // 先收起浮窗避免被截进图里
                        FxComposeControl.hideSearch(application)
                        // 后台悬浮窗直接 startActivity 会被 Android 10+ 静默拦截（点了没反应），
                        // 改由前台服务豁免限制后拉起识屏界面（服务在拉起阶段用 specialUse 类型，拿到授权后再升级 mediaProjection）
                        val serviceIntent = Intent(context, ScreenCaptureService::class.java).apply {
                            action = ScreenCaptureService.ACTION_LAUNCH
                        }
                        try {
                            context.startForegroundService(serviceIntent)
                        } catch (e: Exception) {
                            Log.e("ScanSearchCapture", "[阶段0-startForegroundService失败] 走兜底直接 startActivity：${e.javaClass.name}：${e.message}", e)
                            // 兜底：个别系统禁止后台启动前台服务时，直接尝试启动识屏 Activity
                            val intent = Intent(context, ScreenCaptureActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.weight(1f).height(Dimens.CompactControlHeight),
                )

                Button(
                    onClick = submit@{
                        // 空值校验：无可用配置时安全退出，避免空指针
                        val config = selectedConfigData
                        if (config == null) {
                            android.widget.Toast.makeText(context, noConfigTips, android.widget.Toast.LENGTH_SHORT).show()
                            return@submit
                        }

                        // 框选截图 + 视觉模型能力探测：只有模型支持图片输入才把截图一起发，
                        // 否则仅用 OCR 文字（不假装能理解图片，也不擅自切换模型）。
                        // 先于空校验计算，因为纯图表/公式选区可能 OCR 出空文字，但只要带了截图且模型支持视觉就允许提交。
                        val capturedImage = StaticVariableConfig.capturedImageFlow.value
                        val supportsVision = StaticVariableConfig.supportsVision(config.modelName)
                        val imageBase64 = if (capturedImage != null && supportsVision) capturedImage else null
                        if (capturedImage != null && !supportsVision) {
                            Log.d("ScanSearchCapture", "当前模型 ${config.modelName} 不支持图片输入，忽略框选截图，仅用 OCR 文字")
                        }

                        // 空校验：既没有文字、也没有可用的截图时退出
                        if (questionText.isBlank() && imageBase64 == null) {
                            return@submit
                        }

                        // 取消上一次尚未结束的请求，避免串流/重复回答
                        HttpControl.cancel()
                        val t0 = System.currentTimeMillis()
                        loading = true

                        showWebView.value = false
                        showResultVisible.value = true
                        // 归档上一条问答（含详细讲解与最终答案）
                        if (lastQuestion.isNotBlank() && answerText.value.isNotBlank()) {
                            history.add(ChatItem(lastQuestion, answerText.value))
                        }
                        lastQuestion = questionText
                        answerText.value = ""

                        if (imageBase64 != null) {
                            Log.d("ScanSearchCapture", "视觉模型 ${config.modelName}，图片+文字一起发送 base64长度=${imageBase64.length}")
                            // 截图只用于本次提交，用完即清，避免把旧图附到后续无关问题上
                            StaticVariableConfig.postCapturedImage(null)
                        }

                        // 有截图时，把「图片已附带」这件事写进 user 文案，提示视觉模型看图。
                        // 纯图表时 questionText 可能为空，仍给出明确指令，避免模型对着空文本发呆。
                        val userText = if (imageBase64 != null) {
                            if (questionText.isBlank()) {
                                "下面附了一张框选截图，请识别并解释图中的内容（可能是数学公式、图表、几何图或流程图）。"
                            } else {
                                "下面附了一张框选截图，请结合图片内容回答我的问题：\n$questionText"
                            }
                        } else {
                            questionText
                        }
                        val sendData = SendData(
                            stream = streamInfo.value,
                            config.modelName,
                            listOf(
                                SendMessage("system", prompt),
                                SendMessage("user", userText),
                            ),
                        )
                        val jsonConfig = Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                            coerceInputValues = true
                        }
                        Log.d("ScanSearchTime", "[本地请求准备完成] ${System.currentTimeMillis() - t0}ms")
                        // 首个有效回答文字是否已到达（用于记录「首字耗时」）
                        var firstContent = true
                        // 统计解析失败的 SSE 块，用于排查乱码（判断是数据层还是展示层问题）
                        var parseFailCount = 0

                        if (streamInfo.value) {
                            HttpControl.sendStream(
                                sendData,
                                config.api,
                                config.apiKey,
                                imageBase64,
                                onChunkReceived = { payload ->
                                    // payload 已是去掉 data: 前缀的 JSON，直接解析
                                    try {
                                        val str = jsonConfig.decodeFromString<ChatResponse>(payload)
                                        if (str.choices.isNotEmpty()) {
                                            val delta = str.choices[0].delta
                                            // reasoning_content 为模型内部推理，不再展示
                                            val content = delta?.content
                                            if (!content.isNullOrEmpty()) {
                                                if (firstContent) {
                                                    firstContent = false
                                                    loading = false
                                                    Log.d("ScanSearchTime", "[首个有效文字] ${System.currentTimeMillis() - t0}ms")
                                                }
                                                answerText.value += content
                                            }
                                        }
                                    } catch (e: Exception) {
                                        // 诊断：解析失败的块往往就是乱码来源（如字段类型不匹配），
                                        // 只记录失败块的载荷前缀与异常类型，不含 API Key。
                                        parseFailCount++
                                        Log.w(
                                            "ScanSearchParse",
                                            "[解析失败块#$parseFailCount] ${e.javaClass.simpleName}：${e.message} payload前120字=${payload.take(120)}"
                                        )
                                    }
                                },
                                onComplete = {
                                    loading = false
                                    Log.d("ScanSearchTime", "[完整回答结束] ${System.currentTimeMillis() - t0}ms")
                                    Log.d("ScanSearchParse", "[回答汇总] 累计长度=${answerText.value.length} 解析失败块数=$parseFailCount")
                                },
                                onError = { error ->
                                    loading = false
                                    answerText.value = "$requestFailedText$error"
                                },
                            )
                        } else {
                            HttpControl.send(
                                sendData,
                                config.api,
                                config.apiKey,
                                imageBase64,
                                onSuccess = { str ->
                                    loading = false
                                    Log.d("ScanSearchTime", "[完整回答结束] ${System.currentTimeMillis() - t0}ms")
                                    try {
                                        val jsstr = jsonConfig.decodeFromString<ChatResponse>(str)
                                        if (jsstr.choices.isNotEmpty()) {
                                            // reasoning_content 为模型内部推理，不再展示，仅取 content
                                            jsstr.choices[0].message?.content?.let {
                                                answerText.value = it
                                            }
                                        }
                                    } catch (e: Exception) {
                                        // 忽略解析异常
                                    }
                                },
                                onError = { str ->
                                    loading = false
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
                        explainText,
                        style = TextStyle(fontSize = 14.sp),
                    )
                }
            }
        }
    }
}

// 内容区：回答结果 / 网页 两态切换。
// 独立成 composable（而非内联在 Column 里），避免 AnimatedVisibility 被解析成
// ColumnScope 扩展导致「cannot be called in this context with an implicit receiver」。
@Composable
private fun ContentArea(
    modifier: Modifier,
    showResultVisible: Boolean,
    showWebView: Boolean,
    history: List<ChatItem>,
    currentAnswer: String,
    loading: Boolean,
) {
    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = showResultVisible,
            modifier = Modifier.fillMaxSize(),
        ) {
            ResultList(history = history, currentAnswer = currentAnswer, loading = loading)
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

// 回答结果：把历史问答 + 当前答案拼成 Markdown，用 WebView + KaTeX 渲染（支持数学公式）
@Composable
private fun ResultList(
    history: List<ChatItem>,
    currentAnswer: String,
    loading: Boolean,
) {
    val explanationLabel = stringResource(TextValue.explanation_label)
    val answerLabel = stringResource(TextValue.answer_label)
    val generatingText = stringResource(TextValue.generating)
    val dark = isSystemInDarkTheme()
    val markdown = remember(history, currentAnswer, explanationLabel, answerLabel) {
        buildConversationMarkdown(history, currentAnswer, explanationLabel, answerLabel)
    }
    Column(modifier = Modifier.fillMaxSize()) {
        if (loading && currentAnswer.isBlank()) {
            Text(
                text = generatingText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Dimens.SpaceSm, vertical = Dimens.SpaceXs),
            )
        }
        MathMarkdownView(
            markdown = markdown,
            dark = dark,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

// 拼装整段会话的 Markdown（问题 + 详细讲解 + 最终答案，用 --- 分隔）
private fun buildConversationMarkdown(
    history: List<ChatItem>,
    currentAnswer: String,
    explanationLabel: String,
    answerLabel: String,
): String {
    val sb = StringBuilder()
    for (item in history) {
        if (item.question.isNotBlank()) {
            sb.append("**问题**：").append(item.question).append("\n\n")
        }
        appendAnswerBlocks(sb, item.answer, explanationLabel, answerLabel)
        sb.append("---\n\n")
    }
    appendAnswerBlocks(sb, currentAnswer, explanationLabel, answerLabel)
    return sb.toString()
}

// 把一条回答按「【最终答案】」标记拆成 详细讲解 + 最终答案 两块
private fun appendAnswerBlocks(
    sb: StringBuilder,
    answer: String,
    explanationLabel: String,
    answerLabel: String,
) {
    if (answer.isBlank()) return
    val (explanation, conclusion) = splitAnswer(answer)
    if (explanation.isNotBlank()) {
        sb.append("**").append(explanationLabel).append("**：\n\n").append(explanation).append("\n\n")
    }
    if (conclusion.isNotBlank()) {
        sb.append("**").append(answerLabel).append("**：\n\n").append(conclusion).append("\n\n")
    }
}

// 拆分回答：找到「【最终答案】」标记，之前为讲解、之后为结论；无标记时整段作为讲解
private fun splitAnswer(answer: String): Pair<String, String> {
    val idx = answer.indexOf(FINAL_ANSWER_MARKER)
    if (idx < 0) return answer to ""
    val explanation = answer.substring(0, idx).trim()
    val conclusion = answer.substring(idx + FINAL_ANSWER_MARKER.length)
        .trimStart(':', '：', ' ', '-', '\n')
        .trim()
    return explanation to conclusion
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
private data class ChatItem(val question: String, val answer: String)

// 最终答案标记：模型在回答末尾用它区分「详细讲解」与「最终答案」
private const val FINAL_ANSWER_MARKER = "【最终答案】"
