package com.buguagaoshu.scan.search.control

import com.buguagaoshu.scan.search.data.SendData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
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
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.SECONDS) // 无限等待流数据
        .retryOnConnectionFailure(true)
        .build()
    private val contentType: MediaType = "application/json".toMediaType()

    // 所有回调统一切到主线程，避免在 IO 线程更新 Compose 状态导致崩溃
    private fun postMain(block: () -> Unit) {
        CoroutineScope(Dispatchers.Main).launch { block() }
    }

    fun send(
        sendData: SendData,
        url: String,
        key: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val body = Json.encodeToString(sendData).toRequestBody(contentType)
                val req = Request
                    .Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Authorization", "Bearer $key")
                    .build()
                client.newCall(req).execute().use { data ->
                    if (data.isSuccessful) {
                        val responseBody = data.body?.string() ?: ""
                        postMain { onSuccess(responseBody) }
                    } else {
                        val errBody = data.body?.string() ?: ""
                        postMain { onError("HTTP ${data.code}: $errBody") }
                    }
                }
            } catch (e: Exception) {
                postMain { onError(e.message ?: "网络错误") }
            }
        }
    }

    fun sendStream(
        sendData: SendData,
        url: String,
        key: String,
        onChunkReceived: (String) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val body = Json.encodeToString(sendData).toRequestBody(contentType)
            val req = Request
                .Builder()
                .url(url)
                .post(body)
                .addHeader("Authorization", "Bearer $key")
                .build()
            client.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    postMain { onError(e.message ?: "网络错误") }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!response.isSuccessful) {
                            val errBody = response.body?.string() ?: ""
                            postMain { onError("HTTP ${response.code}: $errBody") }
                            return
                        }
                        val reader = response.body.charStream()
                        reader.forEachLine { line ->
                            if (line.isNotBlank()) {
                                postMain { onChunkReceived(line) }
                            }
                        }
                        postMain { onComplete() }
                    }
                }
            })
        }
    }
}
