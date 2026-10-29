package com.buguagaoshu.scan.search

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
        var text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim()
        // 部分应用（个别分享面板/浏览器）把文字放在 ClipData 里而非 EXTRA_TEXT，这里兜底读取
        if (text.isNullOrBlank()) {
            val clip = intent.clipData
            if (clip != null && clip.itemCount > 0) {
                text = clip.getItemAt(0)?.coerceToText(this)?.toString()?.trim()
            }
        }
        if (text.isNullOrBlank()) {
            // 第三方应用提供的是空分享（如只分享链接或图片）：明确记录，不假装拿到了文字
            Log.w("ScanSearchShare", "收到 ACTION_SEND 但无可用文字 action=${intent.action} type=${intent.type}")
            return
        }
        // 只记长度，不记录用户选中的完整私人文段
        Log.d("ScanSearchShare", "收到第三方分享文字 length=${text.length}")
        // 可见确认：Toast 明确告知「分享已收到」，用于区分「没收到 Intent」还是「收到了但没回填」
        android.widget.Toast.makeText(
            this,
            "问一下已收到分享文字（${text.length}字）",
            android.widget.Toast.LENGTH_LONG
        ).show()
        StaticVariableConfig.postSharedText(text)
        // 延后到 onResume 之后再显示浮窗：个别系统（MagicOS/Android 16）在 onCreate 阶段
        // 显示系统悬浮窗可能被延迟或拦截；先推文字、再在主循环下一拍打开浮窗更稳。
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            FxComposeControl.openSearchWindow(application, commonViewModel)
        }
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
