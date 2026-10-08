package cc.jaxy.anlobehub.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 浅色：靛蓝主色（#4F46E5 系），surfaceContainer 层级拉开卡片/列表对比
private val LightColors =
    lightColorScheme(
        primary = Color(0xFF4F46E5),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE0E7FF),
        onPrimaryContainer = Color(0xFF1E1B4B),
        secondary = Color(0xFF6366F1),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE0E7FF),
        onSecondaryContainer = Color(0xFF1E1B4B),
        tertiary = Color(0xFF0EA5E9),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFE0F2FE),
        onTertiaryContainer = Color(0xFF0C4A6E),
        error = Color(0xFFDC2626),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFEE2E2),
        onErrorContainer = Color(0xFF7F1D1D),
        background = Color(0xFFF8FAFC),
        onBackground = Color(0xFF0F172A),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF0F172A),
        surfaceVariant = Color(0xFFE2E8F0),
        onSurfaceVariant = Color(0xFF475569),
        surfaceTint = Color(0xFF4F46E5),
        inverseSurface = Color(0xFF0F172A),
        inverseOnSurface = Color(0xFFF1F5F9),
        inversePrimary = Color(0xFFA5B4FC),
        surfaceDim = Color(0xFFE2E8F0),
        surfaceBright = Color(0xFFFFFFFF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF8FAFC),
        surfaceContainer = Color(0xFFF1F5F9),
        surfaceContainerHigh = Color(0xFFE2E8F0),
        surfaceContainerHighest = Color(0xFFCBD5E1),
        outline = Color(0xFF94A3B8),
        outlineVariant = Color(0xFFE2E8F0),
        scrim = Color(0xFF020617),
    )

// 深色：深 slate 底 + 亮靛蓝主色，保证夜间对比度
private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF818CF8),
        onPrimary = Color(0xFF1E1B4B),
        primaryContainer = Color(0xFF3730A3),
        onPrimaryContainer = Color(0xFFE0E7FF),
        secondary = Color(0xFFA5B4FC),
        onSecondary = Color(0xFF1E1B4B),
        secondaryContainer = Color(0xFF3730A3),
        onSecondaryContainer = Color(0xFFE0E7FF),
        tertiary = Color(0xFF38BDF8),
        onTertiary = Color(0xFF082F49),
        tertiaryContainer = Color(0xFF0C4A6E),
        onTertiaryContainer = Color(0xFFE0F2FE),
        error = Color(0xFFF87171),
        onError = Color(0xFF7F1D1D),
        errorContainer = Color(0xFF7F1D1D),
        onErrorContainer = Color(0xFFFEE2E2),
        background = Color(0xFF0F172A),
        onBackground = Color(0xFFF1F5F9),
        surface = Color(0xFF0F172A),
        onSurface = Color(0xFFF1F5F9),
        surfaceVariant = Color(0xFF1E293B),
        onSurfaceVariant = Color(0xFF94A3B8),
        surfaceTint = Color(0xFF818CF8),
        inverseSurface = Color(0xFFF1F5F9),
        inverseOnSurface = Color(0xFF0F172A),
        inversePrimary = Color(0xFF4F46E5),
        surfaceDim = Color(0xFF020617),
        surfaceBright = Color(0xFF1E293B),
        surfaceContainerLowest = Color(0xFF020617),
        surfaceContainerLow = Color(0xFF0F172A),
        surfaceContainer = Color(0xFF1E293B),
        surfaceContainerHigh = Color(0xFF273449),
        surfaceContainerHighest = Color(0xFF334155),
        outline = Color(0xFF475569),
        outlineVariant = Color(0xFF1E293B),
        scrim = Color(0xFF020617),
    )

// 自定义排版：title/display 沿用 M3 默认缩放，只收紧/放宽 body 与 label 行高
private val DefaultTypography = Typography()
private val AnTypography =
    Typography(
        displayLarge = DefaultTypography.displayLarge,
        displayMedium = DefaultTypography.displayMedium,
        displaySmall = DefaultTypography.displaySmall,
        headlineLarge = DefaultTypography.headlineLarge,
        headlineMedium = DefaultTypography.headlineMedium,
        headlineSmall = DefaultTypography.headlineSmall,
        titleLarge = DefaultTypography.titleLarge,
        titleMedium = DefaultTypography.titleMedium,
        titleSmall = DefaultTypography.titleSmall,
        // 正文行高放大，保证中文长文本可读性
        bodyLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 26.sp,
            letterSpacing = 0.5.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.25.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.4.sp,
        ),
        // 标签行高收紧，适配按钮/徽标等小字场景
        labelLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
        ),
    )

/**
 * 应用级主题入口。
 * @param darkTheme 是否深色，默认跟随系统
 * @param dynamicColor 是否在 Android 12+ 启用动态取色，默认开启
 */
@Composable
fun AnlobehubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    // Android 12+ 动态取色优先，低版本/关闭时回落到 Lobe 自定义配色
    val colorScheme =
        if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val context = LocalContext.current
            if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        } else {
            if (darkTheme) DarkColors else LightColors
        }
    // 间距 token 随主题下发，各页面经 MaterialTheme.spacing 取用
    CompositionLocalProvider(LocalSpacing provides AnSpacing()) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AnTypography,
            content = content,
        )
    }
}

// 主题预览：浅色
@Preview(name = "Theme-Light")
@Composable
private fun AnlobehubThemeLightPreview() {
    AnlobehubTheme(darkTheme = false) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("浅色主题", style = MaterialTheme.typography.titleLarge)
            Text("正文行高预览：安罗贝胡，阅读无压力。", style = MaterialTheme.typography.bodyLarge)
            Text("标签预览", style = MaterialTheme.typography.labelLarge)
        }
    }
}

// 主题预览：深色
@Preview(
    name = "Theme-Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AnlobehubThemeDarkPreview() {
    AnlobehubTheme(darkTheme = true) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("深色主题", style = MaterialTheme.typography.titleLarge)
            Text("正文行高预览：安罗贝胡，阅读无压力。", style = MaterialTheme.typography.bodyLarge)
            Text("标签预览", style = MaterialTheme.typography.labelLarge)
        }
    }
}
