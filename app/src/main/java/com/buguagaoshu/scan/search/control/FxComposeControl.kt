package com.buguagaoshu.scan.search.control

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.buguagaoshu.scan.search.config.StaticVariableConfig
import com.buguagaoshu.scan.search.ui.CommonViewModel
import com.buguagaoshu.scan.search.ui.FloatingWindowContent
import com.petterp.floatingx.FloatingX
import com.petterp.floatingx.assist.FxDisplayMode
import com.petterp.floatingx.assist.FxGravity
import com.petterp.floatingx.assist.FxScopeType
import com.petterp.floatingx.compose.enableComposeSupport

object FxComposeControl {
    private val _TAG = "ScanSearch"

    private val _minimize_tag = "minimize"

    private var hasMove = false

    fun updateDisplayMode() {
        if (hasMove) {
            // 锁定：固定窗口，不可拖动
            hasMove = false
            FloatingX.configControl(_TAG).setDisplayMode(FxDisplayMode.ClickOnly)
            StaticVariableConfig.isLockedFlow.value = true
        } else {
            // 解锁：可拖动窗口位置 / 拖到边缘吸附
            hasMove = true
            FloatingX.configControl(_TAG).setDisplayMode(FxDisplayMode.Normal)
            StaticVariableConfig.isLockedFlow.value = false
        }
    }

    fun hideSearch(context: Application) {
        FloatingX.controlOrNull(_TAG)?.hide()
        installMinimize(context)
    }

    // 打开主浮窗：未安装则安装，已安装（可能收起为气泡）则显示；同时隐藏小气泡
    fun openSearchWindow(context: Application, commonViewModel: CommonViewModel) {
        install(context, commonViewModel)
        FloatingX.controlOrNull(_TAG)?.show()
        FloatingX.controlOrNull(_minimize_tag)?.hide()
    }

    // 仅显示已安装的主浮窗并隐藏小气泡（OCR 回填后使用，无需 CommonViewModel）
    fun showSearchWindow() {
        FloatingX.controlOrNull(_TAG)?.show()
        FloatingX.controlOrNull(_minimize_tag)?.hide()
    }

    fun cancel() {
        // 关闭无障碍服务
        StaticVariableConfig.openScan = false
        // 取消进行中的模型请求
        HttpControl.cancel()
        FloatingX.controlOrNull(_TAG)?.cancel()
        FloatingX.controlOrNull(_minimize_tag)?.cancel()
    }

    fun installMinimize(context: Application) {
        if (!FloatingX.isInstalled(_minimize_tag)) {
            // 开启无障碍服务，读取数据
            StaticVariableConfig.openScan = true

            FloatingX.install {
                setContext(context)
                setTag(_minimize_tag)
                // system浮窗必须调用此方法，才可以启用Compose支持
                enableComposeSupport()
                setScopeType(FxScopeType.SYSTEM)
                setGravity(FxGravity.LEFT_OR_TOP)
                setDisplayMode(FxDisplayMode.Normal)

                setLayoutView(
                    ComposeView(context).apply {
                        setContent {
                            // 常驻小气泡：贴边圆形，点击展开主浮窗
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                                    .clickable {
                                        FloatingX.controlOrNull(_TAG)?.show()
                                        FloatingX.controlOrNull(_minimize_tag)?.hide()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "问",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                )
                setEnableLog(false)
                setEdgeOffset(20f)
                setEnableEdgeAdsorption(true)
            }.show()
        } else {
            FloatingX.controlOrNull(_minimize_tag)?.show()
        }
    }

    fun install(context: Application, commonViewModel: CommonViewModel) {
        if (!FloatingX.isInstalled(_TAG)) {
            // 开启无障碍服务，读取数据
            StaticVariableConfig.openScan = true

            FloatingX.install {
                setContext(context)
                setTag(_TAG)
                // system浮窗必须调用此方法，才可以启用Compose支持
                enableComposeSupport()
                setScopeType(FxScopeType.SYSTEM)
                setGravity(FxGravity.LEFT_OR_TOP)
                setDisplayMode(FxDisplayMode.ClickOnly)

                setLayoutView(
                    ComposeView(context).apply {
                        setContent {
                            FloatingWindowContent(commonViewModel)
                        }
                    }
                )
                setEnableLog(false)
                setEdgeOffset(20f)
                setEnableEdgeAdsorption(true)
            }.show()
        } else {
            // TODO 增加消息框
        }
    }
}
