package radomskiy.appy.svg_glb_view.Views

import android.annotation.SuppressLint
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import kotlinx.serialization.json.Json
import radomskiy.appy.svg_glb_view.Model.Marker
import java.io.File
import java.lang.Exception

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GLBViewer(
    modifier: Modifier,
    model: String,
    damageIds: List<Marker>,
    onElementClicked: (String) -> Unit
) {
    val context = LocalContext.current
    var isPageLoaded by remember { mutableStateOf(false) }

    val assetLoader = remember {
        WebViewAssetLoader.Builder()
            .addPathHandler(
                "/assets/", WebViewAssetLoader
                    .AssetsPathHandler(context)
            )
            .build()
    }
    val jsArray = Json.encodeToString(damageIds)

    val webView = remember {
        WebView(context).apply {
            setBackgroundColor(0x00000000)
            setLayerType(
                View.LAYER_TYPE_HARDWARE,
                null
            )

            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowContentAccess = true


            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    return assetLoader.shouldInterceptRequest(request?.url ?: return null)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    isPageLoaded = true
                }
            }
            webChromeClient = WebChromeClient()
            addJavascriptInterface(WebAppInterface(onElementClicked), "elementClicked")
            loadUrl("https://appassets.androidplatform.net/assets/index.html")
        }
    }

    LaunchedEffect(model, damageIds, isPageLoaded) {
        if (isPageLoaded && model.isNotEmpty()) {
            try {
                val virtualUrl = "https://appassets.androidplatform.net/assets/3D/$model.glb"

                webView.evaluateJavascript("loadModel('$virtualUrl');", null)

                val json = jsArray.replace("'", "\\'")
                webView.evaluateJavascript("load2DDamageTo3D($json);", null)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier.fillMaxSize()
    )
}

