package com.obsidiansync.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.obsidiansync.databinding.ActivitySettingsBinding
import com.obsidiansync.domain.AuthManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 设置页面 - 管理Token登录
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authManager = AuthManager(this)

        setupToolbar()
        loadLoginStatus()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        loadLoginStatus()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun loadLoginStatus() {
        // Gitee状态
        if (authManager.isLoggedIn("gitee")) {
            val username = authManager.getUsername("gitee") ?: "用户"
            binding.tvGiteeStatus.text = "已登录 ($username)"
            binding.tvGiteeStatus.setTextColor(0xFF4CAF50.toInt())
            binding.btnGiteeLogin.text = "登出"
        } else {
            binding.tvGiteeStatus.text = "未登录"
            binding.tvGiteeStatus.setTextColor(0xFF757575.toInt())
            binding.btnGiteeLogin.text = "保存并登录"
        }

        // GitHub状态
        if (authManager.isLoggedIn("github")) {
            val username = authManager.getUsername("github") ?: "用户"
            binding.tvGithubStatus.text = "已登录 ($username)"
            binding.tvGithubStatus.setTextColor(0xFF4CAF50.toInt())
            binding.btnGithubLogin.text = "登出"
        } else {
            binding.tvGithubStatus.text = "未登录"
            binding.tvGithubStatus.setTextColor(0xFF757575.toInt())
            binding.btnGithubLogin.text = "保存并登录"
        }
    }

    private fun setupListeners() {
        // Gitee Token登录
        binding.btnGiteeLogin.setOnClickListener {
            if (authManager.isLoggedIn("gitee")) {
                confirmLogout("gitee")
            } else {
                saveTokenAndLogin("gitee")
            }
        }

        // GitHub Token登录
        binding.btnGithubLogin.setOnClickListener {
            if (authManager.isLoggedIn("github")) {
                confirmLogout("github")
            } else {
                saveTokenAndLogin("github")
            }
        }

        // 关于
        binding.btnAbout.setOnClickListener {
            showAboutDialog()
        }
    }

    private fun saveTokenAndLogin(provider: String) {
        val token = when (provider) {
            "gitee" -> binding.etGiteeToken.text.toString().trim()
            "github" -> binding.etGithubToken.text.toString().trim()
            else -> ""
        }

        if (token.isEmpty()) {
            Toast.makeText(this, "请输入Token", Toast.LENGTH_SHORT).show()
            return
        }

        // 验证Token
        val btn = if (provider == "gitee") binding.btnGiteeLogin else binding.btnGithubLogin
        btn.isEnabled = false
        btn.text = "验证中..."

        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                verifyToken(provider, token)
            }

            if (result.isSuccess) {
                val username = result.getOrNull() ?: "${provider}用户"
                authManager.saveToken(provider, token)
                authManager.saveUsername(provider, username)
                Toast.makeText(this@SettingsActivity, "登录成功: $username", Toast.LENGTH_SHORT).show()
                loadLoginStatus()
            } else {
                Toast.makeText(this@SettingsActivity, "Token无效: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                btn.isEnabled = true
                btn.text = "保存并登录"
            }
        }
    }

    private fun verifyToken(provider: String, token: String): Result<String> {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .build()

            val (url, authHeader) = when (provider) {
                "gitee" -> Pair(
                    "https://gitee.com/api/v5/user",
                    "token $token"
                )
                "github" -> Pair(
                    "https://api.github.com/user",
                    "token $token"
                )
                else -> return Result.failure(Exception("Unknown provider"))
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", authHeader)
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val username = when (provider) {
                    "gitee", "github" -> {
                        val regex = """\"login"\s*:\s*"([^"]+)"""".toRegex()
                        regex.find(body)?.groupValues?.get(1) ?: "用户"
                    }
                    else -> "用户"
                }
                Result.success(username)
            } else {
                Result.failure(Exception("验证失败: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun confirmLogout(provider: String) {
        val name = if (provider == "gitee") "Gitee" else "GitHub"
        AlertDialog.Builder(this)
            .setTitle("登出")
            .setMessage("确定要登出 $name 吗？")
            .setPositiveButton("确定") { _, _ ->
                authManager.logout(provider)
                if (provider == "gitee") {
                    binding.etGiteeToken.setText("")
                } else {
                    binding.etGithubToken.setText("")
                }
                loadLoginStatus()
                Toast.makeText(this, "已登出", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("关于 ObsidianSync")
            .setMessage("""
                ObsidianSync v1.0

                通过 Gitee/GitHub 同步你的 Obsidian 笔记库。

                使用步骤：
                1. 在Gitee/GitHub生成Access Token
                2. 在设置中粘贴Token
                3. 添加仓库开始同步

                © 2024 ObsidianSync
            """.trimIndent())
            .setPositiveButton("确定", null)
            .show()
    }

    /**
     * 获取用户仓库列表
     */
    fun getUserRepositories(provider: String, token: String): List<RepoInfo> {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .build()

            val (url, authHeader) = when (provider) {
                "gitee" -> Pair(
                    "https://gitee.com/api/v5/user/repos?per_page=100&sort=updated",
                    "token $token"
                )
                "github" -> Pair(
                    "https://api.github.com/user/repos?per_page=100&sort=updated",
                    "token $token"
                )
                else -> return emptyList()
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", authHeader)
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                parseRepositories(provider, body)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseRepositories(provider: String, json: String): List<RepoInfo> {
        val repos = mutableListOf<RepoInfo>()
        try {
            val regex = """\"full_name"\s*:\s*"([^"]+)"""".toRegex()
            regex.findAll(json).forEach { match ->
                val fullName = match.groupValues[1]
                repos.add(RepoInfo(
                    name = fullName.substringAfter("/"),
                    fullName = fullName,
                    url = if (provider == "gitee") "https://gitee.com/$fullName" else "https://github.com/$fullName"
                ))
            }
        } catch (e: Exception) {
            // 解析失败
        }
        return repos
    }
}

/**
 * 仓库信息
 */
data class RepoInfo(
    val name: String,
    val fullName: String,
    val url: String
)
