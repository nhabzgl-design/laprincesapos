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
import android.content.Intent;
import android.net.Uri;
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
import android.webkit.ValueCallback;
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
    private ValueCallback<Uri[]> backupFilePathCallback;
    private String pendingBackupBase64 = null;
    private String pendingBackupName = "LaPrincesa_backup.json";
    private static final int BACKUP_IMPORT_REQUEST = 52;
    private static final int BACKUP_EXPORT_REQUEST = 53;
    private OutputStream out;
    private static final int PERMISSION_REQUEST = 41;
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private String lastMac = "";
    // Serialize all printer writes so receipt jobs and test/drawer commands cannot overlap.
    private final Object printerWriteLock = new Object();
    // Prevent duplicate receipt jobs while the off-screen WebView is rendering/sending.
    private final java.util.concurrent.atomic.AtomicBoolean receiptPrintInProgress =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Required for WebView.draw(Canvas) to include the full HTML document,
        // not just the currently visible viewport. Without this, long receipts
        // can lose sections from the middle when captured into the printer bitmap.
        WebView.enableSlowWholeDocumentDraw();

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

                // Only actual password prompts should mask what the user types.
                // Inventory names stay readable; stock and price prompts use a visible
                // numeric keyboard so the cashier can verify each value while entering it.
                String promptText = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
                boolean passwordPrompt = promptText.contains("password");
                boolean numericPrompt = promptText.contains("stock")
                    || promptText.contains("price")
                    || promptText.contains("presyo")
                    || promptText.contains("liters")
                    || promptText.contains("amount")
                    || promptText.contains("halagang binayad");

                if (passwordPrompt) {
                    input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                } else if (numericPrompt) {
                    input.setInputType(InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
                        | InputType.TYPE_NUMBER_FLAG_SIGNED);
                } else {
                    input.setInputType(InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                }

                if (defaultValue != null) input.setText(defaultValue);
                input.setSelection(input.getText().length());

                AlertDialog.Builder promptDialog = new AlertDialog.Builder(MainActivity.this)
                    .setTitle(passwordPrompt ? "Password Verification" : "La Princesa POS")
                    .setMessage(message)
                    .setView(input)
                    .setPositiveButton(passwordPrompt ? "Verify" : "OK", (d, which) ->
                        result.confirm(input.getText().toString()))
                    .setNegativeButton("Cancel", (d, which) -> result.cancel());
                AlertDialog dialog = promptDialog.create();
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

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                if (backupFilePathCallback != null) backupFilePathCallback.onReceiveValue(null);
                backupFilePathCallback = filePathCallback;
                try {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                        "application/json", "text/json", "application/octet-stream"
                    });
                    startActivityForResult(intent, BACKUP_IMPORT_REQUEST);
                    return true;
                } catch (Exception e) {
                    backupFilePathCallback = null;
                    filePathCallback.onReceiveValue(null);
                    Toast.makeText(MainActivity.this, "Hindi mabuksan ang file picker.", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new NativePrinter(), "NativePrinter");
        web.addJavascriptInterface(new NativeBackup(), "NativeBackup");

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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BACKUP_IMPORT_REQUEST) {
            if (backupFilePathCallback != null) {
                Uri[] result = (resultCode == RESULT_OK && data != null && data.getData() != null)
                    ? new Uri[] { data.getData() } : null;
                backupFilePathCallback.onReceiveValue(result);
                backupFilePathCallback = null;
            }
            return;
        }
        if (requestCode == BACKUP_EXPORT_REQUEST) {
            Uri uri = (resultCode == RESULT_OK && data != null) ? data.getData() : null;
            String encoded = pendingBackupBase64;
            pendingBackupBase64 = null;
            if (uri == null || encoded == null) {
                Toast.makeText(this, "Kinansela ang pag-save ng backup.", Toast.LENGTH_SHORT).show();
                return;
            }
            try (OutputStream stream = getContentResolver().openOutputStream(uri)) {
                if (stream == null) throw new IllegalStateException("Hindi mabuksan ang napiling lokasyon.");
                stream.write(Base64.decode(encoded, Base64.DEFAULT));
                stream.flush();
                Toast.makeText(this, "Matagumpay na na-save ang backup file.", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Hindi na-save ang backup: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private class NativeBackup {
        @JavascriptInterface
        public void saveBackup(String base64Data, String fileName) {
            if (base64Data == null || base64Data.isEmpty()) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Walang laman ang backup.", Toast.LENGTH_SHORT).show());
                return;
            }
            runOnUiThread(() -> {
                pendingBackupBase64 = base64Data;
                pendingBackupName = (fileName == null || fileName.trim().isEmpty())
                    ? "LaPrincesa_backup.json" : fileName.replaceAll("[^A-Za-z0-9._-]", "_");
                try {
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json");
                    intent.putExtra(Intent.EXTRA_TITLE, pendingBackupName);
                    startActivityForResult(intent, BACKUP_EXPORT_REQUEST);
                } catch (Exception e) {
                    pendingBackupBase64 = null;
                    Toast.makeText(MainActivity.this, "Hindi mabuksan ang Save dialog.", Toast.LENGTH_LONG).show();
                }
            });
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
            synchronized (printerWriteLock) {
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
        }

        @JavascriptInterface
        public boolean printHtmlBase64(String encodedHtml, int receiptCssWidth) {
            if (!receiptPrintInProgress.compareAndSet(false, true)) {
                showReceiptPrintError("May kasalukuyang ini-print. Hintaying matapos bago ulitin.");
                return false;
            }
            final String html;
            try {
                byte[] htmlBytes = Base64.decode(encodedHtml, Base64.DEFAULT);
                html = new String(htmlBytes, StandardCharsets.UTF_8);
            } catch (Exception e) {
                receiptPrintInProgress.set(false);
                showReceiptPrintError("Hindi mabasa ang formatted receipt.");
                return false;
            }

            runOnUiThread(() -> {
                final WebView renderer = new WebView(MainActivity.this);
                try {
                    WebSettings ws = renderer.getSettings();
                    ws.setJavaScriptEnabled(true);
                    ws.setDomStorageEnabled(false);
                    ws.setDefaultTextEncodingName("UTF-8");
                    ws.setUseWideViewPort(false);
                    ws.setLoadWithOverviewMode(false);
                    renderer.setBackgroundColor(Color.WHITE);
                    renderer.setVerticalScrollBarEnabled(false);
                    renderer.setHorizontalScrollBarEnabled(false);
                    renderer.setAlpha(1f);
                    renderer.setClickable(false);
                    renderer.setFocusable(false);
                    if (Build.VERSION.SDK_INT >= 16) renderer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

                    int density = Math.round(getResources().getDisplayMetrics().density * 1000f);
                    int cssWidth = Math.max(180, Math.min(420, receiptCssWidth));
                    int widthPx = Math.max(1, Math.round((float) cssWidth * density / 1000f));
                    int maxHeightPx = Math.round(30000f * density / 1000f);
                    ViewGroup decor = (ViewGroup) getWindow().getDecorView();
                    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(widthPx, maxHeightPx, Gravity.TOP | Gravity.LEFT);
                    params.leftMargin = -widthPx - 20;
                    decor.addView(renderer, params);

                    renderer.setWebViewClient(new WebViewClient() {
                        private boolean handled = false;
                        @Override
                        public void onPageFinished(WebView view, String url) {
                            if (handled) return;
                            handled = true;
                            view.evaluateJavascript("String(Math.max(document.body.scrollHeight,document.documentElement.scrollHeight))", value -> {
                                byte[] data = null;
                                String renderFailure = null;
                                Bitmap raster = null;
                                try {
                                    String clean = value == null ? "" : value.replace("\"", "").trim();
                                    int cssHeight = Integer.parseInt(clean);
                                    if (cssHeight <= 0) throw new IllegalStateException("empty receipt layout; JS height=" + clean);
                                    int heightPx = Math.max(1, Math.round(cssHeight * density / 1000f));
                                    int targetHeight = Math.max(1, Math.round(heightPx * 384f / widthPx));
                                    if (targetHeight > 30000) throw new IllegalStateException("Receipt is too long to render safely (" + targetHeight + " printer rows).");
                                    view.setLayoutParams(new FrameLayout.LayoutParams(widthPx, heightPx, Gravity.TOP | Gravity.LEFT));
                                    view.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY));
                                    view.layout(0, 0, widthPx, heightPx);
                                    raster = Bitmap.createBitmap(384, targetHeight, Bitmap.Config.ARGB_8888);
                                    Canvas canvas = new Canvas(raster);
                                    canvas.drawColor(Color.WHITE);
                                    canvas.scale(384f / widthPx, 384f / widthPx);
                                    view.draw(canvas);
                                    Bitmap centered = centerBitmapHorizontally(raster);
                                    if (centered != raster) { raster.recycle(); raster = centered; }
                                    data = bitmapToEscPos(raster);
                                } catch (OutOfMemoryError e) {
                                    renderFailure = "Kulang ang memory sa pag-render ng buong resibo.";
                                } catch (Exception e) {
                                    String detail = e.getMessage();
                                    renderFailure = "Render error: " + e.getClass().getSimpleName() + (detail == null || detail.isEmpty() ? "" : " — " + detail);
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
                                    receiptPrintInProgress.set(false);
                                    finishReceiptPrint(false, renderFailure == null ? "Walang laman ang render output. Subukan muli." : renderFailure);
                                    return;
                                }
                                new Thread(() -> {
                                    boolean success = false;
                                    String failure = "Nabigo ang pagpapadala ng resibo sa printer.";
                                    synchronized (printerWriteLock) {
                                        try {
                                            if (out == null && !connect(lastMac)) {
                                                failure = "Hindi makakonekta sa printer para sa resibo.";
                                            } else {
                                                final int chunkSize = 200;
                                                for (int offset = 0; offset < printBytes.length; offset += chunkSize) {
                                                    int length = Math.min(chunkSize, printBytes.length - offset);
                                                    out.write(printBytes, offset, length);
                                                    out.flush();
                                                    Thread.sleep(30);
                                                }
                                                out.flush();
                                                success = true;
                                                failure = "";
                                            }
                                        } catch (Exception e) {
                                            closePrinter();
                                        } finally {
                                            receiptPrintInProgress.set(false);
                                        }
                                    }
                                    finishReceiptPrint(success, failure);
                                }, "ReceiptPrintWorker").start();
                            });
                        }
                    });
                    renderer.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null);
                } catch (Exception e) {
                    receiptPrintInProgress.set(false);
                    try {
                        ViewGroup parent = (ViewGroup) renderer.getParent();
                        if (parent != null) parent.removeView(renderer);
                        renderer.destroy();
                    } catch (Exception ignored) {}
                    finishReceiptPrint(false, "Hindi masimulan ang pag-render ng resibo.");
                }
            });
            return true;
        }

        private void finishReceiptPrint(boolean success, String message) {
            runOnUiThread(() -> {
                if (!success && message != null && !message.isEmpty()) {
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                }
                try {
                    web.evaluateJavascript("if(window.__lpNativeReceiptDone){window.__lpNativeReceiptDone(" + success + ",\"" + jsonEscape(message == null ? "" : message) + "\");}", null);
                } catch (Exception ignored) {}
            });
        }

        private void showReceiptPrintError(String message) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
        }

        private Bitmap centerBitmapHorizontally(Bitmap source) {
            int width = source.getWidth();
            int height = source.getHeight();
            int left = width;
            int right = -1;

            // Find the visible receipt content, ignoring near-white anti-aliased pixels.
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int color = source.getPixel(x, y);
                    int luminance = (Color.red(color) * 299
                        + Color.green(color) * 587
                        + Color.blue(color) * 114) / 1000;
                    if (luminance < 245) {
                        if (x < left) left = x;
                        if (x > right) right = x;
                    }
                }
            }

            if (right < left) return source;
            int offset = ((width - 1 - right) - left) / 2;
            // Never crop any printed pixels when centering.
            offset = Math.max(-left, Math.min(width - 1 - right, offset));
            if (offset == 0) return source;

            Bitmap centered = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(centered);
            canvas.drawColor(Color.WHITE);
            canvas.drawBitmap(source, (float) offset, 0f, null);
            return centered;
        }

        private byte[] bitmapToEscPos(Bitmap bitmap) throws Exception {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();

            // Remove only the unused white tail below the last printed pixel.
            // This prevents the printer from feeding blank paper after the receipt,
            // while preserving every printed row (including content in the middle).
            int lastInkRow = -1;
            for (int y = height - 1; y >= 0 && lastInkRow < 0; y--) {
                for (int x = 0; x < width; x++) {
                    int color = bitmap.getPixel(x, y);
                    int luminance = (Color.red(color) * 299
                        + Color.green(color) * 587
                        + Color.blue(color) * 114) / 1000;
                    if (luminance < 180) {
                        lastInkRow = y;
                        break;
                    }
                }
            }
            if (lastInkRow < 0) throw new IllegalStateException("blank receipt image");
            // Retain a small bottom margin after the final printed content.
            height = Math.min(height, lastInkRow + 13);
            if (height > 65535) throw new IllegalStateException("receipt image is too long for ESC/POS");

            int widthBytes = (width + 7) / 8;
            final int bandRows = 256;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(8 + widthBytes * height + 8);
            bytes.write(new byte[] { 0x1B, 0x40 });
            for (int top = 0; top < height; top += bandRows) {
                int rows = Math.min(bandRows, height - top);
                // Each band is its own valid GS v 0 raster command. This is
                // safer for printers with small Bluetooth and image buffers.
                bytes.write(new byte[] {
                    0x1D, 0x76, 0x30, 0x00,
                    (byte) (widthBytes & 0xFF), (byte) ((widthBytes >> 8) & 0xFF),
                    (byte) (rows & 0xFF), (byte) ((rows >> 8) & 0xFF)
                });
                for (int y = top; y < top + rows; y++) {
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
            }
            // No extra feed command: the final band ends at the receipt's final margin.
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
