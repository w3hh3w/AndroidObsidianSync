package com.obsidiansync.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.obsidiansync.R
import com.obsidiansync.data.local.GitRepositoryManager
import com.obsidiansync.domain.AuthManager
import com.obsidiansync.ui.main.MainActivity
import kotlinx.coroutines.*

/**
 * 前台同步服务 - 用于长时间同步操作
 */
class SyncService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var gitManager: GitRepositoryManager
    private lateinit var authManager: AuthManager

    companion object {
        const val CHANNEL_ID = "sync_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_SYNC = "com.obsidiansync.ACTION_SYNC"
        const val EXTRA_REPO_ID = "repo_id"
    }

    override fun onCreate() {
        super.onCreate()
        gitManager = GitRepositoryManager(this)
        authManager = AuthManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SYNC -> {
                val repoId = intent.getLongExtra(EXTRA_REPO_ID, -1)
                if (repoId != -1L) {
                    startForeground(NOTIFICATION_ID, createNotification("正在同步..."))
                    syncRepository(repoId)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun syncRepository(repoId: Long) {
        serviceScope.launch {
            try {
                val repo = gitManager.getRepository(repoId)
                if (repo == null) {
                    updateNotification("仓库不存在")
                    stopSelf()
                    return@launch
                }

                val token = authManager.getToken(repo.provider)
                if (token == null) {
                    updateNotification("请先登录")
                    stopSelf()
                    return@launch
                }

                val result = gitManager.sync(repo.localPath, token, "Sync from ObsidianSync")

                if (result.isSuccess) {
                    updateNotification("同步完成")
                } else {
                    updateNotification("同步失败: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                updateNotification("同步失败: ${e.message}")
            } finally {
                delay(2000) // 显示完成状态2秒
                stopSelf()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "同步服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Obsidian同步通知"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ObsidianSync")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_sync)
            .setOngoing(true)
            .setContentIntent(createPendingIntent())
            .build()

    private fun updateNotification(content: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(content))
    }

    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
    }
}
