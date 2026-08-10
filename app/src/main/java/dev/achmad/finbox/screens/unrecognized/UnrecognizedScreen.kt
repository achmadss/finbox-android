package dev.achmad.finbox.screens.unrecognized

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import dev.achmad.domain.model.UnrecognizedEmail
import dev.achmad.domain.repository.UnrecognizedEmailRepository
import dev.achmad.finbox.util.formatDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object UnrecognizedScreen : Screen, KoinComponent {

    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val model = rememberScreenModel { UnrecognizedScreenModel() }
        val emails by model.emails.collectAsState()

        LazyColumn(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("Emails that no parser could handle", style = MaterialTheme.typography.bodyMedium)
            }
            items(emails, key = { it.id }) { email ->
                EmailCard(
                    email = email,
                    onReviewed = { model.markReviewed(email.id) },
                    onDelete = { model.delete(email.id) },
                )
            }
        }
    }
}

class UnrecognizedScreenModel : ScreenModel, KoinComponent {

    private val repository: UnrecognizedEmailRepository by inject()

    private val _emails = MutableStateFlow<List<UnrecognizedEmail>>(emptyList())
    val emails: StateFlow<List<UnrecognizedEmail>> = _emails

    init {
        screenModelScope.launch { repository.emails().collect { _emails.value = it } }
    }

    fun markReviewed(id: String) {
        screenModelScope.launch { repository.markReviewed(id) }
    }

    fun delete(id: String) {
        screenModelScope.launch { repository.delete(id) }
    }
}

@Composable
private fun EmailCard(
    email: UnrecognizedEmail,
    onReviewed: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = email.subject ?: "(no subject)",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = listOfNotNull(
                        email.sender,
                        formatDate(email.receivedAt),
                        email.reason,
                    ).joinToString(" \u2022 "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onReviewed) {
                Icon(Icons.Filled.Check, contentDescription = "Mark reviewed")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        }
    }
}
