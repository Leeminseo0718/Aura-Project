package com.example.auraapp;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_PERMISSIONS = 1001;
    // 앱이 띄울 프론트엔드 주소 (예: https://aura.example.com). 이 origin에만 네이티브 기능을 허용
    private static final String FRONTEND_URL = "[본인의 프론트엔드 주소]";
    private final UrlPolicy urlPolicy = new UrlPolicy(FRONTEND_URL);
    private WebView webView;
    private NativeBridge nativeBridge;
    private TelephonyManager telephonyManager;
    private MyPhoneStateListener phoneStateListener;
    private MyWebChromeClient chromeClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. 액션바 숨기기
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        // 2. 상태바 숨기기 (풀스크린)
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);

        // 전화 상태 리스너 등록
        telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        phoneStateListener = new MyPhoneStateListener(webView);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_PHONE_STATE}, REQ_PERMISSIONS);
        }

        // WebView 설정
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setMediaPlaybackRequiresUserGesture(false); // 중요

        // 쿠키 허용
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        // ChromeClient 설정 (마이크 권한 처리 포함, 프론트엔드에만 허용)
        chromeClient = new MyWebChromeClient(webView, urlPolicy);
        webView.setWebChromeClient(chromeClient);

        // NativeBridge (프론트엔드 페이지에서만 동작)
        nativeBridge = new NativeBridge(this, webView);
        webView.addJavascriptInterface(nativeBridge, "Android");

        // WebViewClient
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                // 프론트엔드와 소셜 로그인 페이지만 WebView에서 열기
                if (urlPolicy.canOpenInWebView(uri)) return false;
                // 그 외 주소(외부 링크, tel: 등)는 WebView에서 열지 않고 외부 앱으로 넘김
                if (request.isForMainFrame() && urlPolicy.canOpenExternally(uri)) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    } catch (ActivityNotFoundException ignored) {
                    }
                }
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                // 페이지가 바뀔 때마다 네이티브 기능 허용 여부 갱신
                nativeBridge.setTrustedPage(urlPolicy.isTrusted(url));
                super.onPageStarted(view, url, favicon);
            }
        });

        // User-Agent 수정
        String ua = webSettings.getUserAgentString();
        webSettings.setUserAgentString(ua + " Chrome/114.0.5735.196 Mobile Safari/537.36");

        // WebView 디버깅 (디버그 빌드에서만)
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        // URL 로드
        webView.loadUrl(FRONTEND_URL);

        // 런타임 권한 체크
        checkAndRequestPermissions();

        // 뒤로가기 처리
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack();
                else finish();
            }
        });
    }

    private void checkAndRequestPermissions() {
        String[] perms = new String[] {
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.RECORD_AUDIO, // 마이크 권한 추가
                Manifest.permission.MODIFY_AUDIO_SETTINGS
        };
        boolean need = false;
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                need = true; break;
            }
        }
        if (need) ActivityCompat.requestPermissions(this, perms, REQ_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSIONS) {
            for (int i = 0; i < permissions.length; i++) {
                if ((permissions[i].equals(Manifest.permission.READ_PHONE_STATE) ||
                        permissions[i].equals(Manifest.permission.RECORD_AUDIO)) &&
                        grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    if (permissions[i].equals(Manifest.permission.READ_PHONE_STATE)) {
                        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
                    }
                }
            }
        }
    }

    // 전화 수신 처리
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingCall(intent);
    }

    private void handleIncomingCall(Intent intent) {
        String incomingNumber = intent.getStringExtra("incomingNumber");
        if (incomingNumber != null) {
            webView.post(() -> {
                // 수신 번호는 프론트엔드 페이지에만 전달
                if (!nativeBridge.isTrustedPage()) return;
                webView.evaluateJavascript(
                        "window.onIncomingCall && window.onIncomingCall(" + JSONObject.quote(incomingNumber) + ");",
                        null
                );
            });
        }
    }

    // 파일 선택 결과 처리
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1000 && chromeClient != null) {
            ValueCallback<Uri[]> callback = chromeClient.filePathCallback;
            if (callback != null) {
                Uri[] results = null;
                if (resultCode == RESULT_OK && data != null) {
                    if (data.getData() != null) results = new Uri[]{ data.getData() };
                    else if (data.getClipData() != null) {
                        int count = data.getClipData().getItemCount();
                        results = new Uri[count];
                        for (int i = 0; i < count; i++) results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                }

                try { callback.onReceiveValue(results); }
                catch (Exception e) { e.printStackTrace(); }
                finally { chromeClient.resetFilePathCallback(); }
            }
        }
    }
}
