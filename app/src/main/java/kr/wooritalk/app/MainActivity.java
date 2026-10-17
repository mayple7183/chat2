package kr.wooritalk.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.net.http.SslError;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import javax.net.ssl.HttpsURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private static final int REQ_FILE = 4101;
    private static final int REQ_STORAGE = 4102;
    private static final int REQ_NOTIFICATIONS = 4103;
    private WebView webView;
    private ProgressBar progress;
    private ValueCallback<Uri[]> fileCallback;
    private PendingDownload pendingDownload;

    private static class PendingDownload {
        final String url,userAgent,contentDisposition,mime;
        PendingDownload(String u,String ua,String cd,String m){url=u;userAgent=ua;contentDisposition=cd;mime=m;}
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        NotificationHelper.ensureChannel(this);
        if (Build.VERSION.SDK_INT >= 26 && WebView.getCurrentWebViewPackage() == null) {
            new AlertDialog.Builder(this).setTitle("Android System WebView 필요")
                    .setMessage("Chrome은 차단되어 있어도 되지만 Android System WebView는 활성화되어 있어야 우리톡 화면을 표시할 수 있습니다.")
                    .setPositiveButton("확인", null).show();
        }
        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        root.addView(webView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 6);
        root.addView(progress, pp);
        setContentView(root);

        // 하단 메뉴가 시스템 내비게이션 버튼에 가려지지 않되,
        // 너무 위로 떠 보이지 않도록 내비게이션 영역의 약 절반만 여백으로 사용
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            int minGap = Math.round(12 * getResources().getDisplayMetrics().density);
            int bottomPadding = Math.max(minGap, nav.bottom / 2);
            v.setPadding(0, 0, 0, bottomPadding);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);

        configureWebView();
        fetchFirebaseConfigAndToken();
        String path = getIntent().getStringExtra("path");
        loadPath(path);
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(s.getUserAgentString()+" WooriTalkAndroid/1.8.4");
        if (Build.VERSION.SDK_INT >= 26) s.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
        webView.addJavascriptInterface(new NativeBridge(), "WooriNative");
        webView.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req){
                Uri u=req.getUrl();
                if ("https".equalsIgnoreCase(u.getScheme()) && "ydms3105.synology.me".equalsIgnoreCase(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW,u)); } catch (ActivityNotFoundException e) { Toast.makeText(MainActivity.this,"외부 링크를 열 앱이 없습니다.",Toast.LENGTH_SHORT).show(); }
                return true;
            }
            @Override public void onPageStarted(WebView view,String url,Bitmap icon){progress.setVisibility(ProgressBar.VISIBLE);}
            @Override public void onPageFinished(WebView view,String url){progress.setVisibility(ProgressBar.GONE);dispatchFcmReady();}
            @Override public void onReceivedSslError(WebView view,SslErrorHandler handler,SslError error){handler.cancel();Toast.makeText(MainActivity.this,"서버 인증서를 확인해주세요.",Toast.LENGTH_LONG).show();}
        });
        webView.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView view,int value){progress.setProgress(value);progress.setVisibility(value>=100?ProgressBar.GONE:ProgressBar.VISIBLE);}
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params){
                if(fileCallback!=null)fileCallback.onReceiveValue(null);
                fileCallback=cb;
                Intent intent=params.createIntent();
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                try{startActivityForResult(intent,REQ_FILE);return true;}catch(Exception e){fileCallback=null;Toast.makeText(MainActivity.this,"사진/파일 선택 앱을 열 수 없습니다.",Toast.LENGTH_SHORT).show();return false;}
            }
        });
        webView.setDownloadListener((url,ua,cd,mime,len)->startDownload(new PendingDownload(url,ua,cd,mime)));
    }

    private void loadPath(String path){
        if(path==null||path.isEmpty()||!path.startsWith("/"))path="/";
        webView.loadUrl(WooriApp.BASE_URL+path);
    }

    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);loadPath(intent.getStringExtra("path"));}

    @Override public void onBackPressed(){if(webView.canGoBack())webView.goBack();else super.onBackPressed();}

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQ_FILE&&fileCallback!=null){
            Uri[] result=null;
            if(resultCode==RESULT_OK&&data!=null){
                if(data.getClipData()!=null){int n=data.getClipData().getItemCount();result=new Uri[n];for(int i=0;i<n;i++)result[i]=data.getClipData().getItemAt(i).getUri();}
                else if(data.getData()!=null)result=new Uri[]{data.getData()};
            }
            fileCallback.onReceiveValue(result);fileCallback=null;
        }
    }

    private void startDownload(PendingDownload d){
        if(Build.VERSION.SDK_INT==28&&checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){pendingDownload=d;requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},REQ_STORAGE);return;}
        try{
            String name=URLUtil.guessFileName(d.url,d.contentDisposition,d.mime);
            DownloadManager.Request r=new DownloadManager.Request(Uri.parse(d.url));
            r.setTitle(name);r.setDescription("우리톡 파일 다운로드");r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            if(d.mime!=null&&!d.mime.isEmpty())r.setMimeType(d.mime);
            String cookie=CookieManager.getInstance().getCookie(d.url);if(cookie!=null)r.addRequestHeader("Cookie",cookie);
            if(d.userAgent!=null)r.addRequestHeader("User-Agent",d.userAgent);
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,name);
            DownloadManager dm=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);
            long id=dm.enqueue(r);
            getSharedPreferences("wooritalk_downloads",MODE_PRIVATE).edit()
                    .putString("mime_"+id,d.mime==null?"":d.mime)
                    .putString("name_"+id,name).apply();
            Toast.makeText(this,"Download 폴더에 저장합니다. 사진/영상은 완료 후 갤러리에도 등록됩니다.",Toast.LENGTH_SHORT).show();
        }catch(Exception e){Toast.makeText(this,"다운로드에 실패했습니다: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(requestCode,permissions,grants);
        if(requestCode==REQ_STORAGE&&pendingDownload!=null){PendingDownload d=pendingDownload;pendingDownload=null;if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)startDownload(d);else Toast.makeText(this,"저장소 권한이 필요합니다.",Toast.LENGTH_SHORT).show();}
        if(requestCode==REQ_NOTIFICATIONS)dispatchFcmReady();
    }

    private void fetchFirebaseConfigAndToken(){
        new Thread(()->{
            try{
                URL u=new URL(WooriApp.BASE_URL+"/api/mobile/config");
                HttpsURLConnection c=(HttpsURLConnection)u.openConnection();c.setConnectTimeout(10000);c.setReadTimeout(10000);c.setRequestMethod("GET");
                BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream()));StringBuilder sb=new StringBuilder();String line;while((line=br.readLine())!=null)sb.append(line);br.close();
                JSONObject j=new JSONObject(sb.toString());
                if(j.optBoolean("enabled",false)){
                    WooriApp.saveFirebaseConfig(this,j.getString("application_id"),j.getString("api_key"),j.getString("project_id"),j.getString("sender_id"));
                }
                runOnUiThread(this::refreshFcmToken);
            }catch(Exception ignored){runOnUiThread(this::refreshFcmToken);}
        }).start();
    }

    private void refreshFcmToken(){
        if(!WooriApp.initFirebaseFromPrefs(this))return;
        try{
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task->{
                if(task.isSuccessful()&&task.getResult()!=null&&!task.getResult().isEmpty()){
                    WooriApp.prefs(this).edit().putString("fcm_token",task.getResult()).putString("fcm_error","").apply();
                    dispatchFcmReady();
                }else{
                    String msg = task.getException()==null ? "FCM token empty" : task.getException().toString();
                    WooriApp.prefs(this).edit().putString("fcm_error",msg).apply();
                    dispatchFcmReady();
                }
            });
        }catch(Exception e){WooriApp.prefs(this).edit().putString("fcm_error",e.toString()).apply();dispatchFcmReady();}
    }

    private void dispatchFcmReady(){
        if(webView==null)return;
        webView.post(()->webView.evaluateJavascript("window.dispatchEvent(new Event('woori-fcm-ready'));",null));
    }

    private void requestNotificationPermissionIfNeeded(){
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIFICATIONS);}
    }

    public class NativeBridge {
        @JavascriptInterface public String getFcmToken(){return WooriApp.prefs(MainActivity.this).getString("fcm_token","");}
        @JavascriptInterface public String getFcmError(){return WooriApp.prefs(MainActivity.this).getString("fcm_error","");}
        @JavascriptInterface public boolean isNotificationsEnabled(){return WooriApp.prefs(MainActivity.this).getBoolean("notifications_enabled",false);}
        @JavascriptInterface public void enableNotifications(){
            WooriApp.prefs(MainActivity.this).edit().putBoolean("notifications_enabled",true).apply();
            runOnUiThread(()->{requestNotificationPermissionIfNeeded();fetchFirebaseConfigAndToken();});
        }
        @JavascriptInterface public void disableNotifications(){WooriApp.prefs(MainActivity.this).edit().putBoolean("notifications_enabled",false).apply();}
        @JavascriptInterface public void showLocalNotification(String title,String body){if(!isNotificationsEnabled())return;runOnUiThread(()->NotificationHelper.show(MainActivity.this,title,body,"/"));}
        @JavascriptInterface public String getAppVersion(){return "1.8.4";}
    }
}
