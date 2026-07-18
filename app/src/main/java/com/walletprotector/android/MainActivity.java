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

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int CREATE_FILE_REQUEST = 1002;

    private PendingDownload pendingDownload;

    private static class PendingDownload {
        String filename;
        String content;
        PendingDownload(String filename, String content) {
            this.filename = filename;
            this.content = content;
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

        webView.addJavascriptInterface(new SaveBridge(), "AndroidSave");

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
            byte[] fileData = pending.content.getBytes("UTF-8");
            if (fileData == null || fileData.length == 0) {
                Toast.makeText(this, "保存失败：文件为空", Toast.LENGTH_SHORT).show();
                return;
            }
            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os == null) {
                throw new IOException("无法打开输出流");
            }
            try {
                os.write(fileData);
                os.flush();
            } finally {
                os.close();
            }
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

    public class SaveBridge {

        @JavascriptInterface
        public void saveText(final String filename, final String content) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    pendingDownload = new PendingDownload(filename, content);

                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("text/plain");
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
