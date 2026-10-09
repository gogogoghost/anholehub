package cc.jaxy.anlobehub.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Model brand icon (mirrors web `ModelIcon`).
 *
 * Resolution order: model-id regex table -> provider brand icon ->
 * [InitialAvatar]. [label] feeds the initial fallback.
 */
@Composable
fun ModelIcon(
    modelId: String,
    providerId: String? = null,
    label: String? = null,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    val drawable = remember(modelId) { modelBrandIcon(modelId) }
    if (drawable != null) {
        Image(
            painter = painterResource(drawable),
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clip(CircleShape),
        )
        return
    }
    if (!providerId.isNullOrBlank() && providerBrandIcon(providerId) != null) {
        ProviderIcon(
            providerId = providerId,
            name = label ?: modelId,
            size = size,
            modifier = modifier,
        )
        return
    }
    InitialAvatar(name = label ?: modelId, size = size, modifier = modifier)
}
