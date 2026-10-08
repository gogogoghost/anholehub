package cc.jaxy.anlobehub.core.designsystem.component

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.m3.Markdown

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    if (markdown.isEmpty()) return
    SelectionContainer {
        Markdown(
            content = markdown,
            modifier = modifier,
        )
    }
}
