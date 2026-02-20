package com.obsidiansync.ui.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.obsidiansync.databinding.ActivityAuthBinding
import com.obsidiansync.domain.AuthManager
import com.obsidiansync.ui.main.MainActivity
import kotlinx.coroutines.launch

/**
 * 登录页面 - Gitee/GitHub授权
 */
class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authManager = AuthManager(this)

        // 检查是否已登录
        checkLoginStatus()

        setupClickListeners()
    }

    private fun checkLoginStatus() {
        val providers = authManager.getLoggedInProviders()
        if (providers.isNotEmpty()) {
            // 已登录，跳转到主页
            navigateToMain()
        }
    }

    private fun setupClickListeners() {
        // Gitee登录
        binding.btnGiteeLogin.setOnClickListener {
            openGiteeAuth()
        }

        // GitHub登录
        binding.btnGithubLogin.setOnClickListener {
            openGitHubAuth()
        }

        // 跳过登录（查看已配置的仓库）
        binding.btnSkip.setOnClickListener {
            navigateToMain()
        }
    }

    private fun openGiteeAuth() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnGiteeLogin.isEnabled = false

        val authUrl = authManager.getGiteeAuthUrl()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
        startActivity(intent)
    }

    private fun openGitHubAuth() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnGithubLogin.isEnabled = false

        val authUrl = authManager.getGitHubAuthUrl()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        // 处理OAuth回调
        handleOAuthCallback()
    }

    private fun handleOAuthCallback() {
        val uri = intent?.data
        if (uri != null && uri.scheme == "obsidiansync") {
            val code = uri.getQueryParameter("code")
            val provider = when {
                uri.host == "oauth" && uri.path?.contains("gitee") == true -> "gitee"
                uri.host == "oauth" && uri.path?.contains("github") == true -> "github"
                else -> null
            }

            if (code != null && provider != null) {
                // 处理 OAuth token 交换
                lifecycleScope.launch {
                    val result = authManager.handleCallback(provider, code)
                    result.onSuccess {
                        Toast.makeText(this@AuthActivity, "登录成功: ${it.username}", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(this@AuthActivity, "登录失败: ${it.message}", Toast.LENGTH_LONG).show()
                    }
                    navigateToMain()
                }
            }

            binding.progressBar.visibility = View.GONE
            binding.btnGiteeLogin.isEnabled = true
            binding.btnGithubLogin.isEnabled = true
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
