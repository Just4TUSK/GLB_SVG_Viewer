package radomskiy.appy.svg_glb_view.Views

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.serialization.json.Json
import radomskiy.appy.svg_glb_view.Model.Marker

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SVGViewer(
    modifier: Modifier,
    fileName: String,
    damageIds: List<Marker>,
    onElementClicked: (String) -> Unit
) {
    val context = LocalContext.current
    val jsArray = Json.encodeToString(damageIds)

    var isPageLoaded by remember { mutableStateOf(false) }

    val webView = remember {
        WebView(context).apply {
            setBackgroundColor(0x00000000)

            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    isPageLoaded = true
                }
            }
            webChromeClient = WebChromeClient()
            addJavascriptInterface(WebAppInterface(onElementClicked), "elementClicked")
        }
    }

    LaunchedEffect(fileName) {
            isPageLoaded = false
            val svgContent = context.assets.open(fileName).bufferedReader().use { it.readText() }
            webView.loadDataWithBaseURL(
                "file:///android_asset/",
                svgContent,
                "text/html",
                "utf-8",
                null
            )
    }
    LaunchedEffect(jsArray, isPageLoaded) {
        if (isPageLoaded) {
            val json = jsArray.replace("'", "\\'")
            webView.evaluateJavascript("load3DDamageToSVG($json);", null)
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier.fillMaxSize()
    )
}


class WebAppInterface(private val onMessageReceived: (String) -> Unit) {
    @JavascriptInterface
    fun postMessage(message: String) {
        onMessageReceived(message)
    }
}