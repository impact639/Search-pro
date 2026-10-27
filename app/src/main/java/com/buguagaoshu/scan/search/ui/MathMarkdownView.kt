package com.buguagaoshu.scan.search.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.json.JSONObject

// 用 WebView + KaTeX 渲染 Markdown 与数学公式（$...$ 行内、$$...$$ 块级）。
// 选择 WebView 而非 Compose LaTeX 库：后者需升级整套 Kotlin/Compose/Material3 栈。
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MathMarkdownView(
    markdown: String,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageLoaded by remember { mutableStateOf(false) }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // 只允许加载本地 assets，禁止远程内容与 file:// 互访（沿用 WebView 安全策略）
                settings.allowFileAccess = true
                settings.allowContentAccess = false
                settings.allowFileAccessFromFileURLs = false
                settings.allowUniversalAccessFromFileURLs = false
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        pageLoaded = true
                    }
                }
                webView = this
                loadUrl("file:///android_asset/math.html")
            }
        },
        modifier = modifier,
    )

    // 内容或主题变化时，把 Markdown 注入 WebView 渲染（页面加载完成后才执行）
    LaunchedEffect(markdown, dark, pageLoaded) {
        if (!pageLoaded) return@LaunchedEffect
        val wv = webView ?: return@LaunchedEffect
        // 防抖：流式输出 delta 高频更新时，等 120ms 无新内容再全量渲染，
        // 避免每个 token 都重跑 markdown-it + KaTeX 造成滑动卡顿。
        delay(120)
        val js = "render(${JSONObject.quote(markdown)}, $dark)"
        wv.evaluateJavascript(js, null)
    }
}
