package cc.jaxy.anlobehub.feature.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cc.jaxy.anlobehub.core.common.result.AnResult
import cc.jaxy.anlobehub.core.data.discovery.DiscoveryRepository
import cc.jaxy.anlobehub.core.data.discovery.KnowledgeBase
import cc.jaxy.anlobehub.core.data.session.ServerStore
import cc.jaxy.anlobehub.core.designsystem.component.AnTopBar
import cc.jaxy.anlobehub.core.designsystem.component.EmptyBox
import cc.jaxy.anlobehub.core.designsystem.component.ErrorBox
import cc.jaxy.anlobehub.core.designsystem.component.RefreshBox
import cc.jaxy.anlobehub.core.designsystem.component.SkeletonList
import cc.jaxy.anlobehub.core.designsystem.theme.spacing
import cc.jaxy.anlobehub.core.designsystem.text.UiText
import cc.jaxy.anlobehub.core.designsystem.text.resolve
import cc.jaxy.anlobehub.core.designsystem.text.toUiText
import cc.jaxy.anlobehub.core.designsystem.theme.AnlobehubTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
internal class KnowledgeBasesViewModel @Inject constructor(
    private val serverStore: ServerStore,
    private val discoveryRepository: DiscoveryRepository,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val items: List<KnowledgeBase> = emptyList(),
        val error: UiText? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState(loading = true)
            val baseUrl = runCatching { serverStore.baseUrl.first() }.getOrNull()
            if (baseUrl.isNullOrBlank()) {
                _uiState.value = UiState(loading = false, error = UiText.Res(R.string.no_server))
                return@launch
            }
            when (val result = discoveryRepository.listKnowledgeBases(baseUrl)) {
                is AnResult.Ok -> _uiState.value = UiState(loading = false, items = result.value)
                is AnResult.Err -> _uiState.value =
                    UiState(loading = false, error = result.error.toUiText())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeBasesScreen(onBack: () -> Unit) {
    KnowledgeBasesContent(onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KnowledgeBasesContent(
    onBack: () -> Unit,
    viewModel: KnowledgeBasesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { AnTopBar(title = stringResource(R.string.kb_title), onBack = onBack) },
    ) { padding ->
        when {
            uiState.loading && uiState.items.isEmpty() ->
                SkeletonList(modifier = Modifier.fillMaxSize().padding(padding))
            uiState.error != null && uiState.items.isEmpty() ->
                ErrorBox(
                    text = uiState.error!!.resolve(),
                    onRetry = { viewModel.load() },
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            else ->
                KnowledgeBaseBody(
                    items = uiState.items,
                    refreshing = uiState.loading,
                    onRefresh = { viewModel.load() },
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
        }
    }
}

@Composable
private fun KnowledgeBaseBody(
    items: List<KnowledgeBase>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val context = LocalContext.current
    val wipText = stringResource(R.string.kb_wip)
    RefreshBox(
        refreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        if (items.isEmpty()) {
            EmptyBox(
                icon = Icons.Filled.Folder,
                title = stringResource(R.string.kb_empty_title),
                description = stringResource(R.string.discovery_empty_desc),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(spacing.l),
            ) {
                items(items, key = { it.id }) { kb ->
                    KnowledgeBaseRow(
                        kb = kb,
                        onClick = {
                            Toast.makeText(context, wipText, Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun KnowledgeBaseRow(
    kb: KnowledgeBase,
    onClick: () -> Unit,
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
                    text = kb.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.kb_untitled),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = if (!kb.description.isNullOrBlank()) {
                {
                    Text(
                        text = kb.description!!,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                null
            },
            leadingContent = {
                Icon(imageVector = Icons.Filled.Folder, contentDescription = null)
            },
        )
    }
}

@Preview(name = "知识库-浅色", showBackground = true)
@Composable
private fun KnowledgeBaseBodyLightPreview() {
    AnlobehubTheme(darkTheme = false) {
        Column {
            KnowledgeBaseBody(
                items = listOf(
                    KnowledgeBase(id = "1", title = "产品手册", description = "产品使用说明与常见问题"),
                    KnowledgeBase(id = "2", title = "研发知识库", description = "技术文档沉淀"),
                ),
                refreshing = false,
                onRefresh = {},
            )
        }
    }
}

@Preview(name = "知识库-深色", showBackground = true)
@Composable
private fun KnowledgeBaseBodyDarkPreview() {
    AnlobehubTheme(darkTheme = true) {
        Column {
            KnowledgeBaseBody(
                items = emptyList(),
                refreshing = false,
                onRefresh = {},
            )
        }
    }
}
