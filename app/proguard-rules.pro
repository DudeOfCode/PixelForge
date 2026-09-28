# Keep the JavaScript bridge used by the WebView
-keepclassmembers class com.pixelforge.app.** {
    @android.webkit.JavascriptInterface <methods>;
}
