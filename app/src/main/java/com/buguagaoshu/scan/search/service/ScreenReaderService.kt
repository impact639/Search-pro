package com.buguagaoshu.scan.search.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.buguagaoshu.scan.search.config.StaticVariableConfig

/**
 * 无障碍服务：仅用于「划词」——捕获第三方应用中用户选中的文字，自动回填输入框。
 */
class ScreenReaderService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("ScanSearchShare", "无障碍服务已连接（划词可用）")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // 划词（选中文字）始终捕获，不依赖浮窗是否已打开
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
            Log.d("ScanSearchShare", "无障碍收到选区变化事件 pkg=${event.packageName}")
            handleTextSelection(event)
        }
    }

    private fun handleTextSelection(event: AccessibilityEvent) {
        // 优先用源节点：textSelectionStart/End 是专为选区设计的标准 API，最可靠。
        val source = event.source
        if (source != null) {
            try {
                val start = source.textSelectionStart
                val end = source.textSelectionEnd
                val full = source.text?.toString() ?: ""
                if (start >= 0 && end > start && end <= full.length) {
                    val selected = full.substring(start, end).trim()
                    if (selected.isNotEmpty()) {
                        Log.d("ScanSearchShare", "划词捕获(源节点) length=${selected.length}")
                        StaticVariableConfig.selectedTextFlow.value = selected
                        return
                    }
                } else {
                    Log.w("ScanSearchShare", "划词-源节点无有效选区 start=$start end=$end fullLen=${full.length}")
                }
            } catch (e: Exception) {
                Log.w("ScanSearchShare", "划词-源节点读取异常 ${e.javaClass.simpleName}:${e.message}")
            } finally {
                source.recycle()
            }
        }

        // 回退：事件自带文本 + 选区范围（个别 App 只在事件层给出选区）
        val eventText = event.text?.joinToString("") ?: ""
        val from = event.fromIndex
        val to = event.toIndex
        if (from >= 0 && to > from && to <= eventText.length) {
            val selected = eventText.substring(from, to).trim()
            if (selected.isNotEmpty()) {
                Log.d("ScanSearchShare", "划词捕获(事件级) length=${selected.length}")
                StaticVariableConfig.selectedTextFlow.value = selected
            }
        }
    }

    override fun onInterrupt() {
        // 服务中断时的处理
    }
}
