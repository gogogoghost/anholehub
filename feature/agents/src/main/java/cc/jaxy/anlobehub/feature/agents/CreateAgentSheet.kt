package cc.jaxy.anlobehub.feature.agents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import cc.jaxy.anlobehub.core.data.chat.AIModel
import cc.jaxy.anlobehub.core.designsystem.component.AnTextField
import cc.jaxy.anlobehub.core.designsystem.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAgentSheet(
    creating: Boolean,
    selectedModel: AIModel?,
    onDismiss: () -> Unit,
    onConfirm: (title: String, systemRole: String?, model: String?, provider: String?) -> Unit,
    onOpenModelPicker: () -> Unit,
    onModelClear: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf("") }
    var systemRole by remember { mutableStateOf("") }
    var titleError by remember { mutableStateOf(false) }
    val spacing = MaterialTheme.spacing
    ModalBottomSheet(onDismissRequest = { if (!creating) onDismiss() }, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.l)
                .padding(bottom = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.m),
        ) {
            Text(
                stringResource(R.string.agents_create_title),
                style = MaterialTheme.typography.titleLarge,
            )
            AnTextField(
                value = title,
                onValueChange = {
                    title = it
                    titleError = false
                },
                label = stringResource(R.string.agents_create_title_label),
                modifier = Modifier.fillMaxWidth(),
                isError = titleError,
                supportingText = if (titleError) {
                    { Text(stringResource(R.string.agents_create_title_required)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            AnTextField(
                value = systemRole,
                onValueChange = { systemRole = it },
                label = stringResource(R.string.agents_create_system_role_label),
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.agents_create_model_label)) },
                supportingContent = {
                    Text(
                        selectedModel?.displayName?.takeIf { it.isNotBlank() }
                            ?: selectedModel?.id?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.agents_create_model_auto),
                    )
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selectedModel != null) {
                            IconButton(onClick = onModelClear) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(cc.jaxy.anlobehub.core.designsystem.R.string.common_clear),
                                )
                            }
                        }
                        Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null)
                    }
                },
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenModelPicker),
            )
            Button(
                onClick = {
                    if (title.isBlank()) {
                        titleError = true
                        return@Button
                    }
                    onConfirm(
                        title.trim(),
                        systemRole.trim().takeIf { it.isNotBlank() },
                        selectedModel?.id,
                        selectedModel?.providerId,
                    )
                },
                enabled = !creating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (creating) {
                        stringResource(R.string.agents_creating)
                    } else {
                        stringResource(R.string.agents_create)
                    },
                )
            }
        }
    }
}

