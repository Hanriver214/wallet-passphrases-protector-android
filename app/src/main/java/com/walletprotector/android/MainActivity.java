package com.walletprotector.android;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private WebView webView;
    private static final int OPEN_FILE_REQUEST = 1001;
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
        webView.addJavascriptInterface(new LoaderBridge(), "AndroidLoader");

        webView.loadUrl("file:///android_asset/www/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == OPEN_FILE_REQUEST) {
            handleOpenFileResult(resultCode, data);
        } else if (requestCode == CREATE_FILE_REQUEST) {
            handleCreateFileResult(resultCode, data);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void handleOpenFileResult(int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            deliverFileContent("", "已取消选择");
            return;
        }
        Uri uri = data.getData();
        try {
            String content = readFileText(uri);
            if (content == null || content.isEmpty()) {
                deliverFileContent("", "文件内容为空");
                return;
            }
            String filename = queryFilename(uri);
            deliverFileContent(content, filename);
        } catch (Exception e) {
            deliverFileContent("", "读取失败：" + e.getMessage());
        }
    }

    private String readFileText(Uri uri) throws IOException {
        InputStream is = getContentResolver().openInputStream(uri);
        if (is == null) {
            throw new IOException("无法打开输入流");
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = is.read(chunk)) != -1) {
                buffer.write(chunk, 0, n);
            }
            return buffer.toString("UTF-8");
        } finally {
            is.close();
        }
    }

    private String queryFilename(Uri uri) {
        String result = "mimabiao.txt";
        try (android.database.Cursor cursor = getContentResolver().query(
                uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String name = cursor.getString(idx);
                    if (name != null && !name.isEmpty()) {
                        result = name;
                    }
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    private void deliverFileContent(String content, String filename) {
        if (webView == null) return;
        final String jsContent = escapeForJs(content);
        final String jsFilename = escapeForJs(filename);
        final String js = "window._onFileLoaded && window._onFileLoaded("
                + jsFilename + ", " + jsContent + ");";
        runOnUiThread(() -> webView.evaluateJavascript(js, null));
    }

    private static String escapeForJs(String s) {
        if (s == null) s = "";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append("\"");
        return sb.toString();
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
            if (fileData.length == 0) {
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
            new Handler(Looper.getMainLooper()).post(() -> {
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
            });
        }
    }

    public class LoaderBridge {
        @JavascriptInterface
        public void openFile() {
            new Handler(Looper.getMainLooper()).post(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/plain");
                try {
                    startActivityForResult(intent, OPEN_FILE_REQUEST);
                } catch (Exception e) {
                    deliverFileContent("", "无法打开文件选择器：" + e.getMessage());
                }
            });
        }
    }
}
