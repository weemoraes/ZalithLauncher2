package com.movtery.zalithlauncher.ui.screens.content.elements

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.createBitmap
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.account.Account
import com.movtery.zalithlauncher.game.account.AccountType
import com.movtery.zalithlauncher.game.account.getAccountTypeName
import com.movtery.zalithlauncher.game.account.isLocalAccount
import com.movtery.zalithlauncher.game.account.isSkinChangeAllowed
import com.movtery.zalithlauncher.game.account.otherserver.models.AuthResult
import com.movtery.zalithlauncher.game.account.otherserver.models.Servers.Server
import com.movtery.zalithlauncher.game.skin.SkinModelType
import com.movtery.zalithlauncher.utils.animation.getAnimateTween
import java.io.IOException
import java.nio.file.Files

/**
 * 微软登录的操作状态
 */
sealed interface MicrosoftLoginOperation {
    data object None : MicrosoftLoginOperation
    data object RunTask: MicrosoftLoginOperation
}

/**
 * 添加认证服务器时的状态
 */
sealed interface ServerOperation {
    data object None : ServerOperation
    data object AddNew : ServerOperation
    data class Delete(val serverName: String, val serverIndex: Int) : ServerOperation
    data class Add(val serverUrl: String) : ServerOperation
    data class OnThrowable(val throwable: Throwable) : ServerOperation
}

/**
 * 账号操作的状态
 */
sealed interface AccountOperation {
    data object None : AccountOperation
    data class Delete(val account: Account) : AccountOperation
    data class Refresh(val account: Account) : AccountOperation
    data class OnFailed(val error: String) : AccountOperation
}

/**
 * 更换账号皮肤的状态
 */
sealed interface AccountSkinOperation {
    data object None: AccountSkinOperation
    data class SaveSkin(val uri: Uri): AccountSkinOperation
    data object SelectSkinModel: AccountSkinOperation
    data object AlertModel: AccountSkinOperation
    data object PreResetSkin: AccountSkinOperation
    data object ResetSkin: AccountSkinOperation
}

/**
 * 认证服务器登陆时的状态
 */
sealed interface OtherLoginOperation {
    data object None : OtherLoginOperation
    data class OnLogin(val server: Server) : OtherLoginOperation
    data class OnFailed(val error: String) : OtherLoginOperation
    data class SelectRole(
        val profiles: List<AuthResult.AvailableProfiles>,
        val selected: (AuthResult.AvailableProfiles) -> Unit
    ) : OtherLoginOperation
}

@Composable
fun AccountAvatar(
    modifier: Modifier = Modifier,
    avatarSize: Int = 64,
    account: Account?,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .clip(shape = MaterialTheme.shapes.extraLarge)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .padding(all = 12.dp)
        ) {
            if (account != null) {
                PlayerFace(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    account = account,
                    avatarSize = avatarSize
                )
            } else {
                Icon(
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.CenterHorizontally),
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = contentColor
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                text = account?.username ?: stringResource(R.string.account_add_new_account),
                maxLines = 1,
                style = MaterialTheme.typography.titleSmall,
                color = contentColor
            )
            if (account != null) {
                Text(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    text = getAccountTypeName(context, account),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
fun PlayerFace(
    modifier: Modifier = Modifier,
    account: Account,
    avatarSize: Int = 64
) {
    val context = LocalContext.current
    val avatarBitmap = remember(account) {
        getAvatarFromAccount(context, account, avatarSize).asImageBitmap()
    }

    val newAvatarSize = avatarBitmap.width.dp

    Image(
        modifier = modifier.size(newAvatarSize),
        bitmap = avatarBitmap,
        contentDescription = null
    )
}

@Composable
fun AccountItem(
    modifier: Modifier = Modifier,
    currentAccount: Account?,
    account: Account,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onSelected: (uniqueUUID: String) -> Unit = {},
    onChangeSkin: () -> Unit = {},
    onResetSkin: () -> Unit = {},
    onRefreshClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {}
) {
    val selected = currentAccount?.uniqueUUID == account.uniqueUUID
    val scale = remember { Animatable(initialValue = 0.95f) }
    LaunchedEffect(Unit) {
        scale.animateTo(targetValue = 1f, animationSpec = getAnimateTween())
    }
    Surface(
        modifier = modifier.graphicsLayer(scaleY = scale.value, scaleX = scale.value),
        color = color,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.large,
        shadowElevation = 2.dp,
        onClick = {
            if (selected) return@Surface
            onSelected(account.uniqueUUID)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape = MaterialTheme.shapes.large)
                .padding(all = 8.dp)
        ) {
            RadioButton(
                selected = selected,
                onClick = {
                    if (selected) return@RadioButton
                    onSelected(account.uniqueUUID)
                }
            )
            PlayerFace(
                modifier = Modifier.align(Alignment.CenterVertically),
                account = account,
                avatarSize = 46
            )
            Spacer(modifier = Modifier.width(18.dp))
            Column(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .weight(1f)
            ) {
                val context = LocalContext.current
                Text(text = account.username)
                Text(
                    text = getAccountTypeName(context, account),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Row {
                val isLocalHasSkin = account.isLocalAccount() && account.getSkinFile().exists()
                val icon = if (isLocalHasSkin) Icons.Default.RestartAlt else Icons.Outlined.Checkroom
                val description = if (isLocalHasSkin) {
                    stringResource(R.string.generic_reset)
                } else {
                    stringResource(R.string.account_change_skin)
                }
                val onClickAction = if (isLocalHasSkin) onResetSkin else onChangeSkin

                IconButton(
                    onClick = onClickAction,
                    enabled = account.isSkinChangeAllowed()
                ) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        imageVector = icon,
                        contentDescription = description
                    )
                }
                IconButton(
                    onClick = onRefreshClick,
                    enabled = account.accountType != AccountType.LOCAL.tag
                ) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.generic_refresh)
                    )
                }
                IconButton(
                    onClick = onDeleteClick
                ) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.generic_delete)
                    )
                }
            }
        }
    }
}

@Composable
fun LoginItem(
    modifier: Modifier = Modifier,
    serverName: String,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .clip(shape = MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(PaddingValues(horizontal = 4.dp, vertical = 12.dp))
    ) {
        Icon(
            modifier = Modifier.size(24.dp),
            imageVector = Icons.Default.Add,
            contentDescription = serverName,
            tint = contentColor
        )
        Spacer(
            modifier = Modifier.width(8.dp)
        )
        Text(
            modifier = Modifier.align(Alignment.CenterVertically),
            text = serverName,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun ServerItem(
    modifier: Modifier = Modifier,
    server: Server,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    onClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .clip(shape = MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(start = 4.dp)
    ) {
        Text(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically),
            text = server.serverName,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(
            modifier = Modifier.width(8.dp)
        )
        IconButton(
            onClick = onDeleteClick
        ) {
            Icon(
                modifier = Modifier.size(24.dp),
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.generic_delete),
                tint = contentColor
            )
        }
    }
}

@Composable
fun OtherServerLoginDialog(
    server: Server,
    onRegisterClick: (url: String) -> Unit = {},
    onDismissRequest: () -> Unit = {},
    onConfirm: (email: String, password: String) -> Unit = { _, _ -> }
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = server.serverName,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.size(16.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    isError = email.isEmpty(),
                    label = { Text(text = stringResource(R.string.account_label_email)) },
                    supportingText = {
                        if (email.isEmpty()) {
                            Text(text = stringResource(R.string.account_supporting_email_invalid_empty))
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large
                )
                Spacer(modifier = Modifier.size(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    isError = password.isEmpty(),
                    label = { Text(text = stringResource(R.string.account_label_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = Transparent,
                    ),
                    supportingText = {
                        if (password.isEmpty()) {
                            Text(text = stringResource(R.string.account_supporting_password_invalid_empty))
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large
                )
                if (!server.register.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = stringResource(R.string.account_other_login_register),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Start).clickable { onRegisterClick(server.register!!) }
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                } else Spacer(modifier = Modifier.size(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onDismissRequest
                    ) {
                        Text(text = stringResource(R.string.generic_cancel))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (email.isNotEmpty() && password.isNotEmpty()) {
                                onConfirm(email, password)
                            }
                        }
                    ) {
                        Text(text = stringResource(R.string.generic_confirm))
                    }
                }
            }
        }
    }
}

@Composable
fun SelectSkinModelDialog(
    onDismissRequest: () -> Unit = {},
    onSelected: (SkinModelType) -> Unit = {}
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.account_change_skin_select_model_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.size(16.dp))
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.account_change_skin_select_model_message),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.size(16.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            onSelected(SkinModelType.STEVE)
                        }
                    ) {
                        Text(text = stringResource(R.string.account_change_skin_model_steve))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            onSelected(SkinModelType.ALEX)
                        }
                    ) {
                        Text(text = stringResource(R.string.account_change_skin_model_alex))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onDismissRequest
                    ) {
                        Text(text = stringResource(R.string.generic_cancel))
                    }
                }
            }
        }
    }
}

@Throws(Exception::class)
private fun getAvatarFromAccount(context: Context, account: Account, size: Int): Bitmap {
    val skin = account.getSkinFile()
    if (skin.exists()) {
        runCatching {
            Files.newInputStream(skin.toPath()).use { `is` ->
                val bitmap = BitmapFactory.decodeStream(`is`)
                    ?: throw IOException("Failed to read the skin picture and try to parse it to a bitmap")
                return getAvatar(bitmap, size)
            }
        }.onFailure { e ->
            Log.e("SkinLoader", "Failed to load avatar from locally!", e)
        }
    }
    return getDefaultAvatar(context, size)
}

@Throws(Exception::class)
private fun getDefaultAvatar(context: Context, size: Int): Bitmap {
    val `is` = context.assets.open("steve.png")
    return getAvatar(BitmapFactory.decodeStream(`is`), size)
}

private fun getAvatar(skin: Bitmap, size: Int): Bitmap {
    val faceOffset = Math.round(size / 18.0).toFloat()
    val scaleFactor = skin.width / 64.0f
    val faceSize = Math.round(8 * scaleFactor)
    val faceBitmap = Bitmap.createBitmap(skin, faceSize, faceSize, faceSize, faceSize, null as Matrix?, false)
    val hatBitmap = Bitmap.createBitmap(skin, Math.round(40 * scaleFactor), faceSize, faceSize, faceSize, null as Matrix?, false)
    val avatar = createBitmap(size, size)
    val canvas = android.graphics.Canvas(avatar)
    val faceScale = ((size - 2 * faceOffset) / faceSize)
    val hatScale = (size.toFloat() / faceSize)
    var matrix = Matrix()
    matrix.postScale(faceScale, faceScale)
    val newFaceBitmap = Bitmap.createBitmap(faceBitmap, 0, 0, faceSize, faceSize, matrix, false)
    matrix = Matrix()
    matrix.postScale(hatScale, hatScale)
    val newHatBitmap = Bitmap.createBitmap(hatBitmap, 0, 0, faceSize, faceSize, matrix, false)
    canvas.drawBitmap(newFaceBitmap, faceOffset, faceOffset, Paint(Paint.ANTI_ALIAS_FLAG))
    canvas.drawBitmap(newHatBitmap, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG))
    return avatar
}