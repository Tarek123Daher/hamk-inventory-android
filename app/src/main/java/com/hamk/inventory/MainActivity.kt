package com.hamk.inventory

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.net.http.SslError

class MainActivity : android.app.Activity() {
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var offlineView: View
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    private val siteUri: Uri by lazy { Uri.parse(BuildConfig.SITE_URL) }
    private val fileChooserRequestCode = 4102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(9, 13, 19)
        window.navigationBarColor = Color.rgb(9, 13, 19)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(9, 13, 19))
        }
        webView = WebView(this)
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(226, 30, 43))
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.rgb(226, 30, 43))
            visibility = View.GONE
        }
        offlineView = createOfflineView()

        root.addView(webView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        root.addView(progressBar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(3),
            Gravity.TOP,
        ))
        root.addView(offlineView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        setContentView(root)

        CookieManager.getInstance().setAcceptCookie(true)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }
        webView.webViewClient = createWebViewClient()
        webView.webChromeClient = createWebChromeClient()
        webView.setDownloadListener(createDownloadListener())
        webView.loadUrl(BuildConfig.SITE_URL)
    }

    override fun onBackPressed() {
        if (this::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    @Deprecated("Required for the system file picker used by HTML upload fields")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != fileChooserRequestCode) return

        val callback = fileChooserCallback ?: return
        fileChooserCallback = null
        callback.onReceiveValue(
            if (resultCode == RESULT_OK) WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            else null,
        )
    }

    override fun onDestroy() {
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        if (this::webView.isInitialized) {
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun createWebViewClient() = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            if (isTrustedSite(uri)) return false

            if (uri.scheme == "https" || uri.scheme == "http" ||
                uri.scheme == "mailto" || uri.scheme == "tel"
            ) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(this@MainActivity, "No app can open this link.", Toast.LENGTH_SHORT).show()
                }
            }
            return true
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            super.onPageStarted(view, url, favicon)
            view?.visibility = View.VISIBLE
            offlineView.visibility = View.GONE
            progressBar.visibility = View.VISIBLE
            progressBar.isIndeterminate = true
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            progressBar.isIndeterminate = false
            progressBar.visibility = View.GONE
            if (url != null && isTrustedSite(Uri.parse(url))) offlineView.visibility = View.GONE
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            super.onReceivedError(view, request, error)
            if (request.isForMainFrame) showOfflineMessage()
        }

        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
            handler?.cancel()
            showOfflineMessage()
        }
    }

    private fun createWebChromeClient() = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            progressBar.isIndeterminate = false
            progressBar.progress = newProgress
            progressBar.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
        }

        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?,
        ): Boolean {
            fileChooserCallback?.onReceiveValue(null)
            fileChooserCallback = filePathCallback ?: return false

            return try {
                val pickerIntent = fileChooserParams?.createIntent()
                    ?: Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                startActivityForResult(pickerIntent, fileChooserRequestCode)
                true
            } catch (_: ActivityNotFoundException) {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = null
                false
            }
        }
    }

    private fun createDownloadListener() = DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
        val uri = Uri.parse(url)
        if (!isTrustedSite(uri) || uri.scheme != "https") {
            Toast.makeText(this, "Downloads are only allowed from HAMK Inventory.", Toast.LENGTH_LONG).show()
            return@DownloadListener
        }

        val request = DownloadManager.Request(uri).apply {
            setMimeType(mimeType)
            setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType))
            setDescription("Downloading from HAMK Inventory")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                URLUtil.guessFileName(url, contentDisposition, mimeType),
            )
            addRequestHeader("User-Agent", userAgent)
            CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
        }

        try {
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            Toast.makeText(this, "Download started.", Toast.LENGTH_SHORT).show()
        } catch (_: IllegalArgumentException) {
            Toast.makeText(this, "Could not start the download.", Toast.LENGTH_LONG).show()
        }
    }

    private fun createOfflineView(): View {
        val title = TextView(this).apply {
            text = "HAMK Inventory is unavailable"
            setTextColor(Color.WHITE)
            textSize = 21f
            gravity = Gravity.CENTER
        }
        val description = TextView(this).apply {
            text = "Check your internet connection and make sure the club server is running, then try again."
            setTextColor(Color.LTGRAY)
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(14), dp(24), dp(14))
        }
        val retryButton = Button(this).apply {
            text = "Try again"
            setOnClickListener {
                offlineView.visibility = View.GONE
                webView.loadUrl(BuildConfig.SITE_URL)
            }
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(24), dp(28), dp(24))
            visibility = View.GONE
            addView(title, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
            addView(description, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
            addView(retryButton, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.CENTER
                topMargin = dp(12)
            })
        }
    }

    private fun showOfflineMessage() {
        progressBar.visibility = View.GONE
        webView.visibility = View.GONE
        offlineView.visibility = View.VISIBLE
    }

    private fun isTrustedSite(uri: Uri): Boolean =
        uri.scheme.equals(siteUri.scheme, ignoreCase = true) &&
            uri.host.equals(siteUri.host, ignoreCase = true) &&
            uri.port == siteUri.port &&
            uri.userInfo == null

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
