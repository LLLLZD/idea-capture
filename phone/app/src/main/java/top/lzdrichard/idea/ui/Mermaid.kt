package top.lzdrichard.idea.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/**
 * 用系统 WebView + 打包在 assets 里的 mermaid 渲染流程图，不联网、不上报。
 * 渲染失败（语法错误等）时退回纯文本展示，避免整段稿子看不下去。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MermaidDiagram(code: String, modifier: Modifier = Modifier) {
    var heightDp by remember(code) { mutableStateOf(200) }
    val html = remember(code) { mermaidHtml(code) }

    AndroidView(
        modifier = modifier.fillMaxWidth().height(heightDp.dp),
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = false
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun resize(px: Int) {
                            val dp = (px / ctx.resources.displayMetrics.density).toInt()
                            Handler(Looper.getMainLooper()).post {
                                if (dp > 40) heightDp = dp.coerceIn(80, 2000)
                            }
                        }
                    },
                    "Android",
                )
                webViewClient = WebViewClient()
                loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null)
            }
        },
    )
}

@Composable
fun MermaidFallback(code: String, modifier: Modifier = Modifier) {
    Text(
        text = code,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        modifier = modifier,
    )
}

private fun mermaidHtml(code: String): String {
    val quoted = JSONObject.quote(code)
    return """
<!doctype html>
<html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
  html,body { margin:0; padding:10px 8px; background:#EDE7DA; }
  #c svg { max-width:100%; height:auto; }
  #err { color:#9b3b2f; font-size:12px; white-space:pre-wrap; margin:0; }
</style>
<script src="mermaid.min.js"></script>
</head>
<body>
<div id="c"></div>
<pre id="err" style="display:none"></pre>
<script>
  var code = $quoted;
  function report() {
    var h = Math.ceil(document.body.getBoundingClientRect().height);
    try { Android.resize(h); } catch (e) {}
  }
  (function () {
    try {
      mermaid.initialize({ startOnLoad: false, theme: 'neutral', securityLevel: 'strict' });
      mermaid.render('d0', code).then(function (r) {
        document.getElementById('c').innerHTML = r.svg;
        setTimeout(report, 50);
      }).catch(function (e) {
        var el = document.getElementById('err');
        el.style.display = 'block';
        el.textContent = '流程图解析失败：' + (e && e.message ? e.message : e);
        setTimeout(report, 50);
      });
    } catch (e) {
      var el = document.getElementById('err');
      el.style.display = 'block';
      el.textContent = '流程图渲染不可用：' + e;
      setTimeout(report, 50);
    }
  })();
</script>
</body></html>
"""
}
