package com.laprincesa.pos;
import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
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
import java.io.OutputStream;
import java.util.UUID;
public class MainActivity extends AppCompatActivity {
  WebView web;
  BluetoothSocket socket;
  OutputStream out;
  static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
  String lastMac = "";
  @SuppressLint("SetJavaScriptEnabled")
  @Override protected void onCreate(Bundle b){
    super.onCreate(b);
    web = new WebView(this); setContentView(web);
    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setAllowFileAccess(true); s.setDatabaseEnabled(true);
    web.setWebChromeClient(new WebChromeClient());
    web.setWebViewClient(new WebViewClient());
    web.addJavascriptInterface(new NativePrinter(), "NativePrinter");
    web.loadUrl("https://nhabzgl-design.github.io/laprincesapos/");
    askPerms();
  }
  void askPerms(){
    if(Build.VERSION.SDK_INT>=31){
      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION}, 1);
    } else {
      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
    }
  }
  public class NativePrinter {
    @JavascriptInterface public String listPrinters(){
      try{
        BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
        if(ad==null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        boolean first=true;
        for(BluetoothDevice d: ad.getBondedDevices()){
          if(!first) sb.append(",");
          sb.append("{\"name\":\"").append(d.getName()).append("\",\"mac\":\"").append(d.getAddress()).append("\"}");
          first=false;
        }
        sb.append("]"); return sb.toString();
      }catch(Exception e){ return "[]"; }
    }
    @JavascriptInterface public boolean connect(String mac){
      try{
        BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
        if(ad==null) return false;
        BluetoothDevice dev = null;
        if(mac!=null && !mac.isEmpty()) dev = ad.getRemoteDevice(mac);
        else {
          for(BluetoothDevice d: ad.getBondedDevices()){
            String n = d.getName()!=null?d.getName():"";
            if(n.contains("XP")||n.contains("POS")||n.contains("Printer")||n.contains("58")){ dev=d; break; }
          }
          if(dev==null && !ad.getBondedDevices().isEmpty()) dev = ad.getBondedDevices().iterator().next();
        }
        if(dev==null) return false;
        if(socket!=null){ try{socket.close();}catch(Exception ignored){} }
        socket = dev.createRfcommSocketToServiceRecord(SPP);
        ad.cancelDiscovery();
        socket.connect();
        out = socket.getOutputStream();
        lastMac = dev.getAddress();
        return true;
      }catch(Exception e){ e.printStackTrace(); return false; }
    }
    @JavascriptInterface public boolean printBase64(String b64){
      try{
        if(out==null){ if(!connect(lastMac)) return false; }
        byte[] data = Base64.decode(b64, Base64.DEFAULT);
        int CH=200;
        for(int i=0;i<data.length;i+=CH){
          int len = Math.min(CH, data.length-i);
          out.write(data, i, len); out.flush();
          Thread.sleep(30);
        }
        return true;
      }catch(Exception e){ e.printStackTrace(); return false; }
    }
    @JavascriptInterface public boolean drawer(){
      return printBase64(Base64.encodeToString(new byte[]{0x1B,0x70,0x00,0x3C,(byte)0xFF}, Base64.NO_WRAP));
    }
    @JavascriptInterface public void toast(String m){ runOnUiThread(()-> Toast.makeText(MainActivity.this, m, Toast.LENGTH_SHORT).show()); }
  }
      }
