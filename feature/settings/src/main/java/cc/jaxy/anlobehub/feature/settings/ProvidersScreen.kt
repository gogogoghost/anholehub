package cc.jaxy.anlobehub.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cc.jaxy.anlobehub.core.common.result.AnResult
import cc.jaxy.anlobehub.core.data.provider.AiProvider
import cc.jaxy.anlobehub.core.data.provider.ProviderRepository
import cc.jaxy.anlobehub.core.data.session.ServerStore
import cc.jaxy.anlobehub.core.designsystem.component.AnTopBar
import cc.jaxy.anlobehub.core.designsystem.component.EmptyBox
import cc.jaxy.anlobehub.core.designsystem.component.ErrorBox
import cc.jaxy.anlobehub.core.designsystem.component.InitialAvatar
import cc.jaxy.anlobehub.core.designsystem.component.RefreshBox
import cc.jaxy.anlobehub.core.designsystem.component.SkeletonList
import cc.jaxy.anlobehub.core.designsystem.text.UiText
import cc.jaxy.anlobehub.core.designsystem.text.resolve
import cc.jaxy.anlobehub.core.designsystem.text.toUiText
import cc.jaxy.anlobehub.core.designsystem.theme.AnlobehubTheme
import cc.jaxy.anlobehub.core.designsystem.theme.spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
internal class ProvidersViewModel @Inject constructor(
    private val serverStore: ServerStore,
    private val providerRepository: ProviderRepository,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val providers: List<AiProvider> = emptyList(),
        val error: UiText? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _errorEvent = MutableStateFlow<UiText?>(null)
    val errorEvent: StateFlow<UiText?> = _errorEvent.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            val baseUrl = runCatching { serverStore.baseUrl.first() }.getOrNull()
            if (baseUrl.isNullOrBlank()) {
                _uiState.value = UiState(loading = false, error = UiText.Res(R.string.no_server))
                return@launch
            }
            when (val result = providerRepository.listProviders(baseUrl)) {
                is AnResult.Ok -> _uiState.value = UiState(loading = false, providers = result.value)
                is AnResult.Err -> _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = result.error.toUiText(),
                )
            }
        }
    }

    fun consumeToggleError() {
        _errorEvent.value = null
    }

    fun toggle(provider: AiProvider, enabled: Boolean) {
        viewModelScope.launch {
            val current = _uiState.value.providers
            _uiState.value = _uiState.value.copy(
                providers = current.map {
                    if (it.id == provider.id) it.copy(enabled = enabled) else it
                },
            )
            val baseUrl = runCatching { serverStore.baseUrl.first() }.getOrNull()
            if (baseUrl.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(providers = current)
                _errorEvent.value = UiText.Res(R.string.no_server)
                return@launch
            }
            when (val result = providerRepository.toggleProvider(baseUrl, provider.id, enabled)) {
                is AnResult.Ok -> Unit
                is AnResult.Err -> {
                    _uiState.value = _uiState.value.copy(providers = current)
                    _errorEvent.value = result.error.toUiText()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(
    onBack: () -> Unit,
    onProviderClick: (id: String, name: String) -> Unit,
) {
    ProvidersContent(onBack = onBack, onProviderClick = onProviderClick)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProvidersContent(
    onBack: () -> Unit,
    onProviderClick: (id: String, name: String) -> Unit,
    viewModel: ProvidersViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val errorEvent by viewModel.errorEvent.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val toggleError = errorEvent?.resolve()
    LaunchedEffect(toggleError) {
        if (toggleError != null) {
            snackbar.showSnackbar(toggleError)
            viewModel.consumeToggleError()
        }
    }
    Scaffold(
        topBar = { AnTopBar(title = stringResource(R.string.providers_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            uiState.loading && uiState.providers.isEmpty() ->
                SkeletonList(modifier = Modifier.fillMaxSize().padding(padding))
            uiState.error != null && uiState.providers.isEmpty() ->
                ErrorBox(
                    text = uiState.error!!.resolve(),
                    onRetry = { viewModel.load() },
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            else ->
                ProvidersBody(
                    providers = uiState.providers,
                    refreshing = uiState.loading,
                    onRefresh = { viewModel.load() },
                    onProviderClick = onProviderClick,
                    onToggle = { provider, enabled -> viewModel.toggle(provider, enabled) },
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
        }
    }
}

@Composable
private fun ProvidersBody(
    providers: List<AiProvider>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onProviderClick: (id: String, name: String) -> Unit,
    onToggle: (AiProvider, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    RefreshBox(
        refreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        if (providers.isEmpty()) {
            EmptyBox(
                icon = Icons.Filled.Cloud,
                title = stringResource(R.string.providers_empty_title),
                description = stringResource(R.string.discovery_empty_desc),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(spacing.l),
            ) {
                items(providers, key = { it.id }) { provider ->
                    ProviderRow(
                        provider = provider,
                        onClick = {
                            onProviderClick(provider.id, provider.name ?: provider.id)
                        },
                        onToggle = { onToggle(provider, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderRow(
    provider: AiProvider,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(bottom = spacing.s),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = provider.name?.takeIf { it.isNotBlank() } ?: provider.id,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Text(
                    text = provider.id,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingContent = {
                InitialAvatar(name = provider.name?.takeIf { it.isNotBlank() } ?: provider.id)
            },
            trailingContent = {
                Switch(
                    checked = provider.enabled,
                    onCheckedChange = onToggle,
                )
            },
        )
    }
}

@Preview(name = "Providers", showBackground = true)
@Composable
private fun ProvidersBodyPreview() {
    AnlobehubTheme(darkTheme = false) {
        ProvidersBody(
            providers = listOf(
                AiProvider(id = "openai", name = "OpenAI", enabled = true),
                AiProvider(id = "anthropic", name = "Anthropic", enabled = false),
            ),
            refreshing = false,
            onRefresh = {},
            onProviderClick = { _, _ -> },
            onToggle = { _, _ -> },
        )
    }
}
