package com.laprincesa.pos;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.view.View;

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
        // Prevent Android/Google Autofill overlays from fighting with the POS password fields.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            web.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        }

        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsPrompt(WebView view, String url, String message,
                                      String defaultValue, JsPromptResult result) {
                EditText input = new EditText(MainActivity.this);
                input.setSingleLine(true);
                input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                if (defaultValue != null) input.setText(defaultValue);
                input.setSelection(input.getText().length());

                AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Password Verification")
                    .setMessage(message)
                    .setView(input)
                    .setPositiveButton("Verify", (d, which) ->
                        result.confirm(input.getText().toString()))
                    .setNegativeButton("Cancel", (d, which) -> result.cancel())
                    .create();
                dialog.setOnCancelListener(d -> result.cancel());
                dialog.show();
                input.requestFocus();
                return true;
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("La Princesa POS")
                    .setMessage(message)
                    .setPositiveButton("OK", (d, which) -> result.confirm())
                    .setOnCancelListener(d -> result.cancel())
                    .show();
                return true;
            }
        });
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
        public boolean printHtmlBase64(String encodedHtml) {
            final String html;
            try {
                byte[] htmlBytes = Base64.decode(encodedHtml, Base64.DEFAULT);
                html = new String(htmlBytes, StandardCharsets.UTF_8);
            } catch (Exception e) {
                showReceiptPrintError("Hindi mabasa ang formatted receipt.");
                return false;
            }

            // Do not wait for WebView rendering here. JavaScript bridge calls can arrive
            // on the WebView UI thread; waiting for onPageFinished on that same thread
            // deadlocks until the caller times out.
            runOnUiThread(() -> {
                final WebView renderer = new WebView(MainActivity.this);
                try {
                    WebSettings ws = renderer.getSettings();
                    ws.setJavaScriptEnabled(true);
                    ws.setDomStorageEnabled(false);
                    ws.setDefaultTextEncodingName("UTF-8");
                    renderer.setBackgroundColor(Color.WHITE);
                    renderer.setVerticalScrollBarEnabled(false);
                    renderer.setHorizontalScrollBarEnabled(false);
                    renderer.setAlpha(1f);
                    renderer.setClickable(false);
                    renderer.setFocusable(false);
                    if (Build.VERSION.SDK_INT >= 16) {
                        renderer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                    }

                    int density = Math.round(getResources().getDisplayMetrics().density * 1000f);
                    int widthPx = Math.max(224, Math.round(224f * density / 1000f));
                    int maxHeightPx = Math.round(12000f * density / 1000f);
                    ViewGroup decor = (ViewGroup) getWindow().getDecorView();
                    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        widthPx, maxHeightPx, Gravity.TOP | Gravity.LEFT);
                    params.leftMargin = -widthPx - 20;
                    decor.addView(renderer, params);

                    renderer.setWebViewClient(new WebViewClient() {
                        private boolean handled = false;

                        @Override
                        public void onPageFinished(WebView view, String url) {
                            if (handled) return;
                            handled = true;
                            view.evaluateJavascript(
                                "String(Math.max(document.body.scrollHeight,document.documentElement.scrollHeight))",
                                value -> {
                                    byte[] data = null;
                                    String renderFailure = null;
                                    Bitmap raster = null;
                                    try {
                                        String clean = value == null ? "" : value.replace("\"", "").trim();
                                        int cssHeight = Integer.parseInt(clean);
                                        if (cssHeight <= 0) throw new IllegalStateException("empty receipt layout; JS height=" + clean);
                                        cssHeight = Math.min(cssHeight, 6000);
                                        int heightPx = Math.max(1, Math.round(cssHeight * density / 1000f));
                                        view.setLayoutParams(new FrameLayout.LayoutParams(
                                            widthPx, heightPx, Gravity.TOP | Gravity.LEFT));
                                        view.measure(
                                            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                                            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY));
                                        view.layout(0, 0, widthPx, heightPx);

                                        // Restore the earlier full-size WebView capture, then scale
                                        // to the printer width. Keep this inside the asynchronous UI
                                        // callback so the old CountDownLatch deadlock cannot return.
                                        Bitmap original = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
                                        try {
                                            Canvas canvas = new Canvas(original);
                                            canvas.drawColor(Color.WHITE);
                                            view.draw(canvas);
                                            int targetHeight = Math.max(1, Math.round(heightPx * 384f / widthPx));
                                            raster = Bitmap.createScaledBitmap(original, 384, targetHeight, true);
                                        } finally {
                                            if (!original.isRecycled()) original.recycle();
                                        }
                                        data = bitmapToEscPos(raster);
                                    } catch (OutOfMemoryError memoryError) {
                                        renderFailure = "Kulang ang memory (" + memoryError.getClass().getSimpleName() + ")";
                                    } catch (Exception renderError) {
                                        String detail = renderError.getMessage();
                                        renderFailure = "Render error: " + renderError.getClass().getSimpleName()
                                            + (detail == null || detail.isEmpty() ? "" : " — " + detail);
                                    } finally {
                                        if (raster != null && !raster.isRecycled()) raster.recycle();
                                        try {
                                            ViewGroup parent = (ViewGroup) view.getParent();
                                            if (parent != null) parent.removeView(view);
                                            view.stopLoading();
                                            view.destroy();
                                        } catch (Exception ignored) {}
                                    }

                                    final byte[] printBytes = data;
                                    if (printBytes == null || printBytes.length == 0) {
                                        showReceiptPrintError(renderFailure == null
                                            ? "Walang laman ang render output. Subukan muli."
                                            : renderFailure);
                                        return;
                                    }
                                    new Thread(() -> {
                                        try {
                                            if (out == null && !connect(lastMac)) {
                                                showReceiptPrintError("Hindi makakonekta sa printer para sa resibo.");
                                                return;
                                            }
                                            final int chunkSize = 1024;
                                            for (int offset = 0; offset < printBytes.length; offset += chunkSize) {
                                                int length = Math.min(chunkSize, printBytes.length - offset);
                                                out.write(printBytes, offset, length);
                                                out.flush();
                                                Thread.sleep(12);
                                            }
                                        } catch (Exception e) {
                                            closePrinter();
                                            showReceiptPrintError("Nabigo ang pagpapadala ng resibo sa printer.");
                                        }
                                    }, "ReceiptPrintWorker").start();
                                });
                        }
                    });
                    renderer.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null);
                } catch (Exception e) {
                    try {
                        ViewGroup parent = (ViewGroup) renderer.getParent();
                        if (parent != null) parent.removeView(renderer);
                        renderer.destroy();
                    } catch (Exception ignored) {}
                    showReceiptPrintError("Hindi masimulan ang pag-render ng resibo.");
                }
            });
            return true;
        }

        private void showReceiptPrintError(String message) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
        }

        private byte[] bitmapToEscPos(Bitmap bitmap) throws Exception {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int widthBytes = (width + 7) / 8;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(8 + widthBytes * height + 8);
            bytes.write(new byte[] { 0x1B, 0x40 });
            bytes.write(new byte[] {
                0x1D, 0x76, 0x30, 0x00,
                (byte) (widthBytes & 0xFF), (byte) ((widthBytes >> 8) & 0xFF),
                (byte) (height & 0xFF), (byte) ((height >> 8) & 0xFF)
            });
            for (int y = 0; y < height; y++) {
                for (int xb = 0; xb < widthBytes; xb++) {
                    int value = 0;
                    for (int bit = 0; bit < 8; bit++) {
                        int x = xb * 8 + bit;
                        if (x < width) {
                            int color = bitmap.getPixel(x, y);
                            int luminance = (Color.red(color) * 299
                                + Color.green(color) * 587
                                + Color.blue(color) * 114) / 1000;
                            if (luminance < 180) value |= (0x80 >> bit);
                        }
                    }
                    bytes.write(value);
                }
            }
            bytes.write(new byte[] { 0x0A, 0x0A, 0x0A, 0x1D, 0x56, 0x42, 0x00 });
            return bytes.toByteArray();
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
