package dev.achmad.finbox.features.onboarding

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.achmad.finbox.R
import dev.achmad.finbox.core.parser.AvailableParser
import dev.achmad.finbox.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingInstallParsersContent(
    parsers: List<AvailableParser> = listOf(),
    loading: Boolean = false,
    installing: Boolean = false,
    onRefresh: () -> Unit = {},
    onClickInstallParsers: (List<AvailableParser>) -> Unit = {},
) {
    val selectedParsers = remember { mutableStateListOf<AvailableParser>() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.onboarding_parsers_title))
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedParsers.isNotEmpty() && !installing,
                    onClick = { onClickInstallParsers(selectedParsers.toList()) },
                ) {
                    Text(
                        stringResource(
                            if (installing) R.string.onboarding_parsers_installing
                            else R.string.onboarding_parsers_install
                        )
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    ) { contentPadding ->
        // Nothing to choose from yet: a spinner instead of an empty box that
        // looks like "no parsers exist".
        if (loading && parsers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        PullToRefreshBox(
            isRefreshing = loading,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // The gesture needs something scrollable to pull on.
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_parsers_subtitle),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            shape = RoundedCornerShape(8.dp)
                        )
                ) {
                    parsers.forEachIndexed { index, parser ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (selectedParsers.contains(parser)) {
                                        selectedParsers.remove(parser)
                                    } else {
                                        selectedParsers.add(parser)
                                    }
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val size = Modifier.size(40.dp)
                            if (parser.iconUrl == null) {
                                Icon(
                                    imageVector = Icons.Filled.Extension,
                                    contentDescription = null,
                                    modifier = size
                                )
                            } else {
                                AsyncImage(
                                    model = parser.iconUrl,
                                    contentDescription = null,
                                    modifier = size
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = parser.name,
                                )
                                Text(
                                    text = parser.versionName,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            Checkbox(
                                checked = selectedParsers.contains(parser),
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        selectedParsers.add(parser)
                                    } else {
                                        selectedParsers.remove(parser)
                                    }
                                }
                            )
                        }
                        if (index != parsers.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                ElevatedCard {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            modifier = Modifier.size(24.dp),
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.onboarding_parsers_info),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OnboardingScreenPreviewInstallParsers() {
    AppTheme {
        OnboardingInstallParsersContent(
            parsers = listOf(
                AvailableParser(
                    name = "BRI",
                    provider = "Gmail",
                    pkg = "com.finbox.parser.gmail",
                    versionCode = 12,
                    versionName = "1.4.0",
                    libVersion = 2.1,
                    apkUrl = "https://example.com/parsers/gmail-transactions-1.4.0.apk",
                    sha256 = "a3f5c8d91e2b7f4a6c0d8e1f3b5a7c9d2e4f6a8b0c1d3e5f7a9b2c4d6e8f0a1b",
                    iconUrl = null,
                ),
                AvailableParser(
                    name = "Mandiri",
                    provider = "Bank Mandiri",
                    pkg = "com.finbox.parser.mandiri",
                    versionCode = 7,
                    versionName = "1.2.1",
                    libVersion = 2.0,
                    apkUrl = "https://example.com/parsers/mandiri-1.2.1.apk",
                    sha256 = "b7d4e9f2a1c6d8e3f5b0a7c9d2e4f1a6b8c3d5e7f9a0b2c4d6e8f1a3b5c7d9e",
                    iconUrl = null,
                ),
                AvailableParser(
                    name = "BCA",
                    provider = "BCA",
                    pkg = "com.finbox.parser.bca",
                    versionCode = 4,
                    versionName = "1.0.3",
                    libVersion = 1.8,
                    apkUrl = "https://example.com/parsers/bca-1.0.3.apk",
                    sha256 = "c8e1a4f7b2d5e9a3c6f0b8d1e4a7c9f2b5d8e0a3c6f1b4d7e9a2c5f8b0d3e6",
                    iconUrl = null,
                ),
            )
        )
    }
}