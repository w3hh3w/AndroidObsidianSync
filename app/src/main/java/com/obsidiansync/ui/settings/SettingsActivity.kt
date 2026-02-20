package com.obsidiansync.ui.settings

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.obsidiansync.databinding.ActivitySettingsBinding
import com.obsidiansync.domain.AuthManager
import com.obsidiansync.ui.main.MainActivity
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
        // GitHub Token登录
        binding.btnGithubLogin.setOnClickListener {
            if (authManager.isLoggedIn("github")) {
                // 登出
                confirmLogout()
            } else {
                // 登录
                saveTokenAndLogin()
            }
        }

        // 关于
        binding.btnAbout.setOnClickListener {
            showAboutDialog()
        }
    }

    private fun saveTokenAndLogin() {
        val token = binding.etGithubToken.text.toString().trim()

        if (token.isEmpty()) {
            Toast.makeText(this, "请输入Access Token", Toast.LENGTH_SHORT).show()
            return
        }

        // 验证Token是否有效
        binding.btnGithubLogin.isEnabled = false
        binding.btnGithubLogin.text = "验证中..."

        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                verifyGitHubToken(token)
            }

            if (result.isSuccess) {
                val username = result.getOrNull() ?: "GitHub用户"
                authManager.saveToken("github", token)
                authManager.saveUsername("github", username)
                Toast.makeText(this@SettingsActivity, "登录成功: $username", Toast.LENGTH_SHORT).show()
                loadLoginStatus()
            } else {
                Toast.makeText(this@SettingsActivity, "Token无效: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                binding.btnGithubLogin.isEnabled = true
                binding.btnGithubLogin.text = "保存并登录"
            }
        }
    }

    private fun verifyGitHubToken(token: String): Result<String> {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url("https://api.github.com/user")
                .addHeader("Authorization", "token $token")
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                // 解析用户名
                val username = try {
                    val regex = """\"login"\s*:\s*"([^"]+)"""".toRegex()
                    regex.find(body)?.groupValues?.get(1) ?: "用户"
                } catch (e: Exception) {
                    "用户"
                }
                Result.success(username)
            } else {
                Result.failure(Exception("验证失败: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("登出")
            .setMessage("确定要登出GitHub吗？")
            .setPositiveButton("确定") { _, _ ->
                authManager.logout("github")
                binding.etGithubToken.setText("")
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

                通过 GitHub 同步你的 Obsidian 笔记库。

                使用步骤：
                1. 在GitHub生成Access Token
                2. 在设置中粘贴Token
                3. 添加仓库开始同步

                © 2024 ObsidianSync
            """.trimIndent())
            .setPositiveButton("确定", null)
            .show()
    }
}
