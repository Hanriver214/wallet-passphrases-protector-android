package com.walletprotector.android;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Entry activity. Hosts a single WebView that loads the bundled, fully-local
 * web assets from file:///android_asset/www/index.html. No network permission
 * is declared in the manifest, so the app is strictly offline.
 *
 * File downloads (e.g. the generated "密码表" cipher table) use the Storage
 * Access Framework (ACTION_CREATE_DOCUMENT) so the user picks the save
 * location themselves — no storage permissions needed.
 */
public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int CREATE_FILE_REQUEST = 1002;

    private PendingDownload pendingDownload;

    private static class PendingDownload {
        String filename;
        String base64Data;
        String mimeType;
        PendingDownload(String filename, String base64Data, String mimeType) {
            this.filename = filename;
            this.base64Data = base64Data;
            this.mimeType = mimeType;
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(true);

        webView.addJavascriptInterface(new DownloadBridge(), "AndroidDownloader");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript(buildDownloadInterceptorJs(), null);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view,
                                             ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;
                try {
                    Intent intent = params.createIntent();
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        webView.loadUrl("file:///android_asset/www/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (filePathCallback == null) {
                return;
            }
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                results = new Uri[]{data.getData()};
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        } else if (requestCode == CREATE_FILE_REQUEST) {
            handleCreateFileResult(resultCode, data);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void handleCreateFileResult(int resultCode, Intent data) {
        PendingDownload pending = pendingDownload;
        pendingDownload = null;

        if (pending == null) {
            return;
        }

        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            Toast.makeText(this, "已取消保存", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri uri = data.getData();
        try {
            byte[] fileData = android.util.Base64.decode(
                    pending.base64Data, android.util.Base64.DEFAULT);
            if (fileData == null || fileData.length == 0) {
                Toast.makeText(this, "保存失败：文件为空", Toast.LENGTH_SHORT).show();
                return;
            }
            writeFileData(uri, fileData);
            Toast.makeText(this, "保存成功：" + pending.filename,
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败：" + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK && webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private static String buildDownloadInterceptorJs() {
        return "(function() {"
            + "  var _createObjectURL = URL.createObjectURL.bind(URL);"
            + "  var _blobStore = new Map();"
            + "  URL.createObjectURL = function(blob) {"
            + "    var url = _createObjectURL(blob);"
            + "    var reader = new FileReader();"
            + "    reader.onload = function() {"
            + "      var base64 = reader.result.split(',')[1];"
            + "      _blobStore.set(url, {data: base64, mime: blob.type});"
            + "    };"
            + "    reader.readAsDataURL(blob);"
            + "    return url;"
            + "  };"
            + "  document.addEventListener('click', function(e) {"
            + "    var a = e.target.closest('a[download]');"
            + "    if (!a) return;"
            + "    var href = a.href;"
            + "    var filename = a.download || 'download.txt';"
            + "    if (href.startsWith('blob:')) {"
            + "      e.preventDefault();"
            + "      e.stopPropagation();"
            + "      var info = _blobStore.get(href);"
            + "      if (info && info.data && window.AndroidDownloader) {"
            + "        AndroidDownloader.requestSaveFile(filename, info.data, info.mime);"
            + "      }"
            + "    }"
            + "  }, true);"
            + "})();";
    }

    private void writeFileData(Uri uri, byte[] data) throws IOException {
        OutputStream os = getContentResolver().openOutputStream(uri);
        if (os == null) {
            throw new IOException("无法打开输出流");
        }
        try {
            os.write(data);
            os.flush();
        } finally {
            os.close();
        }
    }

    public class DownloadBridge {

        @JavascriptInterface
        public void requestSaveFile(final String filename,
                                     final String base64Data,
                                     final String mimeType) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    pendingDownload = new PendingDownload(filename, base64Data, mimeType);
                    String resolvedMime = (mimeType != null && !mimeType.isEmpty())
                            ? mimeType : "text/plain";

                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType(resolvedMime);
                    intent.putExtra(Intent.EXTRA_TITLE, filename);

                    try {
                        startActivityForResult(intent, CREATE_FILE_REQUEST);
                    } catch (Exception e) {
                        pendingDownload = null;
                        Toast.makeText(MainActivity.this,
                                "无法打开文件保存对话框：" + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                }
            });
        }
    }
}
