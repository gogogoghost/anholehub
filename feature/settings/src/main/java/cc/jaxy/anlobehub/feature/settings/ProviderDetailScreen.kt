package cc.jaxy.anlobehub.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cc.jaxy.anlobehub.core.common.result.AnResult
import cc.jaxy.anlobehub.core.data.provider.CheckResult
import cc.jaxy.anlobehub.core.data.provider.ProviderDetail
import cc.jaxy.anlobehub.core.data.provider.ProviderRepository
import cc.jaxy.anlobehub.core.data.session.ServerStore
import cc.jaxy.anlobehub.core.designsystem.component.AnTextField
import cc.jaxy.anlobehub.core.common.util.isEditableProvider
import cc.jaxy.anlobehub.core.common.util.providerProxyUrl
import cc.jaxy.anlobehub.core.designsystem.component.AnTopBar
import cc.jaxy.anlobehub.core.designsystem.component.ErrorBox
import cc.jaxy.anlobehub.core.designsystem.component.PasswordField
import cc.jaxy.anlobehub.core.designsystem.component.SectionTitle
import cc.jaxy.anlobehub.core.designsystem.component.SettingRow
import cc.jaxy.anlobehub.core.designsystem.component.SkeletonList
import cc.jaxy.anlobehub.core.designsystem.R as DsR
import cc.jaxy.anlobehub.core.designsystem.text.UiText
import cc.jaxy.anlobehub.core.designsystem.text.resolve
import cc.jaxy.anlobehub.core.designsystem.text.toUiText
import cc.jaxy.anlobehub.core.designsystem.theme.spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class ProviderDetailViewModel @Inject constructor(
    private val serverStore: ServerStore,
    private val providerRepository: ProviderRepository,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val detail: ProviderDetail? = null,
        val error: UiText? = null,
        val saving: Boolean = false,
        val checking: Boolean = false,
        val checkResult: CheckResult? = null,
        val checkModelOverride: String? = null,
        val fetching: Boolean = false,
        val remoteModels: List<String>? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _event = MutableStateFlow<UiText?>(null)
    val event: StateFlow<UiText?> = _event.asStateFlow()

    private var providerId: String = ""

    fun bind(id: String) {
        if (providerId == id) return
        providerId = id
        load()
    }

    fun load() {
        viewModelScope.launch {
            val id = providerId
            if (id.isBlank()) return@launch
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            val baseUrl = runCatching { serverStore.baseUrl.first() }.getOrNull()
            if (baseUrl.isNullOrBlank()) {
                _uiState.value = UiState(loading = false, error = UiText.Res(R.string.no_server))
                return@launch
            }
            when (val result = providerRepository.getDetail(baseUrl, id)) {
                is AnResult.Ok -> _uiState.value = UiState(loading = false, detail = result.value)
                is AnResult.Err -> _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = result.error.toUiText(),
                )
            }
        }
    }

    fun consumeEvent() {
        _event.value = null
    }

    fun save(apiKey: String?, baseURL: String?) {
        viewModelScope.launch {
            val id = providerId
            val detail = _uiState.value.detail
            val baseUrl = runCatching { serverStore.baseUrl.first() }.getOrNull()
            if (baseUrl.isNullOrBlank()) {
                _event.value = UiText.Res(R.string.no_server)
                return@launch
            }
            _uiState.value = _uiState.value.copy(saving = true)
            val keyChanged = apiKey != null && apiKey != (detail?.apiKey ?: "")
            val urlChanged = baseURL != null && baseURL != (detail?.baseURL ?: "")
            when (
                val result = providerRepository.updateConfig(
                    baseUrl = baseUrl,
                    id = id,
                    // Blank new key means "leave unchanged"; only send edits.
                    apiKey = apiKey?.takeIf { keyChanged && it.isNotBlank() },
                    baseURL = baseURL?.takeIf { urlChanged },
                )
            ) {
                is AnResult.Ok -> {
                    _uiState.value = _uiState.value.copy(saving = false)
                    _event.value = UiText.Res(R.string.provider_detail_saved)
                    load()
                }
                is AnResult.Err -> {
                    _uiState.value = _uiState.value.copy(saving = false)
                    _event.value = result.error.toUiText()
                }
            }
        }
    }

    fun check() {
        viewModelScope.launch {
            val id = providerId
            val baseUrl = runCatching { serverStore.baseUrl.first() }.getOrNull()
            if (baseUrl.isNullOrBlank()) {
                _event.value = UiText.Res(R.string.no_server)
                return@launch
            }
            _uiState.value = _uiState.value.copy(checking = true, checkResult = null)
            val model = _uiState.value.checkModelOverride
                ?.takeIf { it.isNotBlank() }
                ?: _uiState.value.detail?.checkModel?.takeIf { it.isNotBlank() }
            when (val result = providerRepository.checkConnectivity(baseUrl, id, model)) {
                is AnResult.Ok -> _uiState.value = _uiState.value.copy(
                    checking = false,
                    checkResult = result.value,
                )
                is AnResult.Err -> {
                    _uiState.value = _uiState.value.copy(checking = false)
                    _event.value = result.error.toUiText()
                }
            }
        }
    }

    fun setCheckModel(modelId: String) {
        _uiState.value = _uiState.value.copy(checkModelOverride = modelId, checkResult = null)
    }


    fun fetchRemoteModels(apiKeyInput: String, baseURLInput: String) {
        viewModelScope.launch {
            val detail = _uiState.value.detail
            val baseURL = baseURLInput.ifBlank { detail?.baseURL.orEmpty() }
            if (baseURL.isBlank()) {
                _event.value = UiText.Res(R.string.provider_detail_baseurl_required)
                return@launch
            }
            // Masked/stored key is not reusable for direct calls: require explicit entry.
            if (apiKeyInput.isBlank()) {
                _event.value = UiText.Res(R.string.provider_detail_apikey_required)
                return@launch
            }
            _uiState.value = _uiState.value.copy(fetching = true, remoteModels = null)
            when (val result = providerRepository.fetchRemoteModels(baseURL, apiKeyInput)) {
                is AnResult.Ok -> _uiState.value = _uiState.value.copy(
                    fetching = false,
                    remoteModels = result.value,
                )
                is AnResult.Err -> {
                    _uiState.value = _uiState.value.copy(fetching = false)
                    _event.value = result.error.toUiText()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderDetailScreen(
    providerId: String,
    onBack: () -> Unit,
    onModelsClick: (providerId: String, providerName: String?) -> Unit = { _, _ -> },
    onPickCheckModel: () -> Unit = {},
    viewModel: ProviderDetailViewModel = hiltViewModel(),
) {
    ProviderDetailContent(
        providerId = providerId,
        onBack = onBack,
        onModelsClick = onModelsClick,
        onPickCheckModel = onPickCheckModel,
        viewModel = viewModel,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderDetailContent(
    providerId: String,
    onBack: () -> Unit,
    onModelsClick: (providerId: String, providerName: String?) -> Unit = { _, _ -> },
    onPickCheckModel: () -> Unit = {},
    viewModel: ProviderDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val event by viewModel.event.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val message = event?.resolve()
    LaunchedEffect(providerId) { viewModel.bind(providerId) }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.consumeEvent()
        }
    }
    Scaffold(
        topBar = {
            AnTopBar(
                title = uiState.detail?.name?.takeIf { it.isNotBlank() }
                    ?: uiState.detail?.id ?: providerId,
                onBack = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            uiState.loading && uiState.detail == null ->
                SkeletonList(modifier = Modifier.fillMaxSize().padding(padding))
            uiState.error != null && uiState.detail == null ->
                ErrorBox(
                    text = uiState.error!!.resolve(),
                    onRetry = { viewModel.load() },
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            uiState.detail != null ->
                ProviderDetailBody(
                    detail = uiState.detail!!,
                    saving = uiState.saving,
                    checking = uiState.checking,
                    checkResult = uiState.checkResult,
                    checkModelOverride = uiState.checkModelOverride,
                    fetching = uiState.fetching,
                    remoteModels = uiState.remoteModels,
                    onSave = { key, url -> viewModel.save(key, url) },
                    onCheck = { viewModel.check() },
                    onFetch = { key, url -> viewModel.fetchRemoteModels(key, url) },
                    onPickCheckModel = onPickCheckModel,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
        }
    }
}

@Composable
private fun ProviderDetailBody(
    detail: ProviderDetail,
    saving: Boolean,
    checking: Boolean,
    checkResult: CheckResult?,
    checkModelOverride: String?,
    fetching: Boolean,
    remoteModels: List<String>?,
    onSave: (apiKey: String, baseURL: String) -> Unit,
    onCheck: () -> Unit,
    onFetch: (apiKey: String, baseURL: String) -> Unit,
    onModelsClick: () -> Unit = {},
    onPickCheckModel: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val editable = isEditableProvider(detail.id, detail.source)
    val defaultURL = providerProxyUrl(detail.id)
    var apiKey by rememberSaveable(detail.id) { mutableStateOf(detail.apiKey.orEmpty()) }
    var baseURL by rememberSaveable(detail.id) { mutableStateOf(detail.baseURL.orEmpty()) }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = spacing.s),
    ) {
        item(key = "basic-title") {
            SectionTitle(text = stringResource(R.string.provider_detail_section_basic))
        }
        item(key = "basic-card") {
            Card(modifier = Modifier.padding(horizontal = spacing.l)) {
                Column {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.provider_detail_name)) },
                        supportingContent = {
                            Text(detail.name?.takeIf { it.isNotBlank() } ?: detail.id)
                        },
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.provider_detail_description)) },
                        supportingContent = {
                            Text(
                                detail.description?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.provider_detail_no_description),
                            )
                        },
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.provider_detail_source)) },
                        supportingContent = {
                            Text(
                                detail.source?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.provider_detail_no_source),
                            )
                        },
                    )
                }
            }
        }
        item(key = "models-row") {
            Card(modifier = Modifier.padding(horizontal = spacing.l)) {
                SettingRow(
                    icon = Icons.Filled.SmartToy,
                    title = stringResource(R.string.provider_detail_models),
                    subtitle = stringResource(R.string.provider_detail_models_desc),
                    onClick = onModelsClick,
                )
            }
        }
        if (editable) {
        item(key = "credentials-title") {
            SectionTitle(text = stringResource(R.string.provider_detail_section_credentials))
        }
        item(key = "credentials-card") {
            Card(modifier = Modifier.padding(horizontal = spacing.l)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.l),
                    verticalArrangement = Arrangement.spacedBy(spacing.m),
                ) {
                    PasswordField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = stringResource(R.string.provider_detail_apikey),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AnTextField(
                        value = baseURL,
                        onValueChange = { baseURL = it },
                        label = stringResource(R.string.provider_detail_baseurl),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (baseURL.isNotBlank()) {
                                IconButton(onClick = { baseURL = "" }) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = stringResource(DsR.string.common_clear),
                                    )
                                }
                            }
                        },
                        supportingText = {
                            Text(
                                defaultURL?.let {
                                    stringResource(R.string.provider_detail_baseurl_default, it)
                                } ?: stringResource(R.string.provider_detail_baseurl_optional),
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done,
                        ),
                    )
                    Button(
                        onClick = { onSave(apiKey, baseURL) },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (saving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.provider_detail_save))
                        }
                    }
                }
            }
        }
        item(key = "connectivity-title") {
            SectionTitle(text = stringResource(R.string.provider_detail_section_connectivity))
        }
        item(key = "connectivity-card") {
            Card(modifier = Modifier.padding(horizontal = spacing.l)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.l),
                    verticalArrangement = Arrangement.spacedBy(spacing.m),
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.provider_detail_check_model)) },
                        supportingContent = {
                            Text(
                                checkModelOverride?.takeIf { it.isNotBlank() }
                                    ?: detail.checkModel?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.provider_detail_no_check_model),
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.clickable(onClick = onPickCheckModel),
                    )
                    FilledTonalButton(
                        onClick = onCheck,
                        enabled = !checking,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (checking) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.provider_detail_check))
                        }
                    }
                    if (checking) {
                        Text(
                            stringResource(R.string.provider_detail_checking),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    checkResult?.let { result ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.s),
                        ) {
                            if (result.ok) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(stringResource(R.string.provider_detail_check_ok))
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    result.error?.takeIf { it.isNotBlank() }
                                        ?: stringResource(R.string.provider_detail_check_failed),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
        item(key = "remote-title") {
            SectionTitle(text = stringResource(R.string.provider_detail_section_remote_models))
        }
        item(key = "remote-card") {
            Card(modifier = Modifier.padding(horizontal = spacing.l)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.l),
                    verticalArrangement = Arrangement.spacedBy(spacing.m),
                ) {
                    FilledTonalButton(
                        onClick = { onFetch(apiKey, baseURL) },
                        enabled = !fetching,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (fetching) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.provider_detail_fetch))
                        }
                    }
                    if (fetching) {
                        Text(
                            stringResource(R.string.provider_detail_fetching),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    remoteModels?.let { models ->
                        Text(
                            stringResource(R.string.provider_detail_remote_count, models.size),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        models.take(REMOTE_MODEL_PREVIEW_LIMIT).forEach { id ->
                            Text(
                                text = id,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.provider_detail_remote_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        }
    }
}

private const val REMOTE_MODEL_PREVIEW_LIMIT = 50
