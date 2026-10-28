package com.buguagaoshu.scan.search

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.buguagaoshu.scan.search.config.StaticVariableConfig
import com.buguagaoshu.scan.search.control.FxComposeControl
import com.buguagaoshu.scan.search.service.ScreenCaptureService
import com.buguagaoshu.scan.search.ui.theme.SearchTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 屏幕框选 + OCR。
 *
 * 流程：请求屏幕捕获授权 → 捕获当前屏幕一帧 → 全屏显示截图并框选区域 → ML Kit 中文识别 →
 * 结果回填悬浮窗输入框。为满足 Android 14 的 MediaProjection 前台服务要求，捕获前会启动
 * [ScreenCaptureService]。
 */
class ScreenCaptureActivity : ComponentActivity() {

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var bitmap by mutableStateOf<Bitmap?>(null)
    private var statusText by mutableStateOf("正在请求屏幕捕获授权…")
    private var error by mutableStateOf(false)

    // 诊断日志：只记录截屏流程各阶段状态，不含任何用户隐私/密钥
    private val tag = "ScanSearchCapture"
    private var t0 = 0L

    // 资源是否已清理。图像回调与清理函数都投递在 main Handler 上，靠这个标志保证
    // 清理后仍排队中的图像回调不会在已 close 的 ImageReader 上继续操作（避免崩溃）。
    private var cleanedUp = false

    // 延迟启动捕获的 Runnable，存成字段便于 onDestroy 时取消，避免销毁后仍持有 Activity
    private var startCaptureRunnable: Runnable? = null

    // 截图超时兜底：授权成功但长时间拿不到帧（系统未生成画面 / 投影失败）时，
    // 显示错误页而非一直停留在透明窗口（用户看到的「卡住」）。
    private val captureTimeout = Runnable {
        if (bitmap == null && !error && !cleanedUp) {
            Log.w(tag, "[超时] 3 秒未收到图像回调，判定为「VirtualDisplay 未出帧」")
            error = true
            statusText = "截图超时，请重试"
            cleanup()
        }
    }

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(tag, "[授权返回] resultCode=${result.resultCode} dataNull=${result.data == null} 耗时=${System.currentTimeMillis() - t0}ms")
        if (result.resultCode == RESULT_OK && result.data != null) {
            startScreenCapture(result.resultCode, result.data!!)
        } else {
            Log.w(tag, "[授权返回] 用户未授予屏幕捕获权限")
            Toast.makeText(this, "未授予屏幕捕获权限，无法框选", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(tag, "[onCreate] SDK=${Build.VERSION.SDK_INT} 厂商=${Build.MANUFACTURER} 型号=${Build.MODEL} 分辨率=${resources.displayMetrics.widthPixels}x${resources.displayMetrics.heightPixels} densityDpi=${resources.displayMetrics.densityDpi}")
        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        setContent {
            SearchTheme {
                ScreenCaptureScreen(
                    bitmap = bitmap,
                    statusText = statusText,
                    error = error,
                    onCrop = { rect ->
                        val bmp = bitmap
                        if (bmp != null) {
                            runOcr(cropBitmap(bmp, rect))
                        }
                    },
                    onRetry = { requestCapture() },
                    onCancel = { finish() },
                )
            }
        }
        requestCapture()
    }

    private fun requestCapture() {
        t0 = System.currentTimeMillis()
        bitmap = null
        error = false
        statusText = "正在请求屏幕捕获授权…"
        Log.d(tag, "[阶段1-请求授权] 启动系统屏幕捕获授权弹窗")
        captureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    private fun startScreenCapture(resultCode: Int, data: Intent) {
        statusText = "正在捕获屏幕…"
        Log.d(tag, "[阶段2-启动前台服务] SDK=${Build.VERSION.SDK_INT}")

        // Android 14+ 必须先有 mediaProjection 前台服务在运行。startForegroundService 是异步的，
        // 把真正的捕获 post 到下一轮主线程循环，确保服务已调用 startForeground 后再 getMediaProjection，
        // 避免 SecurityException（后台悬浮窗路径下前台服务可能尚未就绪）。
        val serviceIntent = Intent(this, ScreenCaptureService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Log.d(tag, "[阶段2-启动前台服务] startForegroundService 调用成功")
        } catch (e: Exception) {
            Log.e(tag, "[阶段2-启动前台服务失败] ${e.javaClass.name}：${e.message}", e)
        }

        // 延迟到下一轮主线程，并给前台服务足够时间完成 startForeground，
        // 避免 getMediaProjection 抛 SecurityException（表现为直接弹回桌面）
        startCaptureRunnable = Runnable { startCapture(resultCode, data) }
        Handler(Looper.getMainLooper()).postDelayed(startCaptureRunnable!!, 300)
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        try {
            Log.d(tag, "[阶段5-获取MediaProjection] 开始调用 getMediaProjection")
            val mp = mediaProjectionManager.getMediaProjection(resultCode, data)
            if (mp == null) {
                Log.w(tag, "[阶段5-获取MediaProjection失败] 返回 null")
                error = true
                statusText = "无法获取屏幕捕获权限，请重试"
                cleanup()
                return
            }
            mediaProjection = mp
            Log.d(tag, "[阶段5-获取MediaProjection成功] 耗时=${System.currentTimeMillis() - t0}ms")
            beginCapture()
        } catch (e: SecurityException) {
            // 前台服务未就绪等系统原因。注意：Android 14 的授权 resultData 是一次性的，
            // 不能拿同一个 data 再次 getMediaProjection（第二次会抛 IllegalStateException），
            // 因此这里不再「延迟重试」，直接提示用户重试——重试会重新走授权流程拿到新 token。
            Log.e(tag, "[阶段5-SecurityException] ${e.message}（不复用 resultData 重试）", e)
            error = true
            statusText = "屏幕捕获权限异常，请重试"
            cleanup()
        } catch (e: Exception) {
            Log.e(tag, "[阶段5-获取MediaProjection失败] ${e.javaClass.name}：${e.message}", e)
            error = true
            statusText = "屏幕捕获失败，请重试"
            cleanup()
        }
    }

    private fun beginCapture() {
        val mp = mediaProjection ?: run {
            error = true
            statusText = "无法获取屏幕捕获权限，请重试"
            return
        }
        cleanedUp = false
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.d(tag, "[投影被停止] MediaProjection.onStop 触发")
                cleanup()
            }
        }, Handler(Looper.getMainLooper()))

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        try {
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            Log.d(tag, "[阶段6-创建ImageReader] 尺寸=${width}x${height} 格式=RGBA_8888")

            virtualDisplay = mp.createVirtualDisplay(
                "screen-capture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                null,
            )
            Log.d(tag, "[阶段7-创建VirtualDisplay] 结果=${if (virtualDisplay != null) "成功" else "返回 null"} density=$density")
            if (virtualDisplay == null) {
                // createVirtualDisplay 返回 null（常见于 Android 14 选了「单个应用」且选到自己）：
                // 不会有帧产生，直接失败，不必等 3 秒超时。
                Log.e(tag, "[阶段7-创建VirtualDisplay失败] 返回 null，判定为投影创建失败")
                error = true
                statusText = "屏幕捕获失败，请重试"
                cleanup()
                return
            }

            imageReader?.setOnImageAvailableListener({ reader ->
                // 清理后可能仍有已入队的回调：此时 ImageReader 已 close，直接跳过
                if (cleanedUp) return@setOnImageAvailableListener
                Log.d(tag, "[阶段8-图像回调] 触发，耗时=${System.currentTimeMillis() - t0}ms")
                val image = try {
                    reader.acquireLatestImage()
                } catch (e: Exception) {
                    Log.e(tag, "[阶段8-获取图像失败] ${e.javaClass.name}：${e.message}", e)
                    null
                }
                Log.d(tag, "[阶段9-获取图像] acquireLatestImage 返回=${image != null}")
                if (image == null) {
                    // 首帧常未就绪：不清理、也不取消超时，等下一帧或 3 秒超时兜底，
                    // 避免把「暂无帧」误判成失败而提前清理。
                    Log.w(tag, "[阶段9-获取图像] 图像为 null，继续等待后续帧")
                    return@setOnImageAvailableListener
                }
                // 拿到有效帧才取消超时
                Handler(Looper.getMainLooper()).removeCallbacks(captureTimeout)
                try {
                    bitmap = imageToBitmap(image)
                    statusText = ""
                    Log.d(tag, "[阶段9-转Bitmap成功] ${bitmap?.width}x${bitmap?.height}，耗时=${System.currentTimeMillis() - t0}ms")
                } catch (t: Throwable) {
                    Log.e(tag, "[阶段9-转Bitmap失败] 异常类型=${t.javaClass.name}：${t.message}", t)
                    error = true
                    statusText = "截图失败：${t.javaClass.simpleName} ${t.message}"
                } finally {
                    image.close()
                    cleanup()
                }
            }, Handler(Looper.getMainLooper()))

            // 兜底：3 秒内拿不到帧则提示重试，避免透明窗口无限「卡住」
            Handler(Looper.getMainLooper()).postDelayed(captureTimeout, 3000)
        } catch (e: Exception) {
            Log.e(tag, "[阶段6/7-创建ImageReader/VirtualDisplay失败] ${e.javaClass.name}：${e.message}", e)
            error = true
            statusText = "屏幕捕获失败：${e.message}"
            cleanup()
        }
    }

    // ImageReader(RGBA_8888) → Bitmap；处理行 stride 填充
    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val paddedWidth = image.width + rowPadding / pixelStride
        buffer.rewind() // copyPixelsFromBuffer 从 current position 读，先归零避免个别设备读偏
        val bmp = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(buffer)
        return if (paddedWidth == image.width) bmp
        else Bitmap.createBitmap(bmp, 0, 0, image.width, image.height)
    }

    private fun cropBitmap(src: Bitmap, rect: Rect): Bitmap {
        val left = rect.left.coerceIn(0, src.width - 1)
        val top = rect.top.coerceIn(0, src.height - 1)
        val right = rect.right.coerceIn(left + 1, src.width)
        val bottom = rect.bottom.coerceIn(top + 1, src.height)
        return Bitmap.createBitmap(src, left, top, right - left, bottom - top)
    }

    private fun runOcr(bitmap: Bitmap) {
        statusText = "正在识别文字…"
        val ocrT0 = System.currentTimeMillis()
        Log.d(tag, "[阶段12-OCR开始] 裁剪尺寸=${bitmap.width}x${bitmap.height}")
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                recognizer.close()
                val text = visionText.text?.trim() ?: ""
                Log.d(tag, "[阶段12-OCR成功] 识别到 ${text.length} 字符，耗时=${System.currentTimeMillis() - ocrT0}ms")
                if (text.isBlank()) {
                    Log.w(tag, "[阶段12-OCR结果为空]")
                    statusText = "未识别到文字，请重新框选"
                } else {
                    // 复用统一文本导入通道，回填悬浮窗输入框
                    StaticVariableConfig.postSharedText(text)
                    FxComposeControl.showSearchWindow()
                    finish()
                }
            }
            .addOnFailureListener { e ->
                recognizer.close()
                Log.e(tag, "[阶段12-OCR失败] ${e.javaClass.name}：${e.message}", e)
                statusText = "识别失败：${e.message}"
            }
    }

    private fun cleanup() {
        if (cleanedUp) return
        cleanedUp = true
        Handler(Looper.getMainLooper()).removeCallbacks(captureTimeout)
        startCaptureRunnable?.let { Handler(Looper.getMainLooper()).removeCallbacks(it) }
        startCaptureRunnable = null
        Log.d(tag, "[清理资源] 释放 VirtualDisplay/ImageReader/MediaProjection，停止前台服务")
        val mp = mediaProjection
        mediaProjection = null
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mp?.stop()
        stopService(Intent(this, ScreenCaptureService::class.java))
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }
}

@Composable
private fun ScreenCaptureScreen(
    bitmap: Bitmap?,
    statusText: String,
    error: Boolean,
    onCrop: (Rect) -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    if (bitmap == null && !error) {
        // 透明：授权/捕获阶段不绘制背景，让第三方 App 透过透明窗口被捕获
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    if (bitmap == null) {
        // 捕获失败：不透明错误页
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = statusText, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onRetry) { Text("重试") }
                    Button(onClick = onCancel) { Text("取消") }
                }
            }
        }
        return
    }

    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var start by remember { mutableStateOf<Offset?>(null) }
    var end by remember { mutableStateOf<Offset?>(null) }

    // Canvas 比整屏截图小（下方有一行按钮），截图等比缩放居中绘制，
    // 框选坐标再按同一比例反算回 bitmap 像素坐标，保证「框哪识别哪」。
    val scale = if (canvasSize != IntSize.Zero && bitmap.width > 0 && bitmap.height > 0)
        min(canvasSize.width.toFloat() / bitmap.width.toFloat(), canvasSize.height.toFloat() / bitmap.height.toFloat())
    else 1f
    val dstW = (bitmap.width * scale).roundToInt()
    val dstH = (bitmap.height * scale).roundToInt()
    val dstOffsetX = ((canvasSize.width - dstW) / 2f).roundToInt()
    val dstOffsetY = ((canvasSize.height - dstH) / 2f).roundToInt()

    val cropRect: Rect? = if (start != null && end != null && canvasSize != IntSize.Zero && scale > 0f) {
        val left = ((min(start!!.x, end!!.x) - dstOffsetX) / scale).roundToInt().coerceIn(0, bitmap.width)
        val top = ((min(start!!.y, end!!.y) - dstOffsetY) / scale).roundToInt().coerceIn(0, bitmap.height)
        val right = ((max(start!!.x, end!!.x) - dstOffsetX) / scale).roundToInt().coerceIn(0, bitmap.width)
        val bottom = ((max(start!!.y, end!!.y) - dstOffsetY) / scale).roundToInt().coerceIn(0, bitmap.height)
        if (right > left && bottom > top) Rect(left, top, right, bottom) else null
    } else null

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (statusText.isNotBlank()) {
            Text(
                text = statusText,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                start = offset
                                end = offset
                            },
                            onDrag = { change, _ ->
                                end = change.position
                            },
                            onDragEnd = { /* 保留框选 */ },
                            onDragCancel = {
                                start = null
                                end = null
                            },
                        )
                    },
            ) {
                drawImage(
                    image = imageBitmap,
                    dstOffset = IntOffset(dstOffsetX, dstOffsetY),
                    dstSize = IntSize(dstW, dstH),
                )
                if (start != null && end != null) {
                    val left = min(start!!.x, end!!.x)
                    val top = min(start!!.y, end!!.y)
                    val right = max(start!!.x, end!!.x)
                    val bottom = max(start!!.y, end!!.y)
                    val w = right - left
                    val h = bottom - top
                    if (w > 0f && h > 0f) {
                        val cw = size.width.toFloat()
                        val ch = size.height.toFloat()
                        // 选区外半透明变暗（EvenOdd 镂空选区），一眼看清选区边界
                        val dimPath = Path().apply {
                            addRect(androidx.compose.ui.geometry.Rect(0f, 0f, cw, ch))
                            addRect(androidx.compose.ui.geometry.Rect(left, top, right, bottom))
                            fillType = PathFillType.EvenOdd
                        }
                        drawPath(dimPath, color = Color.Black.copy(alpha = 0.55f))
                        // 双色边框：白色粗边（深色/彩色背景上醒目）+ 亮红细边（白色背景上醒目）
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(left, top),
                            size = Size(w, h),
                            style = Stroke(width = 4.dp.toPx()),
                        )
                        drawRect(
                            color = Color(0xFFFF3B30),
                            topLeft = Offset(left, top),
                            size = Size(w, h),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("取消") }
            Button(onClick = onRetry, modifier = Modifier.weight(1f)) { Text("重截") }
            Button(
                onClick = { cropRect?.let(onCrop) },
                enabled = cropRect != null,
                modifier = Modifier.weight(1f),
            ) { Text("识别") }
        }
    }
}
