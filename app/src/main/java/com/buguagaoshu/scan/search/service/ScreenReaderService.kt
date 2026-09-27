package com.buguagaoshu.scan.search.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.buguagaoshu.scan.search.config.StaticVariableConfig
import com.buguagaoshu.scan.search.data.ScanSearchData
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ScreenReaderService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!StaticVariableConfig.openScan) {
            return
        }

        // 划词：捕获用户选中的文字
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
            handleTextSelection(event)
            return
        }

        // 监听滑动事件，读取全屏文本（兜底）
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_HOVER_ENTER
        ) {
            val rootNode = rootInActiveWindow ?: return
            StaticVariableConfig.screenTextList.clear()
            traverseNodeLoop(rootNode)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun handleTextSelection(event: AccessibilityEvent) {
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
            // 划词捕获失败时忽略，不影响读屏兜底
        } finally {
            source.recycle()
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun traverseNodeLoop(node: AccessibilityNodeInfo) {
        val stack = mutableListOf<AccessibilityNodeInfo>()
        val seen = mutableSetOf<String>()
        stack.add(node)

        while (stack.isNotEmpty()) {
            val currentNode = stack.removeAt(stack.size - 1)

            val text = currentNode.text
            if (text != null) {
                val trimmed = text.toString().trim()
                // 去噪：过短、纯数字、纯标点/符号的都过滤掉，相同文本去重
                val isNoise = trimmed.length < 2 ||
                    trimmed.all { it.isDigit() } ||
                    trimmed.none { it.isLetterOrDigit() }
                if (!isNoise && seen.add(trimmed)) {
                    StaticVariableConfig.screenTextList.add(
                        ScanSearchData(Uuid.random().toString(), trimmed)
                    )
                }
            }

            for (i in currentNode.childCount - 1 downTo 0) {
                val child = currentNode.getChild(i)
                if (child != null) {
                    stack.add(child)
                }
            }
        }
    }

    override fun onInterrupt() {
        // 服务中断时的处理
    }
}
