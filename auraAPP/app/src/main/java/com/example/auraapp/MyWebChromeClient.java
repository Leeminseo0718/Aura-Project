package com.example.auraapp;

import android.app.Activity;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.content.Intent;
import android.net.Uri;

import java.util.Arrays;

public class MyWebChromeClient extends WebChromeClient {
    private WebView webView;
    private final UrlPolicy urlPolicy;
    public ValueCallback<Uri[]> filePathCallback;

    public MyWebChromeClient(WebView webView, UrlPolicy urlPolicy) {
        this.webView = webView;
        this.urlPolicy = urlPolicy;
    }

    // 마이크 권한 요청: 프론트엔드 페이지가 마이크를 요청한 경우에만 허용
    @Override
    public void onPermissionRequest(final PermissionRequest request) {
        Activity activity = (Activity) webView.getContext();
        activity.runOnUiThread(() -> {
            boolean wantsAudio = Arrays.asList(request.getResources())
                    .contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE);
            if (wantsAudio && urlPolicy.isTrusted(request.getOrigin())) {
                request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            } else {
                request.deny();
            }
        });
    }

    // 파일 선택
    @Override
    public boolean onShowFileChooser(WebView webView,
                                     ValueCallback<Uri[]> filePathCallback,
                                     FileChooserParams fileChooserParams) {
        this.filePathCallback = filePathCallback;
        try {
            Intent intent = fileChooserParams.createIntent();
            ((Activity) webView.getContext()).startActivityForResult(intent, 1000);
        } catch (Exception e) {
            this.filePathCallback = null;
            return false;
        }
        return true;
    }

    public void resetFilePathCallback() {
        filePathCallback = null;
    }
}
