package com.movtery.zalithlauncher.game.account

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.coroutine.Task
import com.movtery.zalithlauncher.coroutine.TaskSystem
import com.movtery.zalithlauncher.game.account.microsoft.AsyncStatus
import com.movtery.zalithlauncher.game.account.microsoft.AuthType
import com.movtery.zalithlauncher.game.account.microsoft.MicrosoftAuthenticator
import com.movtery.zalithlauncher.game.account.microsoft.NotPurchasedMinecraftException
import com.movtery.zalithlauncher.game.account.microsoft.TimeoutException
import com.movtery.zalithlauncher.game.account.otherserver.OtherLoginApi
import com.movtery.zalithlauncher.game.account.otherserver.OtherLoginHelper
import com.movtery.zalithlauncher.game.account.otherserver.models.Servers
import com.movtery.zalithlauncher.game.account.otherserver.models.Servers.Server
import com.movtery.zalithlauncher.state.MutableStates
import com.movtery.zalithlauncher.state.ObjectStates
import com.movtery.zalithlauncher.ui.screens.content.WEB_VIEW_SCREEN_TAG
import com.movtery.zalithlauncher.ui.screens.content.elements.MicrosoftLoginOperation
import com.movtery.zalithlauncher.utils.CryptoManager
import com.movtery.zalithlauncher.utils.GSON
import com.movtery.zalithlauncher.utils.copyText
import com.movtery.zalithlauncher.utils.string.StringUtils
import com.movtery.zalithlauncher.utils.string.StringUtils.Companion.getMessageOrToString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.Objects
import kotlin.coroutines.CoroutineContext

fun Account.isOtherLoginAccount(): Boolean {
    return !Objects.isNull(otherBaseUrl) && otherBaseUrl != "0"
}

fun Account.isMicrosoftAccount(): Boolean {
    return accountType == AccountType.MICROSOFT.tag
}

fun Account.isLocalAccount(): Boolean {
    return accountType == AccountType.LOCAL.tag
}

fun Account?.isNoLoginRequired(): Boolean {
    return this == null || isLocalAccount()
}

fun Account.isSkinChangeAllowed(): Boolean {
    return isMicrosoftAccount() || isLocalAccount()
}

private const val MICROSOFT_LOGGING_TASK = "microsoft_logging_task"

/**
 * 检查当前微软账号登陆是否正在进行中
 */
fun isMicrosoftLogging() = TaskSystem.containsTask(MICROSOFT_LOGGING_TASK)

fun microsoftLogin(
    context: Context,
    updateOperation: (MicrosoftLoginOperation) -> Unit
) {
    val task = Task.runTask(
        id = MICROSOFT_LOGGING_TASK,
        dispatcher = Dispatchers.IO,
        task = { task ->
            task.updateProgress(-1f, R.string.account_microsoft_fetch_device_code)
            val deviceCode = MicrosoftAuthenticator.fetchDeviceCodeResponse(coroutineContext)
            copyText(null, deviceCode.userCode, context)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    context.getString(R.string.account_microsoft_coped_device_code, deviceCode.userCode),
                    Toast.LENGTH_SHORT
                ).show()
            }
            ObjectStates.accessUrl(deviceCode.verificationUrl)
            task.updateProgress(-1f, R.string.account_microsoft_get_token)
            val tokenResponse = MicrosoftAuthenticator.getTokenResponse(deviceCode, coroutineContext) {
                MutableStates.mainScreenTag?.startsWith(WEB_VIEW_SCREEN_TAG) == false
            }
            ObjectStates.backToLauncherScreen()
            val account = authAsync(
                AuthType.Access,
                tokenResponse.refreshToken,
                tokenResponse.accessToken,
                coroutineContext = coroutineContext,
                updateProgress = task::updateProgress
            )
            task.updateMessage(R.string.account_logging_in_saving)
            account.downloadSkin()
            saveAccount(account)
        },
        onError = { e ->
            when (e) {
                is TimeoutException -> context.getString(R.string.account_logging_time_out)
                is NotPurchasedMinecraftException -> context.getString(R.string.account_logging_not_purchased_minecraft)
                is CancellationException -> { null }
                else -> e.getMessageOrToString()
            }?.let { message ->
                ObjectStates.updateThrowable(
                    ObjectStates.ThrowableMessage(
                        title = context.getString(R.string.account_logging_in_failed),
                        message = message
                    )
                )
            }
        },
        onFinally = {
            updateOperation(MicrosoftLoginOperation.None)
        }
    )

    TaskSystem.submitTask(task)
}

private suspend fun authAsync(
    authType: AuthType,
    refreshToken: String,
    accessToken: String = "NULL",
    coroutineContext: CoroutineContext,
    updateProgress: (Float, Int) -> Unit
): Account {
    return MicrosoftAuthenticator.authAsync(authType, refreshToken, accessToken, coroutineContext) { asyncStatus ->
        when (asyncStatus) {
            AsyncStatus.GETTING_ACCESS_TOKEN ->     updateProgress(0.25f, R.string.account_microsoft_getting_access_token)
            AsyncStatus.GETTING_XBL_TOKEN ->        updateProgress(0.4f, R.string.account_microsoft_getting_xbl_token)
            AsyncStatus.GETTING_XSTS_TOKEN ->       updateProgress(0.55f, R.string.account_microsoft_getting_xsts_token)
            AsyncStatus.AUTHENTICATE_MINECRAFT ->   updateProgress(0.7f, R.string.account_microsoft_authenticate_minecraft)
            AsyncStatus.VERIFY_GAME_OWNERSHIP ->    updateProgress(0.85f, R.string.account_microsoft_verify_game_ownership)
            AsyncStatus.GETTING_PLAYER_PROFILE ->   updateProgress(1f, R.string.account_microsoft_getting_player_profile)
        }
    }
}

fun microsoftRefresh(
    context: Context,
    account: Account,
    onSuccess: suspend (Account, Task) -> Unit,
    onFailed: (error: String) -> Unit = {},
    onFinally: () -> Unit = {}
): Task? {
    if (TaskSystem.containsTask(account.profileId)) return null

    return Task.runTask(
        id = account.profileId,
        dispatcher = Dispatchers.IO,
        task = { task ->
            val newAcc = authAsync(
                AuthType.Refresh,
                account.refreshToken,
                account.accessToken,
                coroutineContext = coroutineContext,
                updateProgress = task::updateProgress
            )
            account.apply {
                this.accessToken = newAcc.accessToken
                this.clientToken = newAcc.clientToken
                this.profileId = newAcc.profileId
                this.username = newAcc.username
                this.refreshToken = newAcc.refreshToken
                this.xuid = newAcc.xuid
            }
            onSuccess(account, task)
        },
        onError = { e ->
            when (e) {
                is TimeoutException -> context.getString(R.string.account_logging_time_out)
                is NotPurchasedMinecraftException -> context.getString(R.string.account_logging_not_purchased_minecraft)
                is CancellationException -> null
                else -> e.getMessageOrToString()
            }?.let { message ->
                onFailed(message)
            }
        },
        onFinally = onFinally
    )
}

fun otherLogin(
    context: Context,
    account: Account,
    onSuccess: suspend (Account, task: Task) -> Unit = { _, _ -> },
    onFailed: (error: String) -> Unit = {},
    onFinally: () -> Unit = {}
): Task? {
    if (TaskSystem.containsTask(account.uniqueUUID)) return null

    return OtherLoginHelper(
        baseUrl = account.otherBaseUrl!!,
        serverName = account.accountType!!,
        email = account.otherAccount!!,
        password = account.otherPassword!!,
        onSuccess = onSuccess,
        onFailed = onFailed,
        onFinally = onFinally
    ).justLogin(context, account)
}

/**
 * 离线账号登陆
 */
fun localLogin(userName: String) {
    val account = Account().apply {
        this.username = userName
        this.accountType = AccountType.LOCAL.tag
    }
    saveAccount(account)
}

fun addOtherServer(
    serverUrl: String,
    serverConfig: () -> MutableStateFlow<Servers>,
    serverConfigFile: File,
    onThrowable: (Throwable) -> Unit = {}
) {
    val task = Task.runTask(
        task = { task ->
            task.updateProgress(-1f, R.string.account_other_login_getting_full_url)
            val fullServerUrl = tryGetFullServerUrl(serverUrl)
            ensureActive()
            task.updateProgress(0.5f, R.string.account_other_login_getting_server_info)
            OtherLoginApi.getServeInfo(fullServerUrl)?.let { data ->
                JSONObject(data).optJSONObject("meta")?.let { meta ->
                    val server = Server(
                        serverName = meta.optString("serverName"),
                        baseUrl = fullServerUrl,
                        register = meta.optJSONObject("links")?.optString("register") ?: ""
                    )
                    if (serverConfig().value.server.any { it.baseUrl == server.baseUrl }) {
                        //确保服务器不重复
                        return@runTask
                    }
                    serverConfig().update { currentConfig ->
                        currentConfig.server.add(server)
                        currentConfig.copy()
                    }
                    task.updateProgress(0.8f, R.string.account_other_login_saving_server)
                    val configString = GSON.toJson(serverConfig().value, Servers::class.java)
                    val text = CryptoManager.encrypt(configString)
                    serverConfigFile.writeText(text)
                    task.updateProgress(1f, R.string.generic_done)
                }
            }
        },
        onError = { e ->
            onThrowable(e)
            Log.e("AddOtherServer", "Failed to add other server\n${StringUtils.throwableToString(e)}")
        }
    )

    TaskSystem.submitTask(task)
}

fun saveAccount(account: Account) {
    runCatching {
        account.save()
        Log.i("SaveAccount", "Saved account: ${account.username}")
    }.onFailure { e ->
        Log.e("SaveAccount", "Failed to save account: ${account.username}", e)
    }

    AccountsManager.reloadAccounts()
}

/**
 * 获取账号类型名称
 */
fun getAccountTypeName(context: Context, account: Account): String {
    return if (account.isMicrosoftAccount()) {
        context.getString(R.string.account_type_microsoft)
    } else if (account.isOtherLoginAccount()) {
        account.accountType ?: "Unknown"
    } else {
        context.getString(R.string.account_type_local)
    }
}

/**
 * 修改自源代码：[HMCL Core: AuthlibInjectorServer.java](https://github.com/HMCL-dev/HMCL/blob/main/HMCLCore/src/main/java/org/jackhuang/hmcl/auth/authlibinjector/AuthlibInjectorServer.java#L60-#L76)
 * <br>原项目版权归原作者所有，遵循GPL v3协议
 */
fun tryGetFullServerUrl(baseUrl: String): String {
    fun String.addSlashIfMissing(): String {
        if (!endsWith("/")) return "$this/"
        return this
    }

    var url = addHttpsIfMissing(baseUrl)
    runCatching {
        var conn = URL(url).openConnection() as HttpURLConnection
        conn.getHeaderField("x-authlib-injector-api-location")?.let { ali ->
            val absoluteAli = URL(conn.url, ali)
            url = url.addSlashIfMissing()
            val absoluteUrl = absoluteAli.toString().addSlashIfMissing()
            if (url != absoluteUrl) {
                conn.disconnect()
                url = absoluteUrl
                conn = absoluteAli.openConnection() as HttpURLConnection
            }
        }

        return url.addSlashIfMissing()
    }.getOrElse { e ->
        Log.e("getFullServerUrl", "Failed to get full server url", e)
    }
    return baseUrl
}

/**
 * 修改自源代码：[HMCL Core: AuthlibInjectorServer.java](https://github.com/HMCL-dev/HMCL/blob/main/HMCLCore/src/main/java/org/jackhuang/hmcl/auth/authlibinjector/AuthlibInjectorServer.java#L90-#L96)
 * <br>原项目版权归原作者所有，遵循GPL v3协议
 */
private fun addHttpsIfMissing(baseUrl: String): String {
    return if (!baseUrl.startsWith("http://", true) && !baseUrl.startsWith("https://")) {
        "https://$baseUrl".lowercase(Locale.ROOT)
    } else baseUrl.lowercase(Locale.ROOT)
}