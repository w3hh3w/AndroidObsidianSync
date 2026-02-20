package com.obsidiansync.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.obsidiansync.databinding.ActivitySettingsBinding
import com.obsidiansync.domain.AuthManager
import com.obsidiansync.ui.main.MainActivity

/**
 * 设置页面 - 管理OAuth配置和账户登录
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
        loadOAuthStatus()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        loadOAuthStatus()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun loadOAuthStatus() {
        // Gitee OAuth配置状态
        val giteeConfigured = authManager.isOAuthConfigured("gitee")
        binding.tvGiteeConfigured.text = if (giteeConfigured) "已配置" else "未配置"
        binding.tvGiteeConfigured.setTextColor(
            if (giteeConfigured) getColor(0xFF4CAF50.toInt()) else getColor(0xFFF44336.toInt())
        )

        // GitHub OAuth配置状态
        val githubConfigured = authManager.isOAuthConfigured("github")
        binding.tvGithubConfigured.text = if (githubConfigured) "已配置" else "未配置"
        binding.tvGithubConfigured.setTextColor(
            if (githubConfigured) getColor(0xFF4CAF50.toInt()) else getColor(0xFFF44336.toInt())
        )

        // Gitee登录状态
        if (authManager.isLoggedIn("gitee")) {
            binding.tvGiteeStatus.text = "已登录 (${authManager.getUsername("gitee")})"
            binding.btnGiteeLogin.text = "登出"
        } else {
            binding.tvGiteeStatus.text = "未登录"
            binding.btnGiteeLogin.text = "登录"
        }

        // GitHub登录状态
        if (authManager.isLoggedIn("github")) {
            binding.tvGithubStatus.text = "已登录 (${authManager.getUsername("github")})"
            binding.btnGithubLogin.text = "登出"
        } else {
            binding.tvGithubStatus.text = "未登录"
            binding.btnGithubLogin.text = "登录"
        }
    }

    private fun setupListeners() {
        // 保存Gitee OAuth配置
        binding.btnSaveGiteeOAuth.setOnClickListener {
            val clientId = binding.etGiteeClientId.text.toString().trim()
            val clientSecret = binding.etGiteeClientSecret.text.toString().trim()

            if (clientId.isEmpty() || clientSecret.isEmpty()) {
                Toast.makeText(this, "请输入Client ID和Secret", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            authManager.saveOAuthConfig("gitee", clientId, clientSecret)
            Toast.makeText(this, "Gitee OAuth配置已保存", Toast.LENGTH_SHORT).show()
            loadOAuthStatus()
        }

        // 保存GitHub OAuth配置
        binding.btnSaveGithubOAuth.setOnClickListener {
            val clientId = binding.etGithubClientId.text.toString().trim()
            val clientSecret = binding.etGithubClientSecret.text.toString().trim()

            if (clientId.isEmpty() || clientSecret.isEmpty()) {
                Toast.makeText(this, "请输入Client ID和Secret", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            authManager.saveOAuthConfig("github", clientId, clientSecret)
            Toast.makeText(this, "GitHub OAuth配置已保存", Toast.LENGTH_SHORT).show()
            loadOAuthStatus()
        }

        // Gitee登录/登出
        binding.btnGiteeLogin.setOnClickListener {
            if (authManager.isLoggedIn("gitee")) {
                confirmLogout("gitee")
            } else {
                if (!authManager.isOAuthConfigured("gitee")) {
                    Toast.makeText(this, "请先配置Gitee OAuth", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                openGiteeAuth()
            }
        }

        // GitHub登录/登出
        binding.btnGithubLogin.setOnClickListener {
            if (authManager.isLoggedIn("github")) {
                confirmLogout("github")
            } else {
                if (!authManager.isOAuthConfigured("github")) {
                    Toast.makeText(this, "请先配置GitHub OAuth", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                openGitHubAuth()
            }
        }

        // 关于
        binding.btnAbout.setOnClickListener {
            showAboutDialog()
        }
    }

    private fun openGiteeAuth() {
        val authUrl = authManager.getGiteeAuthUrl()
        if (authUrl != null) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
            startActivity(intent)
        } else {
            Toast.makeText(this, "请先配置Gitee OAuth", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGitHubAuth() {
        val authUrl = authManager.getGitHubAuthUrl()
        if (authUrl != null) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
            startActivity(intent)
        } else {
            Toast.makeText(this, "请先配置GitHub OAuth", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmLogout(provider: String) {
        val name = if (provider == "gitee") "Gitee" else "GitHub"
        AlertDialog.Builder(this)
            .setTitle("登出")
            .setMessage("确定要登出 $name 吗？")
            .setPositiveButton("确定") { _, _ ->
                authManager.logout(provider)
                loadOAuthStatus()
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
                1. 在Gitee/GitHub创建OAuth应用
                2. 在设置中填写Client ID和Secret
                3. 登录后添加仓库开始同步

                回调地址：
                obsidiansync://oauth/callback

                © 2024 ObsidianSync
            """.trimIndent())
            .setPositiveButton("确定", null)
            .show()
    }
}
