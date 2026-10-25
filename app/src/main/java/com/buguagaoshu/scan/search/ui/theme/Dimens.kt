package com.buguagaoshu.scan.search.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 统一设计规范：间距、圆角、控件尺寸。
 * 页面与悬浮窗统一引用这里，避免各处散落不同数值。
 */
object Dimens {
    val SpaceXs = 4.dp
    val SpaceSm = 8.dp
    val SpaceMd = 12.dp
    val SpaceLg = 16.dp
    val SpaceXl = 24.dp

    val RadiusSm = 8.dp
    val RadiusMd = 12.dp
    val RadiusLg = 16.dp
    val RadiusXl = 20.dp

    // 触控友好高度（Material 建议 48dp）
    val ControlHeight = 48.dp
    // 紧凑控件高度（悬浮窗内使用）
    val CompactControlHeight = 40.dp
    // 悬浮窗整体尺寸
    val FloatingWidth = 300.dp
    val FloatingHeight = 600.dp
}
