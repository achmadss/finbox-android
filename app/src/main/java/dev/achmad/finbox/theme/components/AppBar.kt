package dev.achmad.finbox.theme.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigateUp: (() -> Unit)? = null,
    actions: List<AppBar.AppBarAction> = emptyList(),
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            navigateUp?.let {
                IconButton(onClick = it) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Navigate up",
                    )
                }
            }
        },
        actions = { AppBarActions(actions) },
    )
}

@Composable
fun RowScope.AppBarActions(actions: List<AppBar.AppBarAction>) {
    actions.filterIsInstance<AppBar.Action>().forEach { action ->
        IconButton(
            onClick = action.onClick,
            enabled = action.enabled,
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = action.title,
                tint = action.iconTint ?: LocalContentColor.current,
            )
        }
    }

    val overflowActions = actions.filterIsInstance<AppBar.OverflowAction>()
    if (overflowActions.isEmpty()) return

    var showMenu by remember { mutableStateOf(false) }
    IconButton(onClick = { showMenu = true }) {
        BadgedBox(
            badge = { if (overflowActions.any { it.badge > 0 }) Badge() },
        ) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = "More",
            )
        }
    }
    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = { showMenu = false },
    ) {
        overflowActions.forEach { action ->
            DropdownMenuItem(
                text = { Text(action.title) },
                leadingIcon = action.icon?.let { { Icon(it, contentDescription = null) } },
                trailingIcon = { if (action.badge > 0) Badge { Text(action.badge.toString()) } },
                onClick = {
                    showMenu = false
                    action.onClick()
                },
            )
        }
    }
}

sealed interface AppBar {
    sealed interface AppBarAction

    data class Action(
        val title: String,
        val icon: ImageVector,
        val iconTint: Color? = null,
        val enabled: Boolean = true,
        val onClick: () -> Unit,
    ) : AppBarAction

    /** [badge] > 0 shows the count on the item and a dot on the overflow icon. */
    data class OverflowAction(
        val title: String,
        val icon: ImageVector? = null,
        val badge: Int = 0,
        val onClick: () -> Unit,
    ) : AppBarAction
}
