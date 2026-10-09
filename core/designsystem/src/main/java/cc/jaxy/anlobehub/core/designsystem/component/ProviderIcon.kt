package cc.jaxy.anlobehub.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Provider brand icon (mirrors web `ProviderIcon` avatar style).
 *
 * Static 144px WebP artwork (light/dark via resource qualifiers). Falls
 * back to [InitialAvatar] when upstream has no artwork for [providerId] or
 * a custom [logoUrl] should win.
 */
@Composable
fun ProviderIcon(
    providerId: String,
    name: String?,
    logoUrl: String? = null,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    val drawable = providerBrandIcon(providerId)
    if (!logoUrl.isNullOrBlank() || drawable == null) {
        InitialAvatar(
            name = name,
            avatarUrl = logoUrl?.takeIf { it.isNotBlank() },
            size = size,
            modifier = modifier,
        )
        return
    }
    Image(
        painter = painterResource(drawable),
        contentDescription = null,
        modifier = modifier
            .size(size)
            .clip(CircleShape),
    )
}
