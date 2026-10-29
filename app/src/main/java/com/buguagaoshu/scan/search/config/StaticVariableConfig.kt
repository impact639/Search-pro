package com.buguagaoshu.scan.search.config

import com.buguagaoshu.scan.search.data.ConfigData
import kotlinx.coroutines.flow.MutableStateFlow

// 第三方分享文字事件：seq 自增用于区分重复分享，text 为分享内容
data class SharedTextEvent(val seq: Long, val text: String)

object StaticVariableConfig {
    var isInitialized = false

    // 划词：无障碍捕获到的选中文字，推送给悬浮窗
    val selectedTextFlow = MutableStateFlow<String>("")

    // 第三方分享文字：MainActivity 收到 ACTION_SEND 后推入，悬浮窗监听后填入/追加到输入框
    val sharedTextFlow = MutableStateFlow<SharedTextEvent?>(null)
    private var sharedTextSeq = 0L

    fun postSharedText(text: String) {
        if (text.isBlank()) return
        sharedTextFlow.value = SharedTextEvent(++sharedTextSeq, text)
    }

    // 锁定状态：主浮窗固定(锁定)还是可拖动(解锁)，供悬浮窗图标同步
    val isLockedFlow = MutableStateFlow(true)

    // 框选截图的 base64（JPEG）。配合视觉模型做图片理解/公式识别；非视觉模型时忽略。
    // 每次框选识别成功后覆盖；提交一次后清空，避免把旧图附到无关的新问题上。
    val capturedImageFlow = MutableStateFlow<String?>(null)

    fun postCapturedImage(base64: String?) {
        capturedImageFlow.value = base64
    }

    // 依据模型名启发式判断是否支持图片（vision）输入。仅作能力探测：
    // 不确定时返回 false，宁可退回 OCR 文字，也不假装能识别图片。DeepSeek 文本模型不在列表内，正确返回 false。
    fun supportsVision(modelName: String): Boolean {
        val m = modelName.lowercase()
        return listOf(
            "vl", "vision", "gpt-4o", "gpt-4.1", "gpt-4.5", "gpt-5",
            "o1", "o3", "o4", "claude", "gemini", "qwen-vl", "glm-4v",
            "llava", "minicpm-v", "internvl", "pixtral", "step-1v", "moonshot-vl",
        ).any { m.contains(it) }
    }

    // 存储配置好的 API 列表
    var apiConfigMap : MutableMap<String, ConfigData> = mutableMapOf()
    var API_MAP = "API_MAP"

    var OPTIONS_NAME = "OPTIONS_NAME"
    var API_KEY = "API_KEY"
    var BASE_URL = "BASE_URL"
    var MODEL_NAME = "MODEL_NAME"
}
