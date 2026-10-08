package cc.jaxy.anlobehub.feature.auth

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.jaxy.anlobehub.core.common.preferences.AppLanguage
import cc.jaxy.anlobehub.core.common.preferences.AppTheme
import cc.jaxy.anlobehub.core.designsystem.R as DsR
import cc.jaxy.anlobehub.core.designsystem.component.AnTextField
import cc.jaxy.anlobehub.core.designsystem.component.AnTopBar
import cc.jaxy.anlobehub.core.designsystem.component.AppearanceCard
import cc.jaxy.anlobehub.core.designsystem.component.languageLabel
import cc.jaxy.anlobehub.core.designsystem.component.themeLabel
import cc.jaxy.anlobehub.core.designsystem.component.SectionTitle
import cc.jaxy.anlobehub.core.designsystem.component.SettingRow
import cc.jaxy.anlobehub.core.designsystem.text.UiText
import cc.jaxy.anlobehub.core.designsystem.text.resolve
import cc.jaxy.anlobehub.core.designsystem.theme.AnlobehubTheme
import cc.jaxy.anlobehub.core.designsystem.theme.hapticClick
import cc.jaxy.anlobehub.core.designsystem.theme.spacing

/** 官方云端地址，免输入直连。 */
const val OFFICIAL_BASE_URL = "https://app.lobehub.com"
@Composable
fun ServerScreen(
    onServerConfirmed: (String) -> Unit,
    viewModel: ServerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    ServerContent(
        link = uiState.link,
        probing = uiState.probing,
        error = uiState.error,
        theme = theme,
        language = language,
        onLinkChange = viewModel::onLinkChange,
        onConfirm = { viewModel.confirm(uiState.link, onServerConfirmed) },
        onUseOfficial = { viewModel.confirm(OFFICIAL_BASE_URL, onServerConfirmed) },
        onThemeChange = viewModel::setTheme,
        onLanguageChange = viewModel::setLanguage,
    )
}

@Composable
private fun ServerContent(
    link: String,
    probing: Boolean,
    error: UiText?,
    theme: AppTheme,
    language: AppLanguage,
    onLinkChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onUseOfficial: () -> Unit,
    onThemeChange: (AppTheme) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
) {
    val view = LocalView.current
    val spacing = MaterialTheme.spacing
    val canConfirm = link.isNotBlank() && !probing
    var showAppearance by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(error) {
        if (error != null) hapticClick(view)
    }
    Scaffold(
        topBar = {
            AnTopBar(
                title = "",
                onBack = null,
                actions = {
                    IconButton(onClick = { showAppearance = true }) {
                        Icon(
                            imageVector = Icons.Filled.Translate,
                            contentDescription = languageLabel(language),
                        )
                    }
                    IconButton(onClick = { showAppearance = true }) {
                        Icon(
                            imageVector = Icons.Filled.Palette,
                            contentDescription = themeLabel(theme),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (showAppearance) {
            AlertDialog(
                onDismissRequest = { showAppearance = false },
                title = { Text(stringResource(R.string.auth_appearance_title)) },
                text = {
                    AppearanceCard(
                        theme = theme,
                        language = language,
                        onThemeChange = onThemeChange,
                        onLanguageChange = onLanguageChange,
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showAppearance = false }) {
                        Text(stringResource(DsR.string.common_confirm))
                    }
                },
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(spacing.xxl),
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(
                    modifier = Modifier.size(spacing.xxxl + spacing.xxl),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Hub,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(spacing.xxxl),
                    )
                }
            }
            Spacer(Modifier.height(spacing.l))
            Text(
                text = "Anlobehub",
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = stringResource(R.string.auth_server_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(spacing.xl))
            SectionTitle(text = stringResource(R.string.auth_server_section_official))
            Spacer(Modifier.height(spacing.xs))
            Card(modifier = Modifier.fillMaxWidth()) {
                SettingRow(
                    icon = Icons.Filled.Cloud,
                    title = stringResource(R.string.auth_server_official_title),
                    subtitle = OFFICIAL_BASE_URL,
                    onClick = {
                        if (!probing) {
                            hapticClick(view)
                            onUseOfficial()
                        }
                    },
                )
            }
            Spacer(Modifier.height(spacing.l))
            SectionTitle(text = stringResource(R.string.auth_server_section_selfhost))
            Spacer(Modifier.height(spacing.xs))
            AnTextField(
                value = link,
                onValueChange = onLinkChange,
                label = stringResource(R.string.auth_server_address_label),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (canConfirm) onConfirm() },
                ),
                isError = error != null,
                supportingText = {
                    Text(error?.resolve() ?: stringResource(R.string.auth_server_address_hint))
                },
            )
            Spacer(Modifier.height(spacing.m))
            Button(
                onClick = {
                    hapticClick(view)
                    onConfirm()
                },
                enabled = canConfirm,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (probing) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = spacing.s).size(spacing.l),
                        strokeWidth = spacing.xs2,
                    )
                }
                Text(if (probing) stringResource(R.string.auth_server_probing) else stringResource(R.string.auth_server_continue))
            }
            Spacer(Modifier.height(spacing.m))
            Text(
                text = stringResource(R.string.auth_server_example),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(name = "服务器-浅色", showBackground = true)
@Composable
private fun ServerContentLightPreview() {
    AnlobehubTheme(darkTheme = false) {
        ServerContent(
            link = "lobehub.example.com",
            probing = false,
            error = null,
            theme = AppTheme.SYSTEM,
            language = AppLanguage.SYSTEM,
            onLinkChange = {},
            onConfirm = {},
            onUseOfficial = {},
            onThemeChange = {},
            onLanguageChange = {},
        )
    }
}

@Preview(
    name = "服务器-深色",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ServerContentDarkPreview() {
    AnlobehubTheme(darkTheme = true) {
        ServerContent(
            link = "ftp://bad-url",
            probing = false,
            error = UiText.Raw("输入有误，请检查服务器地址或账号信息"),
            theme = AppTheme.SYSTEM,
            language = AppLanguage.SYSTEM,
            onLinkChange = {},
            onConfirm = {},
            onUseOfficial = {},
            onThemeChange = {},
            onLanguageChange = {},
        )
    }
}
