package com.laprincesa.pos;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

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
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private WebView web;
    private WebViewAssetLoader assetLoader;
    private BluetoothSocket socket;
    private OutputStream printerOut;
    private String lastMac = "";

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web = new WebView(this);
        setContentView(web);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }
        });
        web.addJavascriptInterface(new NativePrinter(), "NativePrinter");
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