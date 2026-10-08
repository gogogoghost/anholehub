package cc.jaxy.anlobehub.feature.agents

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.jaxy.anlobehub.core.designsystem.R as DsR
import cc.jaxy.anlobehub.core.designsystem.component.AnTextField
import cc.jaxy.anlobehub.core.designsystem.component.ErrorBox
import cc.jaxy.anlobehub.core.designsystem.component.SkeletonList
import cc.jaxy.anlobehub.core.designsystem.text.resolve
import cc.jaxy.anlobehub.core.designsystem.theme.AnlobehubTheme
import cc.jaxy.anlobehub.core.designsystem.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentManageSheet(
    agentId: String,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: AgentManageViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(agentId) { viewModel.ensureLoaded(agentId) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) onDeleted()
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        AgentManageBody(
            title = uiState.title,
            systemRole = uiState.systemRole,
            model = uiState.model,
            provider = uiState.provider,
            isInbox = uiState.isInbox,
            loading = uiState.loading,
            saving = uiState.saving,
            duplicating = uiState.duplicating,
            deleting = uiState.deleting,
            saved = uiState.saved,
            errorText = uiState.error?.resolve(),
            onTitleChange = viewModel::onTitleChange,
            onSystemRoleChange = viewModel::onSystemRoleChange,
            onModelChange = viewModel::onModelChange,
            onSave = viewModel::save,
            onDuplicate = viewModel::duplicate,
            onDeleteClick = { confirmDelete = true },
            onRetry = viewModel::load,
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.agents_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.agents_delete_confirm_format,
                        uiState.title.ifBlank { stringResource(R.string.agents_untitled) },
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete()
                    },
                ) {
                    Text(stringResource(DsR.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(DsR.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun AgentManageBody(
    title: String,
    systemRole: String,
    model: String,
    provider: String,
    isInbox: Boolean,
    loading: Boolean,
    saving: Boolean,
    duplicating: Boolean,
    deleting: Boolean,
    saved: Boolean,
    errorText: String?,
    onTitleChange: (String) -> Unit,
    onSystemRoleChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onSave: () -> Unit,
    onDuplicate: () -> Unit,
    onDeleteClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = spacing.l)
            .padding(bottom = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        Text(
            stringResource(R.string.agents_manage_title),
            style = MaterialTheme.typography.titleLarge,
        )
        when {
            loading -> SkeletonList(itemCount = 3)
            errorText != null && title.isBlank() && systemRole.isBlank() ->
                ErrorBox(text = errorText, onRetry = onRetry)
            else -> {
                if (errorText != null) {
                    ErrorBox(text = errorText, onRetry = onRetry)
                }
                AnTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = stringResource(R.string.agents_manage_title_label),
                    modifier = Modifier.fillMaxWidth(),
                )
                AnTextField(
                    value = systemRole,
                    onValueChange = onSystemRoleChange,
                    label = stringResource(R.string.agents_manage_system_role_label),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                )
                AnTextField(
                    value = model,
                    onValueChange = onModelChange,
                    label = stringResource(R.string.agents_manage_model_label),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = provider,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.agents_manage_provider_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (saved) {
                    Text(
                        stringResource(R.string.agents_saved),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.s),
                ) {
                    Button(
                        onClick = onSave,
                        enabled = !saving && title.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(
                                if (saving) {
                                    stringResource(R.string.agents_manage_saving)
                                } else {
                                    stringResource(R.string.agents_manage_save)
                                },
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onDuplicate,
                        enabled = !duplicating,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            if (duplicating) {
                                stringResource(R.string.agents_duplicating)
                            } else {
                                stringResource(R.string.agents_duplicate)
                            },
                        )
                    }
                    if (!isInbox) {
                        IconButton(
                            onClick = onDeleteClick,
                            enabled = !deleting,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(DsR.string.common_delete),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Manage-Light", showBackground = true)
@Composable
private fun AgentManageBodyLightPreview() {
    AnlobehubTheme(darkTheme = false) {
        AgentManageBody(
            title = "Translator",
            systemRole = "Translate the user input.",
            model = "gpt-4o",
            provider = "openai",
            isInbox = false,
            loading = false,
            saving = false,
            duplicating = false,
            deleting = false,
            saved = true,
            errorText = null,
            onTitleChange = {},
            onSystemRoleChange = {},
            onModelChange = {},
            onSave = {},
            onDuplicate = {},
            onDeleteClick = {},
            onRetry = {},
        )
    }
}

@Preview(
    name = "Manage-Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AgentManageBodyDarkPreview() {
    AnlobehubTheme(darkTheme = true) {
        AgentManageBody(
            title = "",
            systemRole = "",
            model = "",
            provider = "",
            isInbox = true,
            loading = false,
            saving = true,
            duplicating = false,
            deleting = false,
            saved = false,
            errorText = null,
            onTitleChange = {},
            onSystemRoleChange = {},
            onModelChange = {},
            onSave = {},
            onDuplicate = {},
            onDeleteClick = {},
            onRetry = {},
        )
    }
}
