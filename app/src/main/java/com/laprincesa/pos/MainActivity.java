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
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.OutputStream;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private WebView web;
    private BluetoothSocket socket;
    private OutputStream out;
    private static final int PERMISSION_REQUEST = 41;
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private String lastMac = "";

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        setContentView(web);

        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);

        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new NativePrinter(), "NativePrinter");

        // Load the POS bundled inside this APK. This avoids dependence on GitHub Pages,
        // network availability, redirects, and remote WebView startup failures.
        web.loadUrl("file:///android_asset/index.html");

        requestBluetoothPermissionsIfNeeded();
    }

    private void requestBluetoothPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 31) {
            String[] permissions = new String[] {
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            };
            boolean needsRequest = false;
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    needsRequest = true;
                    break;
                }
            }
            if (needsRequest) {
                ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST);
            }
        } else if (Build.VERSION.SDK_INT >= 23 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.ACCESS_FINE_LOCATION }, PERMISSION_REQUEST);
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.removeJavascriptInterface("NativePrinter");
            web.destroy();
            web = null;
        }
        closePrinter();
        super.onDestroy();
    }

    private void closePrinter() {
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        out = null;
        socket = null;
    }

    public class NativePrinter {
        @JavascriptInterface
        public String listPrinters() {
            try {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter == null || !adapter.isEnabled()) return "[]";
                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                for (BluetoothDevice device : adapter.getBondedDevices()) {
                    if (!first) json.append(",");
                    String name = device.getName() == null ? "" : device.getName();
                    String mac = device.getAddress() == null ? "" : device.getAddress();
                    json.append("{\"name\":\"").append(jsonEscape(name))
                        .append("\",\"mac\":\"").append(jsonEscape(mac)).append("\"}");
                    first = false;
                }
                return json.append("]").toString();
            } catch (Exception e) {
                return "[]";
            }
        }

        @JavascriptInterface
        public boolean connect(String mac) {
            try {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter == null || !adapter.isEnabled()) return false;
                BluetoothDevice device = null;
                if (mac != null && !mac.trim().isEmpty()) {
                    device = adapter.getRemoteDevice(mac.trim());
                } else {
                    for (BluetoothDevice candidate : adapter.getBondedDevices()) {
                        String name = candidate.getName() == null ? "" : candidate.getName().toLowerCase();
                        if (name.contains("xp") || name.contains("pos") || name.contains("printer") || name.contains("58")) {
                            device = candidate;
                            break;
                        }
                    }
                    if (device == null && !adapter.getBondedDevices().isEmpty()) {
                        device = adapter.getBondedDevices().iterator().next();
                    }
                }
                if (device == null) return false;
                closePrinter();
                adapter.cancelDiscovery();
                socket = device.createRfcommSocketToServiceRecord(SPP);
                socket.connect();
                out = socket.getOutputStream();
                lastMac = device.getAddress();
                return true;
            } catch (Exception e) {
                closePrinter();
                return false;
            }
        }

        @JavascriptInterface
        public boolean printBase64(String encoded) {
            try {
                if (out == null && !connect(lastMac)) return false;
                byte[] data = Base64.decode(encoded, Base64.DEFAULT);
                final int chunkSize = 200;
                for (int offset = 0; offset < data.length; offset += chunkSize) {
                    int length = Math.min(chunkSize, data.length - offset);
                    out.write(data, offset, length);
                    out.flush();
                    Thread.sleep(30);
                }
                return true;
            } catch (Exception e) {
                closePrinter();
                return false;
            }
        }

        @JavascriptInterface
        public boolean drawer() {
            byte[] command = new byte[] { 0x1B, 0x70, 0x00, 0x3C, (byte) 0xFF };
            return printBase64(Base64.encodeToString(command, Base64.NO_WRAP));
        }

        @JavascriptInterface
        public void toast(String message) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
        }
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r");
    }
}
