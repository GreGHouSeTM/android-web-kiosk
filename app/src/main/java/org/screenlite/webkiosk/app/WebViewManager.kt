package org.screenlite.webkiosk.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.View
import android.webkit.*
import androidx.annotation.RequiresApi
import org.screenlite.webkiosk.components.RotatedWebView
import org.screenlite.webkiosk.data.Rotation

class WebViewManager(
    private val context: Context,
    private val onError: (Boolean) -> Unit,
    private val onPageLoading: (Boolean) -> Unit
) {
    private var currentWebView: WebView? = null

    fun createWebView(rotation: Rotation = Rotation.ROTATION_0): WebView {
        val webView = RotatedWebView(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
            overScrollMode = View.OVER_SCROLL_NEVER
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            appliedRotation = rotation.degrees.toFloat()
            requestFocus()
            configureWebViewSettings()
            setupWebViewListeners()
        }
        currentWebView = webView
        return webView
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun WebView.configureWebViewSettings() {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = false
            setSupportMultipleWindows(true)
            // Remote debugging is useful during development but not needed on the TV.
            WebView.setWebContentsDebuggingEnabled(false)
            cacheMode = WebSettings.LOAD_DEFAULT
            displayZoomControls = false
            builtInZoomControls = false
            setSupportZoom(false)
            textZoom = 100
            useWideViewPort = true
            loadWithOverviewMode = true
        }
    }

    fun updateRotation(rotation: Rotation) {
        currentWebView?.let { webView ->
            if (webView is RotatedWebView) {
                webView.appliedRotation = rotation.degrees.toFloat()
            }
            ViewportMetaInjector.inject(webView)
        }
    }

    private fun WebView.setupWebViewListeners() {
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                view.visibility = View.VISIBLE
                onPageLoading(true)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                ViewportMetaInjector.inject(view)
                view.visibility = View.VISIBLE
                onPageLoading(false)
            }

            @Suppress("DEPRECATION")
            @Deprecated("Deprecated in API 23")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                Log.e("WebViewManager", "Page failed: $failingUrl, code=$errorCode, desc=$description")
                onPageLoading(false)
                onError(true)
                super.onReceivedError(view, errorCode, description, failingUrl)
            }

            @RequiresApi(Build.VERSION_CODES.M)
            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    onPageLoading(false)
                    onError(true)
                    Log.e(
                        "WebViewManager",
                        "Main page failed: ${request.url}, code=${error.errorCode}, desc=${error.description}"
                    )
                } else {
                    Log.d("WebViewManager", "Subresource failed: ${request.url}")
                }
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                val tempWebView = WebView(context).apply {
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url?.toString() ?: return false
                            this@WebViewManager.currentWebView?.loadUrl(url)
                            return true
                        }
                    }
                }
                transport?.webView = tempWebView
                resultMsg?.sendToTarget()
                return true
            }
        }
    }
}
