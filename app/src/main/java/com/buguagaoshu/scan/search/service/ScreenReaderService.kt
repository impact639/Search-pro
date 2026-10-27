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
        // 划词（选中文字）始终捕获，不依赖浮窗是否已打开，避免进程重启后 openScan 复位导致划词失效
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
            handleTextSelection(event)
            return
        }

        // 读屏（全屏文本）仅在浮窗运行中捕获，避免无谓遍历
        if (!StaticVariableConfig.openScan) {
            return
        }

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
