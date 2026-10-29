package com.buguagaoshu.scan.search.control

import android.util.Log
import com.buguagaoshu.scan.search.data.SendData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElementSerializer
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.IOException
import java.util.concurrent.TimeUnit

object HttpControl {
    // 计时日志统一 tag，adb logcat -s ScanSearchTime 可单独查看；只记耗时/状态码/错误类别，不含 Key 与对话内容
    private const val TAG = "ScanSearchTime"

    // 非流式：读超时 60s 兜底，避免服务端无响应时永久挂起
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // 流式：读超时放宽（服务端持续推送，空闲超 5 分钟视为断开）；禁用自动重试，避免重复/错乱
    private val streamClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .callTimeout(600, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val contentType: MediaType = "application/json".toMediaType()

    // 当前进行中的请求，供用户取消（关闭浮窗时调用 cancel()）
    @Volatile
    var activeCall: Call? = null
        private set

    fun cancel() {
        activeCall?.cancel()
        activeCall = null
    }

    private fun postMain(block: () -> Unit) {
        CoroutineScope(Dispatchers.Main).launch { block() }
    }

    private fun log(tag: String, elapsedMs: Long) {
        Log.d(TAG, "[$tag] ${elapsedMs}ms")
    }

    // 构建请求体：无图片时走原序列化（保持与既有文本请求完全一致）；
    // 有图片时把 user 消息的 content 变为 [text, image_url] 多模态数组（OpenAI 兼容格式）。
    private fun buildRequestBody(sendData: SendData, imageBase64: String?): String {
        if (imageBase64 == null) return Json.encodeToString(sendData)
        val messages = buildJsonArray {
            for (msg in sendData.messages) {
                if (msg.role == "user") {
                    add(
                        buildJsonObject {
                            put("role", msg.role)
                            put(
                                "content",
                                buildJsonArray {
                                    add(buildJsonObject { put("type", "text"); put("text", msg.content) })
                                    add(
                                        buildJsonObject {
                                            put("type", "image_url")
                                            put(
                                                "image_url",
                                                buildJsonObject {
                                                    put("url", "data:image/jpeg;base64,$imageBase64")
                                                },
                                            )
                                        },
                                    )
                                },
                            )
                        },
                    )
                } else {
                    add(buildJsonObject { put("role", msg.role); put("content", msg.content) })
                }
            }
        }
        val body = buildJsonObject {
            put("stream", sendData.stream)
            put("model", sendData.model)
            put("messages", messages)
        }
        return Json.encodeToString(JsonElementSerializer, body)
    }

    fun send(
        sendData: SendData,
        url: String,
        key: String,
        imageBase64: String? = null,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val t0 = System.currentTimeMillis()
        Log.d(TAG, "非流式请求开始 model=${sendData.model}")
        CoroutineScope(Dispatchers.IO).launch {
            var call: Call? = null
            try {
                val body = buildRequestBody(sendData, imageBase64).toRequestBody(contentType)
                val req = Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Authorization", "Bearer $key")
                    .build()
                log("非流式-本地准备完成", System.currentTimeMillis() - t0)
                call = client.newCall(req)
                activeCall = call
                call.execute().use { data ->
                    log("非流式-响应头到达", System.currentTimeMillis() - t0)
                    if (data.isSuccessful) {
                        val responseBody = data.body?.string() ?: ""
                        log("非流式-完整响应读取完成", System.currentTimeMillis() - t0)
                        postMain { onSuccess(responseBody) }
                    } else {
                        log("非流式-错误 HTTP ${data.code}", System.currentTimeMillis() - t0)
                        postMain { onError("HTTP ${data.code}") }
                    }
                }
            } catch (e: Exception) {
                log("非流式-失败 ${e.javaClass.simpleName}", System.currentTimeMillis() - t0)
                postMain { onError(e.message ?: "网络错误") }
            } finally {
                if (activeCall === call) activeCall = null
            }
        }
    }

    fun sendStream(
        sendData: SendData,
        url: String,
        key: String,
        imageBase64: String? = null,
        // 已解析出的 SSE data: JSON 载荷（去掉 "data:" 前缀）
        onChunkReceived: (String) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        val t0 = System.currentTimeMillis()
        Log.d(TAG, "流式请求开始 model=${sendData.model}")
        CoroutineScope(Dispatchers.IO).launch {
            val body = buildRequestBody(sendData, imageBase64).toRequestBody(contentType)
            val req = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Authorization", "Bearer $key")
                .build()
            val call = streamClient.newCall(req)
            activeCall = call
            log("流式-本地准备完成", System.currentTimeMillis() - t0)
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    log("流式-失败 ${e.javaClass.simpleName}", System.currentTimeMillis() - t0)
                    postMain { onError(e.message ?: "网络错误") }
                    if (activeCall === call) activeCall = null
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!response.isSuccessful) {
                            log("流式-错误 HTTP ${response.code}", System.currentTimeMillis() - t0)
                            postMain { onError("HTTP ${response.code}") }
                        } else {
                            log("流式-响应头到达", System.currentTimeMillis() - t0)
                            val body = response.body
                            if (body == null) {
                                postMain { onError("响应体为空") }
                            } else {
                                parseSse(java.io.BufferedReader(body.charStream()), t0, onChunkReceived, onComplete)
                            }
                        }
                    }
                    if (activeCall === call) activeCall = null
                }
            })
        }
    }

    // 标准 SSE 解析：只取 data: 行、跳过 event:/id:/注释/空行、识别 [DONE] 结束标记
    private fun parseSse(
        reader: java.io.BufferedReader,
        t0: Long,
        onChunkReceived: (String) -> Unit,
        onComplete: () -> Unit
    ) {
        var firstChunk = true
        var done = false
        while (!done) {
            val line = reader.readLine() ?: break
            if (!line.startsWith("data:")) continue
            val payload = line.removePrefix("data:").trimStart()
            if (payload.isEmpty()) continue
            if (firstChunk) {
                firstChunk = false
                log("流式-首个数据块", System.currentTimeMillis() - t0)
            }
            if (payload == "[DONE]") {
                done = true
                log("流式-结束标记[DONE]", System.currentTimeMillis() - t0)
                postMain { onComplete() }
                break
            }
            postMain { onChunkReceived(payload) }
        }
        if (!done) {
            log("流式-流关闭(无[DONE])", System.currentTimeMillis() - t0)
            postMain { onComplete() }
        }
    }
}
