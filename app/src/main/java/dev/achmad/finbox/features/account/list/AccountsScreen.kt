package dev.achmad.finbox.features.account.list

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.SubcomposeAsyncImage
import dev.achmad.data.model.EmailAccount
import dev.achmad.finbox.features.account.detail.AccountDetailsScreen
import dev.achmad.finbox.theme.components.AppBar
import dev.achmad.finbox.util.formatter.formatDate
import dev.achmad.finbox.util.ui.rememberUse24HourClock
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.achmad.finbox.R

object AccountsScreen : Screen {
    private fun readResolve(): Any = AccountsScreen

    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { AccountsScreenModel() }
        val accounts by model.accounts.collectAsState()
        val disabledByAccount by model.disabledByAccount.collectAsState()
        val parsers by model.parsers.collectAsState()
        var confirmRemove by remember { mutableStateOf<EmailAccount?>(null) }
        val addAccount = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            model.addAccount(result.data)
        }

        Scaffold(
            topBar = {
                AppBar(
                    modifier = Modifier.dropShadow(RectangleShape, Shadow(3.dp)),
                    title = stringResource(R.string.accounts),
                    navigateUp = navigator::pop,
                    actions = listOf(
                        AppBar.Action(
                            title = stringResource(R.string.action_add_account),
                            icon = Icons.Outlined.Add,
                            onClick = { addAccount.launch(model.authorizationIntent()) },
                        ),
                    ),
                )
            },
        ) { padding ->
            if (accounts.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    Text(
                        stringResource(R.string.accounts_empty),
                        modifier = Modifier.padding(horizontal = 32.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Scaffold
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(accounts, key = { "account-${it.id}" }) { account ->
                    val disabled = disabledByAccount[account.id].orEmpty()
                    AccountItem(
                        modifier = Modifier.animateItem(),
                        account = account,
                        // Counted off the installed parsers, not off the rows: a parser with
                        // no row of its own is one this account reads with.
                        parsers = parsers.count { it.id !in disabled },
                        onClickItem = { navigator.push(AccountDetailsScreen(account.id)) },
                        onLongClickItem = { confirmRemove = account },
                    )
                }
            }
        }

        confirmRemove?.let { account ->
            RemoveAccountConfirmation(
                email = account.email,
                onConfirm = { model.remove(account.id) },
                onDismiss = { confirmRemove = null },
            )
        }
    }
}

@Composable
private fun AccountItem(
    account: EmailAccount,
    parsers: Int,
    onClickItem: () -> Unit,
    onLongClickItem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClickItem, onLongClick = onLongClickItem)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountAvatar(photoUrl = account.photoUrl, size = 40.dp)

        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                text = account.email,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            AccountSubtitle(account, parsers)
        }

        IconButton(onClick = onClickItem) {
            Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.label_account_info))
        }
    }
}

/**
 * The account's Google picture, or the placeholder when there is none — an account added
 * before the app asked for the `profile` scope, an owner who set no picture, a load that
 * failed. The placeholder is the same icon the rest of the app uses for an account.
 */
@Composable
fun AccountAvatar(photoUrl: String?, size: Dp, modifier: Modifier = Modifier) {
    val shape = modifier.size(size).clip(CircleShape)
    if (photoUrl == null) {
        Icon(
            imageVector = Icons.Outlined.AccountCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = shape,
        )
        return
    }
    SubcomposeAsyncImage(
        model = photoUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = shape,
        loading = { AvatarPlaceholder() },
        error = { AvatarPlaceholder() },
    )
}

@Composable
private fun AvatarPlaceholder() {
    Icon(
        imageVector = Icons.Outlined.AccountCircle,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun AccountSubtitle(account: EmailAccount, parsers: Int) {
    val use24Hour = rememberUse24HourClock()
    val details = listOf(
        account.lastSyncAt
            ?.let { stringResource(R.string.synced, formatDate(it, use24Hour)) }
            ?: stringResource(R.string.never_synced),
        pluralStringResource(R.plurals.account_parser_count, parsers, parsers),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = details.joinToString(" • "),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The one state worth shouting about: the mailbox is there but nothing is read from it.
        if (!account.enabled) {
            Text(
                text = " • " + stringResource(R.string.account_badge_sync_off),
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
fun RemoveAccountConfirmation(
    email: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
        title = { Text(stringResource(R.string.action_remove_account)) },
        // Says what survives, because nothing here deletes what was already read.
        text = { Text(stringResource(R.string.remove_account_confirmation, email)) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_remove)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
