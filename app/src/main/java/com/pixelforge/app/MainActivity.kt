package com.pixelforge.app

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermission: PermissionRequest? = null

    private val fileChooser =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            fileCallback?.onReceiveValue(uris.toTypedArray())
            fileCallback = null
        }

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val req = pendingPermission
            pendingPermission = null
            if (req != null) {
                if (granted) req.grant(req.resources) else req.deny()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        web = WebView(this)
        setContentView(
            web,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        WebView.setWebContentsDebuggingEnabled(true)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
        }

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(BLOB_HOOK, null)
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    val wantsCamera = request.resources.any {
                        it == PermissionRequest.RESOURCE_VIDEO_CAPTURE
                    }
                    if (!wantsCamera) {
                        request.grant(request.resources)
                        return@runOnUiThread
                    }
                    if (hasCamera()) {
                        request.grant(request.resources)
                    } else {
                        pendingPermission = request
                        cameraPermission.launch(Manifest.permission.CAMERA)
                    }
                }
            }

            override fun onShowFileChooser(
                webView: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    fileChooser.launch("image/*")
                    true
                } catch (e: Exception) {
                    fileCallback = null
                    false
                }
            }
        }

        web.addJavascriptInterface(Downloader(), "AndroidDownloader")

        if (!hasCamera()) {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }

        onBackPressedDispatcher.addCallback(this, object :
            androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })

        if (savedInstanceState == null) {
            web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
        } else {
            web.restoreState(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    private fun hasCamera(): Boolean =
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    // __REMAINDER__

    /** Bridge that lets the web page hand a generated image to Android for saving. */
    inner class Downloader {
        @JavascriptInterface
        fun saveBase64(fileName: String, base64: String, mime: String) {
            Thread {
                try {
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    val name = if (fileName.isBlank()) "pixelforge_${System.currentTimeMillis()}" else fileName
                    val ok = saveImage(name, mime, bytes)
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            if (ok) "Saved to Pictures/PixelForge" else "Save failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Save error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        }
    }

    private fun saveImage(name: String, mime: String, bytes: ByteArray): Boolean {
        val resolver = contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, if (mime.isBlank()) "image/jpeg" else mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/PixelForge")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return false
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return true
        } else {
            @Suppress("DEPRECATION")
            val dir = java.io.File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "PixelForge"
            )
            if (!dir.exists()) dir.mkdirs()
            val out: OutputStream = java.io.FileOutputStream(java.io.File(dir, name))
            out.use { it.write(bytes) }
            return true
        }
    }

    companion object {
        /**
         * Intercepts blob:-URL downloads triggered by anchor clicks in the page,
         * reads the blob as base64, and routes it to the native saver so images
         * land in the device gallery. The web app's own logic is untouched.
         */
        private const val BLOB_HOOK = """
            (function(){
              if (window.__pfHook) return; window.__pfHook = true;
              var orig = HTMLAnchorElement.prototype.click;
              HTMLAnchorElement.prototype.click = function(){
                try {
                  if (this.href && this.href.indexOf('blob:') === 0 && this.download){
                    var name = this.download;
                    fetch(this.href).then(function(r){return r.blob();}).then(function(b){
                      var fr = new FileReader();
                      fr.onload = function(){
                        var s = String(fr.result);
                        var i = s.indexOf(',');
                        AndroidDownloader.saveBase64(name, s.substring(i+1), b.type || 'image/jpeg');
                      };
                      fr.readAsDataURL(b);
                    });
                    return;
                  }
                } catch(e){}
                return orig.apply(this, arguments);
              };
            })();
        """
    }
}
