package com.obsidiansync.data.local

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.PullCommand
import org.eclipse.jgit.api.PushCommand
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

/**
 * 仓库配置实体类
 */
@Entity(tableName = "repositories")
data class Repository(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val remoteUrl: String,
    val localPath: String,
    val provider: String, // "github" or "gitee"
    val accessToken: String,
    val lastSyncTime: Long = 0,
    val autoSync: Boolean = false,
    val syncInterval: Int = 30, // minutes
    val excludePatterns: List<String> = emptyList()
)

/**
 * 同步记录
 */
@Entity(tableName = "sync_history")
data class SyncRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val repoId: Long,
    val timestamp: Long,
    val action: String, // "push", "pull", "sync"
    val success: Boolean,
    val message: String
)

/**
 * Room数据库
 */
@Database(entities = [Repository::class, SyncRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun repositoryDao(): RepositoryDao
    abstract fun syncRecordDao(): SyncRecordDao
}

@Dao
interface RepositoryDao {
    @Query("SELECT * FROM repositories")
    suspend fun getAll(): List<Repository>

    @Query("SELECT * FROM repositories WHERE id = :id")
    suspend fun getById(id: Long): Repository?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(repo: Repository): Long

    @Update
    suspend fun update(repo: Repository)

    @Delete
    suspend fun delete(repo: Repository)
}

@Dao
interface SyncRecordDao {
    @Query("SELECT * FROM sync_history WHERE repoId = :repoId ORDER BY timestamp DESC LIMIT 10")
    suspend fun getRecent(repoId: Long): List<SyncRecord>

    @Insert
    suspend fun insert(record: SyncRecord)
}

/**
 * Git仓库管理器 - 核心Git操作
 */
class GitRepositoryManager(private val context: Context) {

    private val db: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "obsidiansync.db"
    ).build()

    /**
     * 克隆仓库到本地
     */
    suspend fun cloneRepository(
        remoteUrl: String,
        localPath: String,
        accessToken: String,
        branch: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val localDir = File(localPath)
            if (localDir.exists()) {
                return@withContext Result.failure(Exception("目录已存在"))
            }

            val credentials = UsernamePasswordCredentialsProvider(accessToken, "")

            // 尝试用户指定的分支，如果没有指定则先尝试 main，失败后尝试 master
            val branchesToTry = listOfNotNull(branch, "main", "master").distinct()

            for (branchName in branchesToTry) {
                try {
                    Git.cloneRepository()
                        .setURI(remoteUrl)
                        .setDirectory(localDir)
                        .setCredentialsProvider(credentials)
                        .setCloneAllBranches(false)
                        .setBranch(branchName)
                        .call()
                    return@withContext Result.success(Unit)
                } catch (e: Exception) {
                    // 当前分支尝试失败，尝试下一个
                    continue
                }
            }

            Result.failure(Exception("无法克隆仓库，请检查仓库地址和分支是否存在"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Pull 拉取远程更新
     */
    suspend fun pull(localPath: String, accessToken: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val git = Git.open(File(localPath))
            val credentials = UsernamePasswordCredentialsProvider(accessToken, "")

            val pullResult = git.pull()
                .setCredentialsProvider(credentials)
                .call()

            val summary = pullResult.mergeResult?.toString() ?: "Pull completed"
            Result.success(summary)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Push 推送到远程
     */
    suspend fun push(localPath: String, accessToken: String, commitMessage: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val git = Git.open(File(localPath))
            val credentials = UsernamePasswordCredentialsProvider(accessToken, "")

            // Add all changes
            git.add()
                .addFilepattern(".")
                .call()

            // Commit
            git.commit()
                .setMessage(commitMessage)
                .call()

            // Push
            git.push()
                .setCredentialsProvider(credentials)
                .call()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取仓库状态
     */
    suspend fun getStatus(localPath: String): Result<GitStatus> = withContext(Dispatchers.IO) {
        try {
            val git = Git.open(File(localPath))
            val status = git.status().call()

            val gitStatus = GitStatus(
                modified = status.modified.toList(),
                added = status.added.toList(),
                removed = status.removed.toList(),
                untracked = status.untracked.toList(),
                hasChanges = !status.isClean
            )
            Result.success(gitStatus)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 同步 - Pull + Push
     */
    suspend fun sync(
        localPath: String,
        accessToken: String,
        commitMessage: String = "Sync from ObsidianSync"
    ): Result<SyncResult> = withContext(Dispatchers.IO) {
        try {
            // First pull
            val pullResult = pull(localPath, accessToken)

            // Then push
            val pushResult = push(localPath, accessToken, commitMessage)

            if (pushResult.isSuccess) {
                Result.success(SyncResult(
                    success = true,
                    message = "Sync completed successfully",
                    pullMessage = pullResult.getOrNull() ?: "",
                    pushed = true
                ))
            } else {
                Result.failure(pushResult.exceptionOrNull() ?: Exception("Push failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Database operations
    suspend fun getAllRepositories(): List<Repository> = db.repositoryDao().getAll()
    suspend fun getRepository(id: Long): Repository? = db.repositoryDao().getById(id)
    suspend fun saveRepository(repo: Repository): Long = db.repositoryDao().insert(repo)
    suspend fun updateRepository(repo: Repository) = db.repositoryDao().update(repo)
    suspend fun deleteRepository(repo: Repository) = db.repositoryDao().delete(repo)
    suspend fun addSyncRecord(record: SyncRecord) = db.syncRecordDao().insert(record)
}

/**
 * Git仓库状态
 */
data class GitStatus(
    val modified: List<String>,
    val added: List<String>,
    val removed: List<String>,
    val untracked: List<String>,
    val hasChanges: Boolean
)

/**
 * 同步结果
 */
data class SyncResult(
    val success: Boolean,
    val message: String,
    val pullMessage: String = "",
    val pushed: Boolean = false
)
