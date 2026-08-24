package dev.achmad.finbox.features.settings.llm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.achmad.finbox.R
import dev.achmad.finbox.theme.components.AppBar

/**
 * One provider: label it, point it somewhere, and prove it works.
 *
 * The model is chosen from what the endpoint lists rather than typed. A
 * mistyped model id is the kind of mistake that surfaces halfway through a
 * backfill, which is the worst possible moment to learn about it.
 */
data class SettingsLlmProviderScreen(private val id: String?) : Screen {

    override val key: ScreenKey = uniqueScreenKey

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = remember { LlmProviderScreenModel(id) }
        val state by model.state.collectAsState()
        var expanded by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(R.string.llm_provider_title),
                    navigateUp = navigator::pop,
                    actions = listOf(
                        AppBar.Action(
                            title = stringResource(R.string.action_save),
                            icon = Icons.Outlined.Check,
                            enabled = state.canSave,
                            onClick = {
                                model.save()
                                navigator.pop()
                            },
                        ),
                    ),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = model::onName,
                    label = { Text(stringResource(R.string.llm_name)) },
                    supportingText = { Text(stringResource(R.string.llm_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.endpoint,
                    onValueChange = model::onEndpoint,
                    label = { Text(stringResource(R.string.llm_endpoint)) },
                    supportingText = { Text(stringResource(R.string.llm_endpoint_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.apiKey,
                    onValueChange = model::onApiKey,
                    label = { Text(stringResource(R.string.llm_api_key)) },
                    // An edit shows no key back, so say what leaving it blank does.
                    supportingText = if (state.hasSavedKey) {
                        { Text(stringResource(R.string.llm_api_key_saved)) }
                    } else {
                        null
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = model::fetchModels,
                        enabled = state.canFetch,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.llm_connect)) }
                    OutlinedButton(
                        onClick = model::test,
                        enabled = state.canTest,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.llm_test)) }
                }

                if (state.busy) {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                }

                if (state.manualModel) {
                    OutlinedTextField(
                        value = state.model,
                        onValueChange = model::onModel,
                        label = { Text(stringResource(R.string.llm_model_manual)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (state.models.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                    ) {
                        OutlinedTextField(
                            value = state.model,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.llm_model)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            state.models.forEach { id ->
                                DropdownMenuItem(
                                    text = { Text(id) },
                                    onClick = {
                                        model.onModel(id)
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                state.message?.let { Message(it) }
            }
        }
    }

    @Composable
    private fun Message(message: LlmProviderScreenModel.Message) {
        val text = when (message) {
            is LlmProviderScreenModel.Message.ConnectFailed ->
                stringResource(R.string.llm_connect_failed, message.reason)
            LlmProviderScreenModel.Message.NoModels -> stringResource(R.string.llm_no_models)
            is LlmProviderScreenModel.Message.TestOk ->
                stringResource(R.string.llm_test_ok, message.reply)
            is LlmProviderScreenModel.Message.TestFailed ->
                stringResource(R.string.llm_test_failed, message.reason)
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
