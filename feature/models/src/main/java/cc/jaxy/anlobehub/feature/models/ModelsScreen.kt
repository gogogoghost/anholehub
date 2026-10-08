package cc.jaxy.anlobehub.feature.models

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.jaxy.anlobehub.core.data.chat.AIModel
import cc.jaxy.anlobehub.core.designsystem.R as DsR
import cc.jaxy.anlobehub.core.designsystem.component.AnTopBar
import cc.jaxy.anlobehub.core.designsystem.component.EmptyBox
import cc.jaxy.anlobehub.core.designsystem.component.ErrorBox
import cc.jaxy.anlobehub.core.designsystem.component.RefreshBox
import cc.jaxy.anlobehub.core.designsystem.component.SkeletonList
import cc.jaxy.anlobehub.core.designsystem.text.UiText
import cc.jaxy.anlobehub.core.designsystem.text.resolve
import cc.jaxy.anlobehub.core.designsystem.theme.AnlobehubTheme
import cc.jaxy.anlobehub.core.designsystem.theme.spacing

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    selectedModelId: String? = null,
    selectedProviderId: String? = null,
    onPick: (AIModel) -> Unit,
    onBack: () -> Unit,
    viewModel: ModelsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    return ModelsContent(
        models = uiState.models,
        loading = uiState.loading,
        error = uiState.error,
        selectedModelId = selectedModelId ?: viewModel.selectedModelId,
        selectedProviderId = selectedProviderId ?: viewModel.selectedProviderId,
        onRefresh = viewModel::refresh,
        onPick = onPick,
        onBack = onBack,
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ModelsContent(
    models: List<AIModel>,
    loading: Boolean,
    error: UiText?,
    selectedModelId: String?,
    selectedProviderId: String?,
    onRefresh: () -> Unit,
    onPick: (AIModel) -> Unit,
    onBack: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(models, query) {
        if (query.isBlank()) {
            models
        } else {
            models.filter {
                (it.displayName ?: "").contains(query, ignoreCase = true) ||
                    it.id.contains(query, ignoreCase = true) ||
                    (it.providerId ?: "").contains(query, ignoreCase = true) ||
                    (it.providerName ?: "").contains(query, ignoreCase = true)
            }
        }
    }
    val grouped = remember(filtered) {
        filtered.groupBy { it.providerName?.takeIf { name -> name.isNotBlank() } ?: it.providerId?.takeIf { id -> id.isNotBlank() } ?: "Unknown" }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .mapValues { (_, list) -> list.sortedBy { it.displayName?.lowercase().orEmpty().ifBlank { it.id.lowercase() } } }
    }
    Scaffold(
        topBar = {
            AnTopBar(
                title = stringResource(R.string.models_title),
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.l, vertical = spacing.s),
                placeholder = { Text(stringResource(R.string.models_search_hint)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(DsR.string.common_clear),
                            )
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
            )
            RefreshBox(
                refreshing = loading,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                when {
                    loading && models.isEmpty() -> SkeletonList()
                    error != null && models.isEmpty() -> ErrorBox(text = error.resolve(), onRetry = onRefresh)
                    models.isEmpty() -> EmptyBox(
                        icon = Icons.Outlined.SmartToy,
                        title = stringResource(R.string.models_empty_title),
                        description = stringResource(R.string.models_empty_desc),
                    )
                    filtered.isEmpty() -> EmptyBox(
                        icon = Icons.Filled.Search,
                        title = stringResource(R.string.models_no_match_title),
                        description = stringResource(R.string.models_no_match_desc),
                    )
                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        grouped.forEach { (provider, list) ->
                            stickyHeader(key = "header-$provider") {
                                Text(
                                    text = provider,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = spacing.l, vertical = spacing.s),
                                )
                            }
                            items(list, key = { (it.providerId.orEmpty()) + "\n" + it.id }) { model ->
                                ModelRow(
                                    model = model,
                                    selected = selectedModelId != null &&
                                        model.id == selectedModelId &&
                                        (selectedProviderId == null || model.providerId == selectedProviderId),
                                    onPick = onPick,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelRow(
    model: AIModel,
    selected: Boolean,
    onPick: (AIModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = {
            Text(model.displayName?.takeIf { it.isNotBlank() } ?: model.id)
        },
        supportingContent = {
            Text(model.id)
        },
        leadingContent = {
            RadioButton(selected = selected, onClick = { onPick(model) })
        },
        trailingContent = {
            model.providerName?.takeIf { it.isNotBlank() }?.let {
                AssistChip(onClick = { onPick(model) }, label = { Text(it) })
            }
        },
        modifier = modifier.clickable { onPick(model) },
    )
}

private val previewModels = listOf(
    AIModel(id = "gpt-4o", displayName = "GPT-4o", providerId = "openai", providerName = "OpenAI"),
    AIModel(id = "gpt-4o-mini", displayName = "GPT-4o mini", providerId = "openai", providerName = "OpenAI"),
    AIModel(id = "claude-sonnet", displayName = "GPT-4o", providerId = "anthropic", providerName = "Anthropic"),
    AIModel(id = "deepseek-chat", displayName = "DeepSeek Chat", providerId = "deepseek", providerName = "DeepSeek"),
)

@Preview(name = "Models-Light", showBackground = true)
@Composable
private fun ModelsLightPreview() {
    AnlobehubTheme(darkTheme = false) {
        ModelsContent(
            models = previewModels,
            loading = false,
            error = null,
            selectedModelId = "gpt-4o",
            selectedProviderId = "openai",
            onRefresh = {},
            onPick = {},
            onBack = {},
        )
    }
}

@Preview(
    name = "Models-Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ModelsDarkPreview() {
    AnlobehubTheme(darkTheme = true) {
        ModelsContent(
            models = previewModels,
            loading = false,
            error = null,
            selectedModelId = "claude-sonnet",
            selectedProviderId = "anthropic",
            onRefresh = {},
            onPick = {},
            onBack = {},
        )
    }
}
