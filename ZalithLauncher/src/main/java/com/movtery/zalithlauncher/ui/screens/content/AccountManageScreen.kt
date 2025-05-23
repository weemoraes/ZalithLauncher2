package com.movtery.zalithlauncher.ui.screens.content

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.context.copyLocalFile
import com.movtery.zalithlauncher.coroutine.Task
import com.movtery.zalithlauncher.coroutine.TaskSystem
import com.movtery.zalithlauncher.game.account.Account
import com.movtery.zalithlauncher.game.account.AccountsManager
import com.movtery.zalithlauncher.game.account.addOtherServer
import com.movtery.zalithlauncher.game.account.isLocalAccount
import com.movtery.zalithlauncher.game.account.isMicrosoftAccount
import com.movtery.zalithlauncher.game.account.isMicrosoftLogging
import com.movtery.zalithlauncher.game.account.localLogin
import com.movtery.zalithlauncher.game.account.microsoftLogin
import com.movtery.zalithlauncher.game.account.otherserver.OtherLoginHelper
import com.movtery.zalithlauncher.game.account.otherserver.models.Servers
import com.movtery.zalithlauncher.game.account.saveAccount
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.path.UrlManager
import com.movtery.zalithlauncher.state.MutableStates
import com.movtery.zalithlauncher.state.ObjectStates
import com.movtery.zalithlauncher.ui.base.BaseScreen
import com.movtery.zalithlauncher.ui.components.ScalingActionButton
import com.movtery.zalithlauncher.ui.components.ScalingLabel
import com.movtery.zalithlauncher.ui.components.SimpleAlertDialog
import com.movtery.zalithlauncher.ui.components.SimpleEditDialog
import com.movtery.zalithlauncher.ui.components.SimpleListDialog
import com.movtery.zalithlauncher.ui.screens.content.elements.AccountItem
import com.movtery.zalithlauncher.ui.screens.content.elements.AccountOperation
import com.movtery.zalithlauncher.ui.screens.content.elements.AccountSkinOperation
import com.movtery.zalithlauncher.ui.screens.content.elements.LoginItem
import com.movtery.zalithlauncher.ui.screens.content.elements.MicrosoftLoginOperation
import com.movtery.zalithlauncher.ui.screens.content.elements.OtherLoginOperation
import com.movtery.zalithlauncher.ui.screens.content.elements.OtherServerLoginDialog
import com.movtery.zalithlauncher.ui.screens.content.elements.SelectSkinModelDialog
import com.movtery.zalithlauncher.ui.screens.content.elements.ServerItem
import com.movtery.zalithlauncher.ui.screens.content.elements.ServerOperation
import com.movtery.zalithlauncher.utils.CryptoManager
import com.movtery.zalithlauncher.utils.GSON
import com.movtery.zalithlauncher.utils.animation.swapAnimateDpAsState
import com.movtery.zalithlauncher.utils.network.NetWorkUtils
import com.movtery.zalithlauncher.utils.string.StringUtils.Companion.getMessageOrToString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.regex.Pattern

const val ACCOUNT_MANAGE_SCREEN_TAG = "AccountManageScreen"

private val localNamePattern = Pattern.compile("[^a-zA-Z0-9_]")

private val otherServerConfig = MutableStateFlow(Servers(ArrayList()))
private val otherServerConfigFile = File(PathManager.DIR_GAME, "other_servers.json")

private fun refreshOtherServer() {
    val text: String = otherServerConfigFile.takeIf { it.exists() }?.readText() ?: return
    val config = CryptoManager.decrypt(text)
    otherServerConfig.value = GSON.fromJson(config, Servers::class.java)
}

@Composable
fun AccountManageScreen() {
    BaseScreen(
        screenTag = ACCOUNT_MANAGE_SCREEN_TAG,
        currentTag = MutableStates.mainScreenTag
    ) { isVisible ->
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            ServerTypeTab(
                isVisible = isVisible,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(all = 12.dp)
                    .weight(2.5f)
            )
            AccountsLayout(
                isVisible = isVisible,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(top = 12.dp, end = 12.dp, bottom = 12.dp)
                    .weight(7.5f)
            )
        }
    }
}

@Composable
private fun ServerTypeTab(
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val xOffset by swapAnimateDpAsState(
        targetValue = (-40).dp,
        swapIn = isVisible
    )

    LaunchedEffect(true) {
        runCatching {
            refreshOtherServer()
        }.onFailure {
            Log.e("ServerTypeTab", "Failed to refresh other server", it)
        }
    }

    var localLoginDialog by rememberSaveable { mutableStateOf(false) }
    var serverOperation by remember { mutableStateOf<ServerOperation>(ServerOperation.None) }
    var otherLoginOperation by remember { mutableStateOf<OtherLoginOperation>(OtherLoginOperation.None) }
    var microsoftLoginOperation by remember { mutableStateOf<MicrosoftLoginOperation>(MicrosoftLoginOperation.None) }

    ServerTypeOperation(
        showLocalLoginDialog = localLoginDialog,
        updateLocalLoginDialog = { localLoginDialog = it },
        serverOperation = serverOperation,
        updateServerOperation = { serverOperation = it },
        otherLoginOperation = otherLoginOperation,
        updateOtherLoginOperation = { otherLoginOperation = it },
        microsoftLoginOperation = microsoftLoginOperation,
        updateMicrosoftLoginOperation = { microsoftLoginOperation = it }
    )

    Surface(
        modifier = modifier
            .offset {
                IntOffset(
                    x = xOffset.roundToPx(),
                    y = 0
                )
            }
            .fillMaxHeight(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 4.dp
    ) {
        Column {
            Column(
                modifier = Modifier
                    .padding(all = 12.dp)
                    .verticalScroll(state = rememberScrollState())
                    .weight(1f)
            ) {
                LoginItem(
                    modifier = Modifier.fillMaxWidth(),
                    serverName = stringResource(R.string.account_type_microsoft),
                ) {
                    if (!isMicrosoftLogging()) {
                        microsoftLoginOperation = MicrosoftLoginOperation.RunTask
                    }
                }
                LoginItem(
                    modifier = Modifier.fillMaxWidth(),
                    serverName = stringResource(R.string.account_type_local)
                ) {
                    localLoginDialog = true
                }
                val servers by otherServerConfig.collectAsState()
                servers.server.forEachIndexed { index, server ->
                    ServerItem(
                        server = server,
                        onClick = { otherLoginOperation = OtherLoginOperation.OnLogin(server) },
                        onDeleteClick = { serverOperation = ServerOperation.Delete(server.serverName, index) }
                    )
                }
            }

            ScalingActionButton(
                modifier = Modifier
                    .padding(PaddingValues(horizontal = 12.dp, vertical = 8.dp))
                    .fillMaxWidth(),
                onClick = { serverOperation = ServerOperation.AddNew }
            ) {
                Text(
                    text = stringResource(R.string.account_add_new_server)
                )
            }
        }
    }
}

@Composable
private fun ServerTypeOperation(
    showLocalLoginDialog: Boolean,
    updateLocalLoginDialog: (Boolean) -> Unit,
    serverOperation: ServerOperation,
    updateServerOperation: (ServerOperation) -> Unit,
    otherLoginOperation: OtherLoginOperation,
    updateOtherLoginOperation: (OtherLoginOperation) -> Unit,
    microsoftLoginOperation: MicrosoftLoginOperation,
    updateMicrosoftLoginOperation: (MicrosoftLoginOperation) -> Unit
) {
    if (showLocalLoginDialog) {
        var showAlert by rememberSaveable { mutableStateOf(false) }

        var username by rememberSaveable { mutableStateOf("") }
        var isUserNameInvalid by rememberSaveable { mutableStateOf(false) }

        SimpleEditDialog(
            title = stringResource(R.string.account_type_local),
            value = username,
            onValueChange = { username = it.trim() },
            label = { Text(text = stringResource(R.string.account_label_username)) },
            isError = isUserNameInvalid,
            supportingText = {
                val errorText = when {
                    username.isEmpty() -> stringResource(R.string.account_supporting_username_invalid_empty)
                    username.length <= 2 -> stringResource(R.string.account_supporting_username_invalid_short)
                    username.length > 16 -> stringResource(R.string.account_supporting_username_invalid_long)
                    localNamePattern.matcher(username).find() -> stringResource(R.string.account_supporting_username_invalid_illegal_characters)
                    else -> ""
                }.also {
                    isUserNameInvalid = it.isNotEmpty()
                }
                if (isUserNameInvalid) {
                    Text(text = errorText)
                }
            },
            singleLine = true,
            onDismissRequest = { updateLocalLoginDialog(false) },
            onConfirm = {
                if (username.isNotEmpty()) {
                    if (isUserNameInvalid) {
                        showAlert = true
                        return@SimpleEditDialog
                    }
                    localLogin(userName = username)
                    updateLocalLoginDialog(false)
                }
            }
        )

        if (showAlert) {
            SimpleAlertDialog(
                title = stringResource(R.string.account_supporting_username_invalid_title),
                text = stringResource(R.string.account_supporting_username_invalid_local_message),
                confirmText = stringResource(R.string.account_supporting_username_invalid_still_use),
                onConfirm = {
                    showAlert = false
                    updateLocalLoginDialog(false)
                    localLogin(userName = username)
                },
                onDismiss = {
                    showAlert = false
                }
            )
        }
    }

    when (serverOperation) {
        is ServerOperation.AddNew -> {
            var serverUrl by rememberSaveable { mutableStateOf("") }
            SimpleEditDialog(
                title = stringResource(R.string.account_add_new_server),
                value = serverUrl,
                onValueChange = { serverUrl = it.trim() },
                label = { Text(text = stringResource(R.string.account_label_server_url)) },
                singleLine = true,
                onDismissRequest = { updateServerOperation(ServerOperation.None) },
                onConfirm = {
                    if (serverUrl.isNotEmpty()) {
                        updateServerOperation(ServerOperation.Add(serverUrl))
                    }
                }
            )
        }
        is ServerOperation.Add -> {
            addOtherServer(
                serverUrl = serverOperation.serverUrl,
                serverConfig = { otherServerConfig },
                serverConfigFile = otherServerConfigFile,
                onThrowable = { updateServerOperation(ServerOperation.OnThrowable(it)) }
            )
            updateServerOperation(ServerOperation.None)
        }
        is ServerOperation.Delete -> {
            SimpleAlertDialog(
                title = stringResource(R.string.account_other_login_delete_server_title),
                text = stringResource(
                    R.string.account_other_login_delete_server_message,
                    serverOperation.serverName
                ),
                onDismiss = { updateServerOperation(ServerOperation.None) },
                onConfirm = {
                    otherServerConfig.update { currentConfig ->
                        currentConfig.server.removeAt(serverOperation.serverIndex)
                        val configString = GSON.toJson(currentConfig, Servers::class.java)
                        val text = CryptoManager.encrypt(configString)
                        runCatching {
                            otherServerConfigFile.writeText(text)
                        }.onFailure {
                            Log.e("ServerTypeTab", "Failed to save other server config", it)
                        }
                        currentConfig.copy()
                    }
                    updateServerOperation(ServerOperation.None)
                }
            )
        }
        is ServerOperation.OnThrowable -> {
            ObjectStates.updateThrowable(
                ObjectStates.ThrowableMessage(
                    title = stringResource(R.string.account_other_login_adding_failure),
                    message = serverOperation.throwable.getMessageOrToString()
                )
            )
            updateServerOperation(ServerOperation.None)
        }
        is ServerOperation.None -> {}
    }

    val context = LocalContext.current
    when (otherLoginOperation) {
        is OtherLoginOperation.OnLogin -> {
            OtherServerLoginDialog(
                server = otherLoginOperation.server,
                onRegisterClick = { url ->
                    NetWorkUtils.openLink(context, url)
                    updateOtherLoginOperation(OtherLoginOperation.None)
                },
                onDismissRequest = { updateOtherLoginOperation(OtherLoginOperation.None) },
                onConfirm = { email, password ->
                    updateOtherLoginOperation(OtherLoginOperation.None)
                    OtherLoginHelper(
                        otherLoginOperation.server, email, password,
                        onSuccess = { account, task ->
                            task.updateMessage(R.string.account_logging_in_saving)
                            account.downloadSkin()
                            saveAccount(account)
                        },
                        onFailed = { error ->
                            updateOtherLoginOperation(OtherLoginOperation.OnFailed(error))
                        }
                    ).createNewAccount(context) { availableProfiles, selectedFunction ->
                        updateOtherLoginOperation(
                            OtherLoginOperation.SelectRole(
                                availableProfiles,
                                selectedFunction
                            )
                        )
                    }
                }
            )
        }
        is OtherLoginOperation.OnFailed -> {
            ObjectStates.updateThrowable(
                ObjectStates.ThrowableMessage(
                    title = stringResource(R.string.account_logging_in_failed),
                    message = otherLoginOperation.error
                )
            )
            updateOtherLoginOperation(OtherLoginOperation.None)
        }
        is OtherLoginOperation.SelectRole -> {
            SimpleListDialog(
                title = stringResource(R.string.account_other_login_select_role),
                itemsProvider = { otherLoginOperation.profiles },
                itemTextProvider = { it.name },
                onItemSelected = { otherLoginOperation.selected(it) },
                onDismissRequest = { updateOtherLoginOperation(OtherLoginOperation.None) }
            )
        }
        is OtherLoginOperation.None -> {}
    }

    when (microsoftLoginOperation) {
        is MicrosoftLoginOperation.None -> {}
        is MicrosoftLoginOperation.RunTask -> {
            microsoftLogin(
                context = context,
                updateOperation = { updateMicrosoftLoginOperation(it) }
            )
            updateMicrosoftLoginOperation(MicrosoftLoginOperation.None)
        }
    }
}

@Composable
private fun AccountsLayout(
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val yOffset by swapAnimateDpAsState(
        targetValue = (-40).dp,
        swapIn = isVisible
    )

    val accounts by AccountsManager.accountsFlow.collectAsState()
    val currentAccount by AccountsManager.currentAccountFlow.collectAsState()

    Card(
        modifier = modifier.offset {
            IntOffset(
                x = 0,
                y = yOffset.roundToPx()
            )
        },
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        var accountOperation by remember { mutableStateOf<AccountOperation>(AccountOperation.None) }
        AccountOperation(
            accountOperation = accountOperation,
            updateAccountOperation = { accountOperation = it }
        )

        if (accounts.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape = MaterialTheme.shapes.extraLarge),
                contentPadding = PaddingValues(all = 12.dp)
            ) {
                items(accounts.size) { index ->
                    val account = accounts[index]

                    var accountSkinOperation by remember { mutableStateOf<AccountSkinOperation>(AccountSkinOperation.None) }
                    AccountSkinOperation(
                        account = account,
                        accountSkinOperation = accountSkinOperation,
                        updateOperation = { accountSkinOperation = it }
                    )

                    val skinPicker = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument()
                    ) { uri ->
                        uri?.let { result ->
                            accountSkinOperation = AccountSkinOperation.SaveSkin(result)
                        }
                    }

                    val context = LocalContext.current
                    AccountItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = if (index != accounts.size - 1) 12.dp else 0.dp),
                        currentAccount = currentAccount,
                        account = account,
                        onSelected = { uniqueUUID ->
                            AccountsManager.setCurrentAccount(uniqueUUID)
                        },
                        onChangeSkin = {
                            if (account.isMicrosoftAccount()) {
                                NetWorkUtils.openLink(context = context, link = UrlManager.URL_MINECRAFT_CHANGE_SKIN)
                            } else if (account.isLocalAccount()) {
                                skinPicker.launch(arrayOf("image/*"))
                            }
                        },
                        onResetSkin = {
                            accountSkinOperation = AccountSkinOperation.PreResetSkin
                        },
                        onRefreshClick = { accountOperation = AccountOperation.Refresh(account) },
                        onDeleteClick = { accountOperation = AccountOperation.Delete(account) }
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                ScalingLabel(
                    modifier = Modifier.align(Alignment.Center),
                    text = stringResource(R.string.account_no_account)
                )
            }
        }
    }
}

@Composable
private fun AccountSkinOperation(
    account: Account,
    accountSkinOperation: AccountSkinOperation,
    updateOperation: (AccountSkinOperation) -> Unit
) {
    val context = LocalContext.current
    when (accountSkinOperation) {
        is AccountSkinOperation.None -> {}
        is AccountSkinOperation.SaveSkin -> {
            val skinFile = account.getSkinFile()
            TaskSystem.submitTask(
                Task.runTask(
                    dispatcher = Dispatchers.IO,
                    task = {
                        context.copyLocalFile(accountSkinOperation.uri, skinFile)
                        updateOperation(AccountSkinOperation.SelectSkinModel)
                    },
                    onError = { th ->
                        FileUtils.deleteQuietly(skinFile)
                        ObjectStates.updateThrowable(
                            ObjectStates.ThrowableMessage(
                                title = context.getString(R.string.error_import_image),
                                message = th.getMessageOrToString()
                            )
                        )
                        updateOperation(AccountSkinOperation.None)
                    }
                )
            )
        }
        is AccountSkinOperation.SelectSkinModel -> {
            SelectSkinModelDialog(
                onDismissRequest = {
                    updateOperation(AccountSkinOperation.None)
                },
                onSelected = { type ->
                    account.skinModelType = type.name
                    updateOperation(AccountSkinOperation.AlertModel)
                }
            )
        }
        is AccountSkinOperation.AlertModel -> {
            SimpleAlertDialog(
                title = stringResource(R.string.generic_warning),
                text = stringResource(R.string.account_change_skin_select_model_alert),
                onDismiss = {
                    saveAccount(account)
                    updateOperation(AccountSkinOperation.None)
                }
            )
        }
        is AccountSkinOperation.PreResetSkin -> {
            SimpleAlertDialog(
                title = stringResource(R.string.generic_reset),
                text = stringResource(R.string.account_change_skin_reset_skin_message),
                onDismiss = { updateOperation(AccountSkinOperation.None) },
                onConfirm = { updateOperation(AccountSkinOperation.ResetSkin) }
            )
        }
        is AccountSkinOperation.ResetSkin -> {
            account.apply {
                FileUtils.deleteQuietly(getSkinFile())
                skinModelType = ""
                saveAccount(this)
            }
        }
    }
}

@Composable
private fun AccountOperation(
    accountOperation: AccountOperation,
    updateAccountOperation: (AccountOperation) -> Unit
) {
    val context = LocalContext.current
    when (accountOperation) {
        is AccountOperation.Delete -> {
            //删除账号前弹出Dialog提醒
            SimpleAlertDialog(
                title = stringResource(R.string.account_delete_title),
                text = stringResource(R.string.account_delete_message,
                    accountOperation.account.username),
                onConfirm = {
                    AccountsManager.deleteAccount(accountOperation.account)
                    updateAccountOperation(AccountOperation.None)
                },
                onDismiss = { updateAccountOperation(AccountOperation.None) }
            )
        }
        is AccountOperation.Refresh -> {
            if (NetWorkUtils.isNetworkAvailable(context)) {
                AccountsManager.performLogin(
                    context = context,
                    account = accountOperation.account,
                    onSuccess = { account, task ->
                        task.updateMessage(R.string.account_logging_in_saving)
                        account.downloadSkin()
                        saveAccount(account)
                    },
                    onFailed = { updateAccountOperation(AccountOperation.OnFailed(it)) }
                )
            }
            updateAccountOperation(AccountOperation.None)
        }
        is AccountOperation.OnFailed -> {
            ObjectStates.updateThrowable(
                ObjectStates.ThrowableMessage(
                    title = stringResource(R.string.account_logging_in_failed),
                    message = accountOperation.error
                )
            )
            updateAccountOperation(AccountOperation.None)
        }
        is AccountOperation.None -> {}
    }
}
