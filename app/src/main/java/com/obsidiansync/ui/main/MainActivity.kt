package com.obsidiansync.ui.main

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.obsidiansync.R
import com.obsidiansync.data.local.GitRepositoryManager
import com.obsidiansync.data.local.Repository
import com.obsidiansync.databinding.ActivityMainBinding
import com.obsidiansync.domain.AuthManager
import com.obsidiansync.domain.SyncManager
import com.obsidiansync.ui.settings.RepoInfo
import com.obsidiansync.ui.settings.SettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主页面 - 仓库列表和同步控制
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var authManager: AuthManager
    private lateinit var gitManager: GitRepositoryManager
    private lateinit var syncManager: SyncManager
    private lateinit var adapter: RepositoryAdapter

    private var selectedLocalPath: String = ""
    private var currentProvider: String = "gitee"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authManager = AuthManager(this)
        gitManager = GitRepositoryManager(this)
        syncManager = SyncManager(this)

        setupUI()
        loadRepositories()
    }

    override fun onResume() {
        super.onResume()
        // 刷新用户信息
        refreshUserInfo()
    }

    private fun refreshUserInfo() {
        val giteeUser = authManager.getUsername("gitee")
        val githubUser = authManager.getUsername("github")
        val user = giteeUser ?: githubUser
        if (user != null) {
            binding.tvUsername.text = user
        }
    }

    private fun setupUI() {
        // 仓库列表
        adapter = RepositoryAdapter(
            onSyncClick = { repo -> syncRepository(repo) },
            onDeleteClick = { repo -> confirmDelete(repo) },
            onSettingsClick = { repo -> openRepoSettings(repo) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        // 添加仓库
        binding.fabAdd.setOnClickListener {
            showAddRepositoryDialog()
        }

        // 刷新
        binding.swipeRefresh.setOnRefreshListener {
            loadRepositories()
        }

        // 设置
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 用户信息
        refreshUserInfo()
    }

    private fun loadRepositories() {
        lifecycleScope.launch {
            val repos = gitManager.getAllRepositories()
            adapter.submitList(repos)
            binding.tvEmpty.visibility = if (repos.isEmpty()) View.VISIBLE else View.GONE
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun showAddRepositoryDialog() {
        // 检查Gitee和GitHub是否至少有一个登录
        val giteeLoggedIn = authManager.isLoggedIn("gitee")
        val githubLoggedIn = authManager.isLoggedIn("github")

        if (!giteeLoggedIn && !githubLoggedIn) {
            Toast.makeText(this, "请先在设置中配置Gitee或GitHub Token", Toast.LENGTH_SHORT).show()
            return
        }

        // 如果只有一个登录，直接用那个
        if (giteeLoggedIn && !githubLoggedIn) {
            currentProvider = "gitee"
            loadAndShowRepos()
            return
        }
        if (!giteeLoggedIn && githubLoggedIn) {
            currentProvider = "github"
            loadAndShowRepos()
            return
        }

        // 两个都登录了，让用户选择
        val providers = arrayOf("Gitee", "GitHub")
        AlertDialog.Builder(this)
            .setTitle("选择平台")
            .setItems(providers) { _, which ->
                currentProvider = if (which == 0) "gitee" else "github"
                loadAndShowRepos()
            }
            .show()
    }

    private fun loadAndShowRepos() {
        val token = authManager.getToken(currentProvider)
        if (token == null) {
            Toast.makeText(this, "Token无效，请重新登录", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val repos = withContext(Dispatchers.IO) {
                try {
                    val client = okhttp3.OkHttpClient()
                    val (url, authHeader) = when (currentProvider) {
                        "gitee" -> Pair(
                            "https://gitee.com/api/v5/user/repos?per_page=100&sort=updated",
                            "token $token"
                        )
                        "github" -> Pair(
                            "https://api.github.com/user/repos?per_page=100&sort=updated",
                            "token $token"
                        )
                        else -> return@withContext emptyList()
                    }

                    val request = okhttp3.Request.Builder()
                        .url(url)
                        .addHeader("Authorization", authHeader)
                        .get()
                        .build()

                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        parseRepoList(body)
                    } else {
                        emptyList()
                    }
                } catch (e: Exception) {
                    emptyList()
                }
            }

            binding.progressBar.visibility = View.GONE

            if (repos.isEmpty()) {
                Toast.makeText(this@MainActivity, "获取仓库列表失败", Toast.LENGTH_SHORT).show()
                return@launch
            }

            showRepoListDialog(repos)
        }
    }

    private fun parseRepoList(json: String): List<RepoInfo> {
        val repos = mutableListOf<RepoInfo>()
        try {
            val regex = """\"full_name"\s*:\s*"([^"]+)"""".toRegex()
            regex.findAll(json).forEach { match ->
                val fullName = match.groupValues[1]
                repos.add(RepoInfo(
                    name = fullName.substringAfter("/"),
                    fullName = fullName,
                    url = if (currentProvider == "gitee") "https://gitee.com/$fullName" else "https://github.com/$fullName"
                ))
            }
        } catch (e: Exception) {
            // 解析失败
        }
        return repos
    }

    private fun showRepoListDialog(repos: List<RepoInfo>) {
        val names = repos.map { it.fullName }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择仓库")
            .setItems(names) { _, which ->
                val selectedRepo = repos[which]
                // 选择本地文件夹
                showFolderPicker(selectedRepo)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showFolderPicker(selectedRepo: RepoInfo) {
        // 保存选择的仓库信息
        currentRepoInfo = selectedRepo
        folderPicker.launch(null)
    }

    private var currentRepoInfo: RepoInfo? = null

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)

                // 使用外部存储作为同步目录
                selectedLocalPath = getExternalFilesDir(null)?.absolutePath + "/${currentRepoInfo?.name}"

                // 克隆仓库
                currentRepoInfo?.let { repo ->
                    addRepositoryFromRepoInfo(repo)
                }
            } catch (e: Exception) {
                Toast.makeText(this, "无法获取文件夹权限", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addRepositoryFromRepoInfo(repo: RepoInfo) {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val token = authManager.getToken(currentProvider)
            if (token == null) {
                Toast.makeText(this@MainActivity, "未登录", Toast.LENGTH_SHORT).show()
                binding.progressBar.visibility = View.GONE
                return@launch
            }

            val localPath = getExternalFilesDir(null)?.absolutePath + "/${repo.name}"
            val result = gitManager.cloneRepository(repo.url, localPath, token, currentProvider)

            if (result.isSuccess) {
                val repository = Repository(
                    name = repo.name,
                    remoteUrl = repo.url,
                    localPath = localPath,
                    provider = currentProvider,
                    accessToken = token,
                    autoSync = false,
                    syncInterval = 30
                )
                gitManager.saveRepository(repository)
                Toast.makeText(this@MainActivity, "仓库添加成功", Toast.LENGTH_SHORT).show()
                loadRepositories()
            } else {
                Toast.makeText(this@MainActivity, "克隆失败: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }

            binding.progressBar.visibility = View.GONE
        }
    }

    private fun addRepository(repoUrl: String) {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            // 解析仓库名
            val repoName = extractRepoName(repoUrl)
            val localPath = getExternalFilesDir(null)?.absolutePath + "/$repoName"

            // 克隆仓库
            val token = authManager.getToken(currentProvider)
            if (token == null) {
                Toast.makeText(this@MainActivity, "未登录", Toast.LENGTH_SHORT).show()
                binding.progressBar.visibility = View.GONE
                return@launch
            }

            val result = gitManager.cloneRepository(repoUrl, localPath, token, currentProvider)

            if (result.isSuccess) {
                // 保存仓库配置
                val repo = Repository(
                    name = repoName,
                    remoteUrl = repoUrl,
                    localPath = localPath,
                    provider = currentProvider,
                    accessToken = token,
                    autoSync = false,
                    syncInterval = 30
                )
                gitManager.saveRepository(repo)
                Toast.makeText(this@MainActivity, "仓库添加成功", Toast.LENGTH_SHORT).show()
                loadRepositories()
            } else {
                Toast.makeText(this@MainActivity, "克隆失败: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }

            binding.progressBar.visibility = View.GONE
        }
    }

    private fun extractRepoName(url: String): String {
        return url.trimEnd('/').substringAfterLast('/').removeSuffix(".git")
    }

    private fun syncRepository(repo: Repository) {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = syncManager.syncRepository(repo)

            if (result.isSuccess) {
                Toast.makeText(this@MainActivity, "同步成功", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "同步失败: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }

            binding.progressBar.visibility = View.GONE
            loadRepositories()
        }
    }

    private fun confirmDelete(repo: Repository) {
        AlertDialog.Builder(this)
            .setTitle("删除仓库")
            .setMessage("确定要删除仓库 ${repo.name} 吗？本地文件不会被删除。")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    gitManager.deleteRepository(repo)
                    loadRepositories()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openRepoSettings(repo: Repository) {
        val intent = Intent(this, SettingsActivity::class.java).apply {
            putExtra("repo_id", repo.id)
        }
        startActivity(intent)
    }
}
