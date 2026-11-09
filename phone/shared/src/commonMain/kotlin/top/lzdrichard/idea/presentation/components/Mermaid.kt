package top.lzdrichard.idea.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 流程图渲染：Android 用系统 WebView + 打包在 assets 里的 mermaid，不联网、不上报。
 * iOS 暂按等宽文本展示源码（渲染器待接 WKWebView），不阻塞出稿链路。
 */
@Composable
expect fun MermaidDiagram(code: String, modifier: Modifier)
