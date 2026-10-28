package com.buguagaoshu.scan.search.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.buguagaoshu.scan.search.R
import com.buguagaoshu.scan.search.ScreenCaptureActivity

/**
 * 屏幕捕获前台服务。
 *
 * 职责 1：Android 14（targetSdk 34）起，调用 MediaProjectionManager.getMediaProjection() 前必须有一个
 * foregroundServiceType="mediaProjection" 的前台服务在运行，否则抛 SecurityException。
 * 职责 2：前台服务豁免「后台启动 Activity」限制，悬浮窗先启动本服务，再由本服务拉起识屏界面，
 * 避免从后台悬浮窗直接 startActivity 被系统静默拦截（表现为点了没反应）。
 */
class ScreenCaptureService : Service() {

    companion object {
        // 悬浮窗「识屏」入口标记：仅该来源才拉起识屏 Activity，避免识屏 Activity 为满足 Android 14
        // 再次 startForegroundService 时重复拉起 Activity。
        const val ACTION_LAUNCH = "com.buguagaoshu.scan.search.action.LAUNCH_SCREEN_CAPTURE"

        // 诊断日志 tag，与 ScreenCaptureActivity 保持一致
        private const val TAG = "ScanSearchCapture"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "[前台服务onStartCommand] action=${intent?.action} SDK=${Build.VERSION.SDK_INT}")
        startForegroundWithType()
        if (intent?.action == ACTION_LAUNCH) {
            val activityIntent = Intent(this, ScreenCaptureActivity::class.java)
            activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            Log.d(TAG, "[前台服务拉起识屏Activity]")
            startActivity(activityIntent)
        }
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
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // API 34+：必须显式指定 mediaProjection 类型
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                Log.d(TAG, "[startForeground成功] 类型=mediaProjection (Android 14+)")
            } else {
                startForeground(1, notification)
                Log.d(TAG, "[startForeground成功] 无类型 (Android <14)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[startForeground失败] ${e.javaClass.name}：${e.message}", e)
        }
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
