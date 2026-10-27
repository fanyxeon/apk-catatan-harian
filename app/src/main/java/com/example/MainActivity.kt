package com.example

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JsResult
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.JeffBg
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(JeffBg)
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        JeffMoneyAppScreen()
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun JeffMoneyAppScreen() {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var fileUploadCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    // File picker launcher untuk upload/ganti foto Jeff
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        fileUploadCallback?.let { callback ->
            if (uri != null) {
                callback.onReceiveValue(arrayOf(uri))
            } else {
                callback.onReceiveValue(null)
            }
        }
        fileUploadCallback = null
    }

    // Tangani tombol Back fisik di Android tanpa lag
    BackHandler(enabled = canGoBack) {
        webViewRef?.goBack()
    }

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .testTag("jeff_money_webview"),
        factory = { ctx ->
            WebView(ctx).apply {
                // Aktifkan akselerasi perangkat keras
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mediaPlaybackRequiresUserGesture = false
                    setOffscreenPreRaster(true)
                }

                // WebChromeClient menangani file picker dan dialog JavaScript
                webChromeClient = object : WebChromeClient() {
                    override fun onShowFileChooser(
                        webView: WebView?,
                        filePathCallback: ValueCallback<Array<Uri>>?,
                        fileChooserParams: FileChooserParams?
                    ): Boolean {
                        fileUploadCallback?.onReceiveValue(null)
                        fileUploadCallback = filePathCallback

                        try {
                            photoPickerLauncher.launch("image/*")
                        } catch (e: Exception) {
                            fileUploadCallback?.onReceiveValue(null)
                            fileUploadCallback = null
                            return false
                        }
                        return true
                    }

                    override fun onJsAlert(
                        view: WebView?,
                        url: String?,
                        message: String?,
                        result: JsResult?
                    ): Boolean {
                        AlertDialog.Builder(ctx)
                            .setTitle("Jeff Money")
                            .setMessage(message ?: "")
                            .setPositiveButton("OK") { _, _ -> result?.confirm() }
                            .setOnCancelListener { result?.cancel() }
                            .show()
                        return true
                    }

                    override fun onJsConfirm(
                        view: WebView?,
                        url: String?,
                        message: String?,
                        result: JsResult?
                    ): Boolean {
                        AlertDialog.Builder(ctx)
                            .setTitle("Konfirmasi")
                            .setMessage(message ?: "")
                            .setPositiveButton("Ya") { _, _ -> result?.confirm() }
                            .setNegativeButton("Batal") { _, _ -> result?.cancel() }
                            .setOnCancelListener { result?.cancel() }
                            .show()
                        return true
                    }

                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        consoleMessage?.let {
                            Log.d("JeffMoneyWeb", "${it.message()} -- From line ${it.lineNumber()} of ${it.sourceId()}")
                        }
                        return true
                    }
                }

                // WebViewClient mencegah navigasi keluar dan mengupdate status canGoBack
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        return false
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        canGoBack = view?.canGoBack() == true
                    }
                }

                // Download listener untuk export file CSV
                setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                    try {
                        val request = DownloadManager.Request(Uri.parse(url)).apply {
                            setMimeType(mimetype)
                            setDescription("Mengunduh laporan transaksi Jeff Money...")
                            setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype))
                            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            setDestinationInExternalPublicDir(
                                Environment.DIRECTORY_DOWNLOADS,
                                URLUtil.guessFileName(url, contentDisposition, mimetype)
                            )
                        }
                        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                        dm?.enqueue(request)
                        Toast.makeText(ctx, "Mulai mengunduh file CSV...", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Gagal mengunduh file", Toast.LENGTH_SHORT).show()
                    }
                }

                loadUrl("file:///android_asset/index.html")
                webViewRef = this
            }
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.destroy()
            webViewRef = null
        }
    }
}
