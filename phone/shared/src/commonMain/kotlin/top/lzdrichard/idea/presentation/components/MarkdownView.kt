package top.lzdrichard.idea.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import top.lzdrichard.idea.presentation.theme.AppSpacing

/**
 * 极简 Markdown 渲染（稿子只用标题/列表/段落/代码块/加粗）。
 *
 * 字号全部走 MaterialTheme.typography，颜色走 MaterialTheme.colorScheme，
 * 代码块与流程图底色走 surfaceVariant —— 换主题不用改这里。
 */
@Composable
fun MarkdownView(md: String, modifier: Modifier = Modifier) {
    val spacing = AppSpacing.current
    val blocks = remember(md) { parseMarkdown(md) }
    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    Spacer(Modifier.height(if (block.level == 1) spacing.lg else spacing.md))
                    Text(
                        text = block.text,
                        style = if (block.level == 1) {
                            MaterialTheme.typography.titleLarge
                        } else {
                            MaterialTheme.typography.titleMedium
                        },
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(spacing.xs))
                }
                is MdBlock.Bullet -> {
                    Row(Modifier.padding(vertical = spacing.xxs)) {
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(spacing.sm))
                        Text(
                            text = inlineStyled(block.text),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                is MdBlock.Paragraph -> {
                    Text(
                        text = inlineStyled(block.text),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = spacing.xxs),
                    )
                }
                is MdBlock.Code -> {
                    Spacer(Modifier.height(spacing.sm))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (block.lang == "mermaid") {
                            MermaidDiagram(block.text, Modifier.fillMaxWidth())
                        } else {
                            Text(
                                text = block.text,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(spacing.md),
                            )
                        }
                    }
                    Spacer(Modifier.height(spacing.sm))
                }
            }
        }
    }
}

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Code(val lang: String, val text: String) : MdBlock
}

private const val FENCE = "\u0060\u0060\u0060"

private fun parseMarkdown(md: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = md.replace("\r\n", "\n").split("\n")
    var i = 0
    while (i < lines.size) {
        val line = lines[i].trim()
        when {
            line.startsWith(FENCE) -> {
                val lang = line.removePrefix(FENCE).trim()
                val content = StringBuilder()
                i += 1
                while (i < lines.size && !lines[i].trim().startsWith(FENCE)) {
                    content.append(lines[i]).append("\n")
                    i += 1
                }
                blocks.add(MdBlock.Code(lang, content.toString().trimEnd()))
            }
            line.startsWith("## ") -> blocks.add(MdBlock.Heading(2, line.removePrefix("## ").trim()))
            line.startsWith("# ") -> blocks.add(MdBlock.Heading(1, line.removePrefix("# ").trim()))
            line.startsWith("- ") || line.startsWith("* ") -> blocks.add(MdBlock.Bullet(line.substring(2).trim()))
            line.isEmpty() -> Unit
            else -> blocks.add(MdBlock.Paragraph(line))
        }
        i += 1
    }
    return blocks
}

@Composable
private fun inlineStyled(text: String): AnnotatedString = remember(text) {
    buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            val start = text.indexOf("**", index)
            if (start < 0) {
                append(text.substring(index))
                break
            }
            val end = text.indexOf("**", start + 2)
            if (end < 0) {
                append(text.substring(index))
                break
            }
            append(text.substring(index, start))
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append(text.substring(start + 2, end))
            }
            index = end + 2
        }
    }
}
