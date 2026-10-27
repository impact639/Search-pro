package com.buguagaoshu.scan.search.config

import com.buguagaoshu.scan.search.data.ConfigData
import com.buguagaoshu.scan.search.data.ScanSearchData
import kotlinx.coroutines.flow.MutableStateFlow

// 第三方分享文字事件：seq 自增用于区分重复分享，text 为分享内容
data class SharedTextEvent(val seq: Long, val text: String)

object StaticVariableConfig {
    // 是否开启读取屏幕功能
    var openScan = false
    var isInitialized = false
    // 存储读取到的屏幕数据
    var screenTextList: ArrayList<ScanSearchData> = ArrayList()

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

    // 存储配置好的 API 列表
    var apiConfigMap : MutableMap<String, ConfigData> = mutableMapOf()
    var API_MAP = "API_MAP"

    var OPTIONS_NAME = "OPTIONS_NAME"
    var API_KEY = "API_KEY"
    var BASE_URL = "BASE_URL"
    var MODEL_NAME = "MODEL_NAME"
}
