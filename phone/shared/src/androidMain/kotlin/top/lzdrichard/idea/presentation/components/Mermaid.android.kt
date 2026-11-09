package top.lzdrichard.idea.presentation.components

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Android 端流程图渲染：WebView 加载打包在 assets 里的 mermaid.min.js。
 *
 * - 不联网：所有资源来自 APK 内 assets
 * - 高度自适应：mermaid 渲染完通过 JS 桥回传像素高度
 * - 颜色全部来自 MaterialTheme，由调用处算好 hex 传进 HTML
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun MermaidDiagram(code: String, modifier: Modifier) {
    var heightPx by remember(code) { mutableStateOf(420) }
    val density = LocalDensity.current
    val heightDp = with(density) { heightPx.toDp() }
    val ink = MaterialTheme.colorScheme.onSurface.toHex()
    val accent = MaterialTheme.colorScheme.primary.toHex()
    val error = MaterialTheme.colorScheme.error.toHex()
    val surface = MaterialTheme.colorScheme.surface.toHex()

    AndroidView(
        modifier = modifier.fillMaxWidth().height(heightDp),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(0x00000000) // token-guard:allow WebView 需要透明背景，0x00000000 是 alpha=0 不是主题色
                settings.javaScriptEnabled = true
                settings.allowFileAccess = true
                addJavascriptInterface(Bridge { px ->
                    Handler(Looper.getMainLooper()).post { heightPx = px.coerceIn(80, 2400) }
                }, "Android")
                loadDataWithBaseURL("file:///android_asset/", buildMermaidHtml(code, ink, accent, error, surface), "text/html", "utf-8", null)
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                "file:///android_asset/",
                buildMermaidHtml(code, ink, accent, error, surface),
                "text/html",
                "utf-8",
                null,
            )
        },
    )
}

private fun Color.toHex(): String = "#" + ((toArgb() and 0xFFFFFF).toString(16).padStart(6, '0'))

private fun buildMermaidHtml(
    code: String,
    ink: String,
    accent: String,
    error: String,
    surface: String,
): String = """
<!doctype html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
  html, body { margin: 0; padding: 8px; background: transparent; }
  #d0 { display: flex; justify-content: center; }
  .err { font: 13px -apple-system, sans-serif; color: ${error}; }
</style>
<script src="mermaid.min.js"></script>
</head>
<body>
<div id="d0"></div>
<script>
  mermaid.initialize({
    startOnLoad: false,
    theme: 'base',
    themeVariables: {
      primaryColor: ${jsString(surface)},
      primaryTextColor: ${jsString(ink)},
      primaryBorderColor: ${jsString(accent)},
      lineColor: ${jsString(ink)},
      textColor: ${jsString(ink)}
    },
    flowchart: { htmlLabels: true, useMaxWidth: true }
  });
  (async () => {
    const src = ${jsString(code)};
    try {
      const out = await mermaid.render('g0', src);
      document.getElementById('d0').innerHTML = out.svg;
      Android.resize(document.body.scrollHeight + 16);
    } catch (e) {
      document.getElementById('d0').innerHTML = '<div class="err">流程图解析失败：' + e.message + '</div>';
      Android.resize(document.body.scrollHeight + 16);
    }
  })();
</script>
</body>
</html>
""".trimIndent()

/** 把任意文本安全塞进 JS 字符串字面量（避免引号/换行把脚本打断）。 */
private fun jsString(value: String): String {
    val sb = StringBuilder("\"")
    for (ch in value) {
        when (ch) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '<' -> sb.append("\\u003c")
            else -> sb.append(ch)
        }
    }
    return sb.append('"').toString()
}

private class Bridge(private val onResize: (Int) -> Unit) {
    @JavascriptInterface
    fun resize(px: Int) = onResize(px)
}
