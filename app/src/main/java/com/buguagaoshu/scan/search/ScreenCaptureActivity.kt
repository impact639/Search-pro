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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.buguagaoshu.scan.search.config.StaticVariableConfig
import com.buguagaoshu.scan.search.control.FxComposeControl
import com.buguagaoshu.scan.search.service.ScreenCaptureService
import com.buguagaoshu.scan.search.ui.theme.SearchTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            startScreenCapture(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "未授予屏幕捕获权限，无法框选", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        bitmap = null
        error = false
        statusText = "正在请求屏幕捕获授权…"
        captureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    private fun startScreenCapture(resultCode: Int, data: Intent) {
        statusText = "正在捕获屏幕…"

        // Android 14+ 必须先启动 mediaProjection 前台服务
        val serviceIntent = Intent(this, ScreenCaptureService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                cleanup()
            }
        }, Handler(Looper.getMainLooper()))

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "screen-capture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null,
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                try {
                    bitmap = imageToBitmap(image)
                    statusText = ""
                } catch (e: Exception) {
                    error = true
                    statusText = "截图失败：${e.message}"
                } finally {
                    image.close()
                    cleanup()
                }
            }
        }, Handler(Looper.getMainLooper()))
    }

    // ImageReader(RGBA_8888) → Bitmap；处理行 stride 填充
    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val paddedWidth = image.width + rowPadding / pixelStride
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
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                recognizer.close()
                val text = visionText.text?.trim() ?: ""
                if (text.isBlank()) {
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
                statusText = "识别失败：${e.message}"
            }
    }

    private fun cleanup() {
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

    // 1:1 绘制：截图按原始像素显示，框选坐标即 bitmap 像素坐标
    val cropRect: Rect? = if (start != null && end != null && canvasSize != IntSize.Zero) {
        val left = min(start!!.x, end!!.x).toInt().coerceIn(0, canvasSize.width)
        val top = min(start!!.y, end!!.y).toInt().coerceIn(0, canvasSize.height)
        val right = max(start!!.x, end!!.x).toInt().coerceIn(0, canvasSize.width)
        val bottom = max(start!!.y, end!!.y).toInt().coerceIn(0, canvasSize.height)
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
                drawImage(imageBitmap)
                if (start != null && end != null) {
                    val left = min(start!!.x, end!!.x)
                    val top = min(start!!.y, end!!.y)
                    val size = Size(abs(end!!.x - start!!.x), abs(end!!.y - start!!.y))
                    // 半透明遮罩 + 白色边框，便于看清选中范围
                    drawRect(
                        color = Color.White.copy(alpha = 0.15f),
                        topLeft = Offset(left, top),
                        size = size,
                    )
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(left, top),
                        size = size,
                        style = Stroke(width = 2.dp.toPx()),
                    )
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
