package com.cinehub.tv;

import android.app.DownloadManager;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;
import androidx.webkit.WebViewAssetLoader;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.*;

public class MainActivity extends AppCompatActivity {
    private static final String UPDATE_URL="https://raw.githubusercontent.com/mooNXy7/CineHUB-TV/main/update.json";
    private static final int CURRENT_VERSION_CODE=128;
    private final ExecutorService updateExecutor=Executors.newSingleThreadExecutor();
    private final Handler mainHandler=new Handler(Looper.getMainLooper());
    private long pendingDownloadId=-1;
    private WebView webView; private WebViewAssetLoader assetLoader;
    private View fullscreenView; private WebChromeClient.CustomViewCallback fullscreenCallback;
    private final BroadcastReceiver downloadReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){long id=i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID,-1);if(id!=pendingDownloadId)return;Uri u=((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).getUriForDownloadedFile(id);if(u!=null)installApk(u);}
    };
    @Override protected void onCreate(@Nullable Bundle b){
        super.onCreate(b);requestWindowFeature(Window.FEATURE_NO_TITLE);setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);hideSystemUi();webView=findViewById(R.id.webview);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE,null);configureWebView();
        assetLoader=new WebViewAssetLoader.Builder().addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this)).build();
        webView.loadUrl("https://appassets.androidplatform.net/assets/cinehub/index.html?tv=1");registerDownloadReceiver();checkForUpdate();
    }
    private void registerDownloadReceiver(){IntentFilter f=new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);if(Build.VERSION.SDK_INT>=33)registerReceiver(downloadReceiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(downloadReceiver,f);}
    private void configureWebView(){
        WebSettings s=webView.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setDatabaseEnabled(true);s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);s.setAllowContentAccess(true);s.setAllowFileAccessFromFileURLs(false);s.setAllowUniversalAccessFromFileURLs(false);s.setSupportZoom(false);s.setBuiltInZoomControls(false);s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);s.setUserAgentString(s.getUserAgentString()+" CineHUB-TV/1.2.8");
        if(WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK))WebSettingsCompat.setForceDark(s,WebSettingsCompat.FORCE_DARK_OFF);
        webView.setBackgroundColor(Color.BLACK);webView.setFocusable(true);webView.requestFocus();if(Build.VERSION.SDK_INT>=26)webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT,true);
        webView.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){return assetLoader!=null?assetLoader.shouldInterceptRequest(r.getUrl()):super.shouldInterceptRequest(v,r);}
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,String u){return assetLoader!=null?assetLoader.shouldInterceptRequest(Uri.parse(u)):super.shouldInterceptRequest(v,u);}
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){Uri u=r.getUrl();String scheme=u.getScheme();if("http".equalsIgnoreCase(scheme)||"https".equalsIgnoreCase(scheme)){String h=u.getHost();if(h==null||"appassets.androidplatform.net".equalsIgnoreCase(h))return false;try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(ActivityNotFoundException ignored){}return true;}return false;}
            @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail d){try{ViewGroup root=findViewById(R.id.root);root.removeView(v);WebView n=new WebView(MainActivity.this);n.setId(R.id.webview);n.setLayoutParams(new ViewGroup.LayoutParams(-1,-1));n.setBackgroundColor(Color.BLACK);n.setLayerType(View.LAYER_TYPE_HARDWARE,null);root.addView(n);webView=n;configureWebView();n.loadUrl("https://appassets.androidplatform.net/assets/cinehub/index.html?tv=1");}catch(Exception e){finish();}return true;}
        });
        webView.setWebChromeClient(new WebChromeClient(){@Override public void onShowCustomView(View v,CustomViewCallback c){enterFullscreen(v,c);}@Override public void onHideCustomView(){exitFullscreen();}});
        webView.setDownloadListener((u,ua,cd,mime,len)->enqueueDownload(u,ua,mime));webView.setOnLongClickListener(v->true);
    }
    private byte[] readAll(InputStream in)throws IOException{ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)o.write(b,0,n);return o.toByteArray();}
    private void checkForUpdate(){updateExecutor.execute(()->{HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(UPDATE_URL).openConnection();c.setConnectTimeout(5000);c.setReadTimeout(5000);c.setRequestProperty("User-Agent","CineHUB-TV/"+CURRENT_VERSION_CODE);try(InputStream in=c.getInputStream()){JSONObject j=new JSONObject(new String(readAll(in),java.nio.charset.StandardCharsets.UTF_8));int code=j.optInt("versionCode",CURRENT_VERSION_CODE);String url=j.optString("apkUrl","");if(code>CURRENT_VERSION_CODE&&!url.isEmpty())mainHandler.post(()->enqueueUpdate(url,j.optString("version","nova versão")));}}catch(Exception ignored){}finally{if(c!=null)c.disconnect();}});}
    private void enqueueUpdate(String url,String version){try{DownloadManager.Request r=new DownloadManager.Request(Uri.parse(url));r.setTitle("CineHUB TV "+version);r.setDescription("Baixando atualização");r.setMimeType("application/vnd.android.package-archive");r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);r.setDestinationInExternalFilesDir(this,Environment.DIRECTORY_DOWNLOADS,"CineHUB-TV-update.apk");pendingDownloadId=((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(r);Toast.makeText(this,"Nova versão encontrada. Download iniciado.",Toast.LENGTH_LONG).show();}catch(Exception ignored){}}
    private void installApk(Uri uri){try{Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(uri,"application/vnd.android.package-archive");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);}catch(Exception e){Toast.makeText(this,"Atualização baixada. Abra o APK em Downloads para instalar.",Toast.LENGTH_LONG).show();}}
    private void enqueueDownload(String u,String ua,String mime){try{DownloadManager.Request r=new DownloadManager.Request(Uri.parse(u));r.setTitle("CineHUB TV");r.setDescription("Download iniciado");if(mime!=null)r.setMimeType(mime);if(ua!=null)r.addRequestHeader("User-Agent",ua);r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,"CineHUB-"+System.currentTimeMillis());((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(r);Toast.makeText(this,"Download iniciado",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"Não foi possível iniciar o download",Toast.LENGTH_SHORT).show();}}
    private void enterFullscreen(View v,WebChromeClient.CustomViewCallback c){if(fullscreenView!=null){c.onCustomViewHidden();return;}fullscreenView=v;fullscreenCallback=c;webView.setVisibility(View.GONE);((ViewGroup)findViewById(R.id.root)).addView(v,new ViewGroup.LayoutParams(-1,-1));hideSystemUi();}
    private void exitFullscreen(){if(fullscreenView==null)return;((ViewGroup)findViewById(R.id.root)).removeView(fullscreenView);fullscreenView=null;if(fullscreenCallback!=null){fullscreenCallback.onCustomViewHidden();fullscreenCallback=null;}webView.setVisibility(View.VISIBLE);webView.requestFocus();showSystemUi();}
    private void hideSystemUi(){getWindow().getDecorView().setSystemUiVisibility(5894);}private void showSystemUi(){getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);}
    @Override public boolean dispatchKeyEvent(KeyEvent e){if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getKeyCode()==KeyEvent.KEYCODE_BACK){handleBack();return true;}return super.dispatchKeyEvent(e);}
    @Override public void onBackPressed(){handleBack();}
    private void handleBack(){if(fullscreenView!=null){exitFullscreen();return;}if(webView==null){super.onBackPressed();return;}webView.evaluateJavascript("(function(){try{return !!(window.CineHUBTV&&window.CineHUBTV.back&&window.CineHUBTV.back());}catch(e){return false;}})();",v->{if("false".equals(v))MainActivity.super.onBackPressed();});}
    @Override protected void onDestroy(){try{unregisterReceiver(downloadReceiver);}catch(Exception ignored){}updateExecutor.shutdownNow();super.onDestroy();}
}