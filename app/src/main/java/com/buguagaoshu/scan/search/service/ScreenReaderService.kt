package com.buguagaoshu.scan.search.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.buguagaoshu.scan.search.config.StaticVariableConfig

/**
 * 无障碍服务：仅用于「划词」——捕获第三方应用中用户选中的文字，自动回填输入框。
 *
 * 已删除「读屏」（全屏文本遍历并展示列表）功能，只保留选中文字导入这一必要能力。
 */
class ScreenReaderService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // 划词（选中文字）始终捕获，不依赖浮窗是否已打开，避免进程重启后状态复位导致划词失效
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
            handleTextSelection(event)
        }
    }

    private fun handleTextSelection(event: AccessibilityEvent) {
        // 方式一：事件自带的文本 + 选区范围（getFromIndex/getToIndex 专为选区事件设计，API 26+）
        val eventText = event.text?.joinToString("") ?: ""
        val from = event.fromIndex
        val to = event.toIndex
        if (from >= 0 && to > from && to <= eventText.length) {
            val selected = eventText.substring(from, to).trim()
            if (selected.isNotEmpty()) {
                StaticVariableConfig.selectedTextFlow.value = selected
                return
            }
        }

        // 方式二：从事件源节点读取选中文字（部分 App 只在这一层给出选区）
        val source = event.source ?: return
        try {
            val start = source.textSelectionStart
            val end = source.textSelectionEnd
            val full = source.text?.toString() ?: ""
            if (start >= 0 && end > start && end <= full.length) {
                val selected = full.substring(start, end).trim()
                if (selected.isNotEmpty()) {
                    StaticVariableConfig.selectedTextFlow.value = selected
                }
            }
        } catch (e: Exception) {
            // 划词捕获失败时忽略
        } finally {
            source.recycle()
        }
    }

    override fun onInterrupt() {
        // 服务中断时的处理
    }
}
