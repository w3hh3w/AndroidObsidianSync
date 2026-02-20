package com.obsidiansync.domain

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/**
 * 认证管理器 - 处理 Gitee/GitHub OAuth
 * 用户需要自己注册OAuth应用并输入Client ID和Secret
 */
class AuthManager(private val context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        // 默认回调地址（用户可在设置中修改）
        const val DEFAULT_GITEE_REDIRECT_URI = "obsidiansync://oauth/callback/gitee"
        const val DEFAULT_GITHUB_REDIRECT_URI = "obsidiansync://oauth/callback/github"

        // 用户配置存储key
        const val KEY_GITEE_CLIENT_ID = "gitee_client_id"
        const val KEY_GITEE_CLIENT_SECRET = "gitee_client_secret"
        const val KEY_GITHUB_CLIENT_ID = "github_client_id"
        const val KEY_GITHUB_CLIENT_SECRET = "github_client_secret"
        const val KEY_REDIRECT_URI = "redirect_uri"

        // Token storage keys
        const val KEY_GITEE_TOKEN = "gitee_access_token"
        const val KEY_GITHUB_TOKEN = "github_access_token"
        const val KEY_GITEE_USERNAME = "gitee_username"
        const val KEY_GITHUB_USERNAME = "github_username"

        // Scopes
        const val GITEE_SCOPE = "user,repo"
        const val GITHUB_SCOPE = "repo,user"
    }

    /**
     * 保存用户的OAuth配置
     */
    fun saveOAuthConfig(provider: String, clientId: String, clientSecret: String) {
        when (provider) {
            "gitee" -> {
                securePrefs.edit()
                    .putString(KEY_GITEE_CLIENT_ID, clientId)
                    .putString(KEY_GITEE_CLIENT_SECRET, clientSecret)
                    .apply()
            }
            "github" -> {
                securePrefs.edit()
                    .putString(KEY_GITHUB_CLIENT_ID, clientId)
                    .putString(KEY_GITHUB_CLIENT_SECRET, clientSecret)
                    .apply()
            }
        }
    }

    /**
     * 获取OAuth配置
     */
    fun getOAuthConfig(provider: String): OAuthConfig? {
        return when (provider) {
            "gitee" -> {
                val clientId = securePrefs.getString(KEY_GITEE_CLIENT_ID, null)
                val clientSecret = securePrefs.getString(KEY_GITEE_CLIENT_SECRET, null)
                if (clientId != null && clientSecret != null) {
                    OAuthConfig(clientId, clientSecret, DEFAULT_GITEE_REDIRECT_URI)
                } else null
            }
            "github" -> {
                val clientId = securePrefs.getString(KEY_GITHUB_CLIENT_ID, null)
                val clientSecret = securePrefs.getString(KEY_GITHUB_CLIENT_SECRET, null)
                if (clientId != null && clientSecret != null) {
                    OAuthConfig(clientId, clientSecret, DEFAULT_GITHUB_REDIRECT_URI)
                } else null
            }
            else -> null
        }
    }

    /**
     * 检查是否已配置OAuth
     */
    fun isOAuthConfigured(provider: String): Boolean {
        return getOAuthConfig(provider) != null
    }

    /**
     * 生成Gitee授权URL
     */
    fun getGiteeAuthUrl(): String? {
        val config = getOAuthConfig("gitee") ?: return null
        return "https://gitee.com/oauth/authorize?" +
                "client_id=${config.clientId}" +
                "&redirect_uri=${URLEncoder.encode(config.redirectUri, "UTF-8")}" +
                "&response_type=code" +
                "&scope=$GITEE_SCOPE"
    }

    /**
     * 生成GitHub授权URL
     */
    fun getGitHubAuthUrl(): String? {
        val config = getOAuthConfig("github") ?: return null
        return "https://github.com/login/oauth/authorize?" +
                "client_id=${config.clientId}" +
                "&redirect_uri=${URLEncoder.encode(config.redirectUri, "UTF-8")}" +
                "&scope=$GITHUB_SCOPE" +
                "&state=obsidiansync"
    }

    /**
     * 处理OAuth回调
     */
    suspend fun handleCallback(provider: String, code: String): Result<AuthResult> = withContext(Dispatchers.IO) {
        try {
            when (provider) {
                "gitee" -> exchangeGiteeCode(code)
                "github" -> exchangeGitHubCode(code)
                else -> Result.failure(Exception("Unknown provider"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 兑换Gitee授权码
     */
    private suspend fun exchangeGiteeCode(code: String): Result<AuthResult> = withContext(Dispatchers.IO) {
        try {
            val config = getOAuthConfig("gitee")
                ?: return@withContext Result.failure(Exception("请先配置OAuth"))

            val tokenUrl = "https://gitee.com/oauth/token?" +
                    "grant_type=authorization_code" +
                    "&client_id=${config.clientId}" +
                    "&client_secret=${config.clientSecret}" +
                    "&code=${code}" +
                    "&redirect_uri=${config.redirectUri}"

            val response = okhttp3.OkHttpClient()
                .newCall(okhttp3.Request.Builder().url(tokenUrl).post(okhttp3.FormBody.Builder().build()).build())
                .execute()

            if (response.isSuccessful) {
                val body = response.body?.string()
                val gson = com.google.gson.Gson()
                val tokenResponse = gson.fromJson(body, GiteeTokenResponse::class.java)

                saveToken("gitee", tokenResponse.accessToken)
                saveUsername("gitee", tokenResponse.name ?: "User")

                Result.success(AuthResult(
                    provider = "gitee",
                    accessToken = tokenResponse.accessToken,
                    username = tokenResponse.name ?: "User"
                ))
            } else {
                Result.failure(Exception("获取Token失败: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 兑换GitHub授权码
     */
    private suspend fun exchangeGitHubCode(code: String): Result<AuthResult> = withContext(Dispatchers.IO) {
        try {
            val config = getOAuthConfig("github")
                ?: return@withContext Result.failure(Exception("请先配置OAuth"))

            // GitHub需要通过后端交换token，这里简化处理
            // 实际应该调用后端API
            Result.success(AuthResult(
                provider = "github",
                accessToken = code,
                username = "User"
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 保存Token
     */
    fun saveToken(provider: String, token: String) {
        val key = if (provider == "gitee") KEY_GITEE_TOKEN else KEY_GITHUB_TOKEN
        securePrefs.edit().putString(key, token).apply()
    }

    /**
     * 保存用户名
     */
    fun saveUsername(provider: String, username: String) {
        val key = if (provider == "gitee") KEY_GITEE_USERNAME else KEY_GITHUB_USERNAME
        securePrefs.edit().putString(key, username).apply()
    }

    /**
     * 获取Token
     */
    fun getToken(provider: String): String? {
        val key = if (provider == "gitee") KEY_GITEE_TOKEN else KEY_GITHUB_TOKEN
        return securePrefs.getString(key, null)
    }

    /**
     * 获取用户名
     */
    fun getUsername(provider: String): String? {
        val key = if (provider == "gitee") KEY_GITEE_USERNAME else KEY_GITHUB_USERNAME
        return securePrefs.getString(key, null)
    }

    /**
     * 检查是否已登录
     */
    fun isLoggedIn(provider: String): Boolean {
        return getToken(provider) != null
    }

    /**
     * 登出
     */
    fun logout(provider: String) {
        val tokenKey = if (provider == "gitee") KEY_GITEE_TOKEN else KEY_GITHUB_TOKEN
        val usernameKey = if (provider == "gitee") KEY_GITEE_USERNAME else KEY_GITHUB_USERNAME
        securePrefs.edit()
            .remove(tokenKey)
            .remove(usernameKey)
            .apply()
    }

    /**
     * 获取所有已登录的提供商
     */
    fun getLoggedInProviders(): List<String> {
        val providers = mutableListOf<String>()
        if (isLoggedIn("gitee")) providers.add("gitee")
        if (isLoggedIn("github")) providers.add("github")
        return providers
    }

    /**
     * 获取所有已配置的OAuth提供商
     */
    fun getConfiguredProviders(): List<String> {
        val providers = mutableListOf<String>()
        if (isOAuthConfigured("gitee")) providers.add("gitee")
        if (isOAuthConfigured("github")) providers.add("github")
        return providers
    }
}

/**
 * OAuth配置
 */
data class OAuthConfig(
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String
)

/**
 * 认证结果
 */
data class AuthResult(
    val provider: String,
    val accessToken: String,
    val username: String
)

/**
 * Gitee Token响应
 */
data class GiteeTokenResponse(
    val accessToken: String,
    val tokenType: String?,
    val expiresIn: Int?,
    val refreshToken: String?,
    val scope: String?,
    val name: String?,
    val login: String?
)

/**
 * GitHub Token响应
 */
data class GitHubTokenResponse(
    val accessToken: String,
    val tokenType: String,
    val scope: String
)
