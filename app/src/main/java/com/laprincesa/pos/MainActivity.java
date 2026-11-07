package com.laprincesa.pos;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.ValueCallback;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.webkit.WebViewAssetLoader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_BT = 41;
    private static final int REQUEST_SAVE_BACKUP = 42;
    private static final int REQUEST_OPEN_BACKUP = 43;
    private ValueCallback<Uri[]> fileChooserCallback;
    private String pendingBackupText = null;
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private WebView web;
    private WebViewAssetLoader assetLoader;
    private BluetoothSocket socket;
    private OutputStream printerOut;
    private String lastMac = "";

    // Apply login/input keyboard fixes after the bundled page loads.
    private static final String FIX_JS =
            "(function(){if(window.__lpKbFix)return;window.__lpKbFix=1;"
            + "var s=document.createElement('style');"
            + "s.textContent='#lp-gate{align-items:safe center!important;padding-top:84px!important}';"
            + "document.head.appendChild(s);"
            + "function fx(e){if(!e||e.nodeType!==1)return;"
            + "var t=(e.getAttribute('type')||'text').toLowerCase();"
            + "if((e.tagName==='INPUT'&&(t==='text'||t==='number'||t==='tel'||t==='search'||t==='email'))||e.tagName==='TEXTAREA'){e.setAttribute('autocomplete','off');}}"
            + "function sc(r){if(r&&r.querySelectorAll){[].forEach.call(r.querySelectorAll('input,textarea'),fx);}}"
            + "sc(document);"
            + "new MutationObserver(function(ms){for(var i=0;i<ms.length;i++){var a=ms[i].addedNodes;for(var j=0;j<a.length;j++){fx(a[j]);sc(a[j]);}}}).observe(document.documentElement,{childList:true,subtree:true});"
            + "document.addEventListener('focusin',function(ev){var t=ev.target;"
            + "if(t&&(t.tagName==='INPUT'||t.tagName==='TEXTAREA')&&t.closest&&t.closest('#lp-gate')){"
            + "setTimeout(function(){try{t.scrollIntoView({block:'center'});}catch(x){}},350);}});"
            + "})();";


    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        // Let Android resize the WebView when the soft keyboard appears.
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web = new WebView(this);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        setContentView(web);
        web.requestFocus(View.FOCUS_DOWN);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                fileChooserCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                try { startActivityForResult(intent, REQUEST_OPEN_BACKUP); }
                catch (Exception e) { fileChooserCallback = null; callback.onReceiveValue(null); return false; }
                return true;
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript(FIX_JS, null);
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("appassets.androidplatform.net".equals(uri.getHost())) return false;
                String scheme = uri.getScheme();
                if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) { }
                }
                return true;
            }
        });
        web.addJavascriptInterface(new NativePrinter(), "NativePrinter");
        web.addJavascriptInterface(new NativeFiles(), "NativeFiles");
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html");
        requestBluetoothPermissions();
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            String[] permissions = new String[] {
                    Manifest.permission.BLUETOOTH_CONNECT
            };
            boolean missing = false;
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    missing = true;
                    break;
                }
            }
            if (missing) ActivityCompat.requestPermissions(this, permissions, REQUEST_BT);
        }
    }

    private boolean hasBluetoothPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    public class NativeFiles {
        @JavascriptInterface public boolean saveBackup(String filename, String jsonText) {
            if (jsonText == null || jsonText.length() > 20_000_000) return false;
            runOnUiThread(() -> {
                pendingBackupText = jsonText;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, filename == null || filename.trim().isEmpty() ? "LaPrincesa_backup.json" : filename);
                try { startActivityForResult(intent, REQUEST_SAVE_BACKUP); }
                catch (Exception e) { pendingBackupText = null; }
            });
            return true;
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OPEN_BACKUP) {
            if (fileChooserCallback != null) {
                Uri[] result = resultCode == RESULT_OK && data != null && data.getData() != null
                        ? new Uri[] { data.getData() } : null;
                fileChooserCallback.onReceiveValue(result);
                fileChooserCallback = null;
            }
            return;
        }
        if (requestCode == REQUEST_SAVE_BACKUP) {
            String content = pendingBackupText;
            pendingBackupText = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null && content != null) {
                try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                    if (out != null) out.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } catch (Exception ignored) { }
            }
        }
    }

    public class NativePrinter {
        @JavascriptInterface public String listPrinters() {
            JSONArray result = new JSONArray();
            try {
                if (!hasBluetoothPermission()) return result.toString();
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter == null) return result.toString();
                for (BluetoothDevice device : adapter.getBondedDevices()) {
                    JSONObject item = new JSONObject();
                    item.put("name", device.getName() == null ? "Bluetooth Printer" : device.getName());
                    item.put("mac", device.getAddress());
                    result.put(item);
                }
            } catch (Exception ignored) { }
            return result.toString();
        }

        @JavascriptInterface public boolean connect(String mac) {
            try {
                if (!hasBluetoothPermission()) return false;
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter == null) return false;
                BluetoothDevice device = (mac == null || mac.trim().isEmpty())
                        ? null : adapter.getRemoteDevice(mac.trim());
                if (device == null) {
                    for (BluetoothDevice candidate : adapter.getBondedDevices()) {
                        device = candidate;
                        break;
                    }
                }
                if (device == null) return false;
                closePrinter();
                socket = device.createRfcommSocketToServiceRecord(SPP);
                socket.connect();
                printerOut = socket.getOutputStream();
                lastMac = device.getAddress();
                return true;
            } catch (Exception error) {
                closePrinter();
                return false;
            }
        }

        @JavascriptInterface public boolean printBase64(String encoded) {
            try {
                if (printerOut == null && !connect(lastMac)) return false;
                byte[] data = Base64.decode(encoded, Base64.DEFAULT);
                final int chunkSize = 256;
                for (int offset = 0; offset < data.length; offset += chunkSize) {
                    int length = Math.min(chunkSize, data.length - offset);
                    printerOut.write(data, offset, length);
                    printerOut.flush();
                }
                return true;
            } catch (Exception error) {
                closePrinter();
                return false;
            }
        }

        @JavascriptInterface public boolean drawer() {
            return printBase64(Base64.encodeToString(
                    new byte[] {0x1B, 0x70, 0x00, 0x19, (byte) 0xFA}, Base64.NO_WRAP));
        }
    }

    private void closePrinter() {
        try { if (printerOut != null) printerOut.close(); } catch (Exception ignored) { }
        try { if (socket != null) socket.close(); } catch (Exception ignored) { }
        printerOut = null;
        socket = null;
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        closePrinter();
        if (web != null) web.destroy();
        super.onDestroy();
    }
}