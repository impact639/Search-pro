package com.buguagaoshu.scan.search.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.buguagaoshu.scan.search.R

/**
 * 屏幕捕获前台服务。
 *
 * Android 14（targetSdk 34）起，调用 MediaProjectionManager.getMediaProjection() 前必须有一个
 * foregroundServiceType="mediaProjection" 的前台服务在运行，否则抛 SecurityException。
 * 本服务仅用于满足该约束并展示一条「正在捕获屏幕」的通知，捕获结束后立即 stopService。
 */
class ScreenCaptureService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundWithType()
        return START_NOT_STICKY
    }

    private fun startForegroundWithType() {
        val channelId = "screen_capture"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "屏幕捕获", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("问一下")
            .setContentText("正在捕获屏幕…")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // API 34+：必须显式指定 mediaProjection 类型
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
