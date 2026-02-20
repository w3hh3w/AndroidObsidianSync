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
import com.obsidiansync.ui.settings.SettingsActivity
import kotlinx.coroutines.launch

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

    // 文件夹选择器
    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { takeFolderPermission(it) }
    }

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
        val giteeUser = authManager.getUsername("gitee")
        val githubUser = authManager.getUsername("github")
        val user = giteeUser ?: githubUser
        if (user != null) {
            binding.tvUsername.text = user
        }
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
        val providers = arrayOf("Gitee", "GitHub")
        AlertDialog.Builder(this)
            .setTitle("选择平台")
            .setItems(providers) { _, which ->
                currentProvider = if (which == 0) "gitee" else "github"

                // 检查是否登录
                if (!authManager.isLoggedIn(currentProvider)) {
                    Toast.makeText(this, "请先在设置中登录${providers[which]}", Toast.LENGTH_SHORT).show()
                    return@setItems
                }

                // 选择本地文件夹
                showFolderPicker()
            }
            .show()
    }

    private fun showFolderPicker() {
        folderPicker.launch(null)
    }

    private fun takeFolderPermission(uri: Uri) {
        try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, takeFlags)

            // 获取真实路径（需要用户授权访问）
            val docId = DocumentsContract.getTreeDocumentId(uri)
            selectedLocalPath = Environment.getExternalStorageDirectory().absolutePath

            // 显示仓库URL输入对话框
            showRepoUrlDialog()
        } catch (e: Exception) {
            Toast.makeText(this, "无法获取文件夹权限", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRepoUrlDialog() {
        val editText = android.widget.EditText(this).apply {
            hint = "输入Gitee/GitHub仓库URL"
        }

        AlertDialog.Builder(this)
            .setTitle("添加仓库")
            .setMessage("请输入仓库地址（如：https://gitee.com/user/obsidian-notes）")
            .setView(editText)
            .setPositiveButton("添加") { _, _ ->
                val repoUrl = editText.text.toString().trim()
                if (repoUrl.isNotEmpty()) {
                    addRepository(repoUrl)
                }
            }
            .setNegativeButton("取消", null)
            .show()
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

            val result = gitManager.cloneRepository(repoUrl, localPath, token)

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
