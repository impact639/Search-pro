package com.buguagaoshu.scan.search

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.buguagaoshu.scan.search.config.StaticVariableConfig
import com.buguagaoshu.scan.search.control.FxComposeControl
import com.buguagaoshu.scan.search.data.ConfigData
import com.buguagaoshu.scan.search.ui.CommonViewModel
import com.buguagaoshu.scan.search.ui.HomePage
import com.buguagaoshu.scan.search.ui.theme.SearchTheme
import com.buguagaoshu.scan.search.utils.PreferencesDataStoreUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json


@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val activityScope = lifecycleScope
    private val commonViewModel: CommonViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            SearchTheme {
                HomePage(commonViewModel, openFloatingWindow = {
                    FxComposeControl.install(application, commonViewModel)
                })
            }
        }
        if (!StaticVariableConfig.isInitialized) {
            StaticVariableConfig.isInitialized = true
            initData(commonViewModel)
        }

        // 处理第三方分享的文字（ACTION_SEND / text/plain）。
        // 不再每次启动强制跳转无障碍设置页，避免打断分享流程（分享文字「消失」的直接原因）。
        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    // 接收第三方分享的文字，推送给悬浮窗输入框
    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        if (intent.type?.startsWith("text/") != true) return
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim()
        if (text.isNullOrBlank()) return
        StaticVariableConfig.postSharedText(text)
        // 打开主浮窗（已安装则显示），让用户看到填入的问题
        FxComposeControl.openSearchWindow(application, commonViewModel)
    }

    private fun initData(commonViewModel: CommonViewModel) {
        println("初始化数据")
        // 使用lifecycleScope替代临时CoroutineScope
        activityScope.launch {
            // 初始化配置
            launch(Dispatchers.IO) { collectApiMap(commonViewModel) }
        }
    }

    private suspend fun collectApiMap(commonViewModel: CommonViewModel) {
        println("开始读取设置选项")
        try {
            PreferencesDataStoreUtils.readString(
                application,
                StaticVariableConfig.API_MAP
            ).collect { value ->
                var configMap : MutableMap<String, ConfigData> = mutableMapOf()
                if (value == null || value.isBlank()) {
                    return@collect
                }

                try {
                    configMap = Json.decodeFromString<MutableMap<String, ConfigData>>(value)
                    commonViewModel.addAllApiMap(configMap)
                } catch (e: Exception) {
                    return@collect
                }
            }
        } catch (e: Exception) {
            println("读取设置选项失败: ${e.message}")
        }
    }
}
