package com.obsidiansync.domain

import android.content.Context
import androidx.work.*
import com.obsidiansync.data.local.GitRepositoryManager
import com.obsidiansync.data.local.Repository
import com.obsidiansync.data.local.SyncRecord
import com.obsidiansync.data.local.SyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * 同步管理器 - 协调Git操作和后台任务
 */
class SyncManager(private val context: Context) {

    private val gitManager = GitRepositoryManager(context)
    private val workManager = WorkManager.getInstance(context)

    companion object {
        const val WORK_TAG_SYNC = "obsidian_sync"
        const val WORK_TAG_REPO = "repo_"
    }

    /**
     * 同步单个仓库
     */
    suspend fun syncRepository(repo: Repository): Result<SyncResult> {
        val token = AuthManager(context).getToken(repo.provider)
            ?: return Result.failure(Exception("Not logged in"))

        // 执行同步
        val result = gitManager.sync(repo.localPath, token, "Sync from ObsidianSync")

        // 记录同步历史
        if (result.isSuccess) {
            gitManager.addSyncRecord(
                SyncRecord(
                    repoId = repo.id,
                    timestamp = System.currentTimeMillis(),
                    action = "sync",
                    success = true,
                    message = result.getOrNull()?.message ?: "Success"
                )
            )
        } else {
            gitManager.addSyncRecord(
                SyncRecord(
                    repoId = repo.id,
                    timestamp = System.currentTimeMillis(),
                    action = "sync",
                    success = false,
                    message = result.exceptionOrNull()?.message ?: "Failed"
                )
            )
        }

        return result
    }

    /**
     * 设置定时同步
     */
    fun scheduleAutoSync(repoId: Long, intervalMinutes: Int) {
        val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(intervalMinutes.toLong(), TimeUnit.MINUTES)
            .addTag(WORK_TAG_SYNC)
            .addTag("$WORK_TAG_REPO$repoId")
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            "sync_repo_$repoId",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    /**
     * 取消定时同步
     */
    fun cancelAutoSync(repoId: Long) {
        workManager.cancelUniqueWork("sync_repo_$repoId")
    }

    /**
     * 立即同步
     */
    fun syncNow(repoId: Long) {
        val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .addTag(WORK_TAG_SYNC)
            .addTag("$WORK_TAG_REPO$repoId")
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        workManager.enqueue(workRequest)
    }

    /**
     * 获取所有仓库状态
     */
    suspend fun getAllRepoStatus(): List<RepoStatus> {
        val repos = gitManager.getAllRepositories()
        return repos.map { repo ->
            val statusResult = gitManager.getStatus(repo.localPath)
            val auth = AuthManager(context)
            RepoStatus(
                repo = repo,
                isLoggedIn = auth.isLoggedIn(repo.provider),
                hasChanges = statusResult.getOrNull()?.hasChanges ?: false,
                lastSyncTime = repo.lastSyncTime
            )
        }
    }
}

/**
 * 仓库状态
 */
data class RepoStatus(
    val repo: Repository,
    val isLoggedIn: Boolean,
    val hasChanges: Boolean,
    val lastSyncTime: Long
)

/**
 * WorkManager Worker - 后台同步
 */
class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val repoId = inputData.getLong("repo_id", -1)
        if (repoId == -1L) {
            return Result.failure()
        }

        val gitManager = GitRepositoryManager(applicationContext)
        val authManager = AuthManager(applicationContext)

        val repo = gitManager.getRepository(repoId)
            ?: return Result.failure()

        val token = authManager.getToken(repo.provider)
            ?: return Result.failure()

        return try {
            val result = gitManager.sync(repo.localPath, token, "Auto sync from ObsidianSync")

            if (result.isSuccess) {
                // 更新最后同步时间
                gitManager.saveRepository(repo.copy(lastSyncTime = System.currentTimeMillis()))
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
