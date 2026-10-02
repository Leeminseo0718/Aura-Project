package com.example.auraapp;

import android.net.Uri;

import java.util.Locale;

// WebView에서 열 수 있는 주소와 네이티브 기능(연락처·통화기록·전화·마이크)을 허용할 주소를 판단
public class UrlPolicy {

    // 소셜 로그인 페이지는 앱 안에서 열어야 로그인 후 프론트엔드 콜백으로 돌아옴
    // (로그인 도중 외부 브라우저가 열리면 그 도메인을 여기에 추가)
    private static final String[] OAUTH_HOST_SUFFIXES = {"google.com", "naver.com", "kakao.com"};
    // WebView에서 열지 않는 주소 중 외부 앱(브라우저, 전화, 메일)으로 넘겨도 되는 scheme
    private static final String[] EXTERNAL_SCHEMES = {"http", "https", "tel", "mailto"};

    private final Uri frontend;

    public UrlPolicy(String frontendUrl) {
        this.frontend = Uri.parse(frontendUrl);
    }

    // 프론트엔드와 같은 origin(scheme + host + port)인지 확인 → 네이티브 기능 허용 기준
    public boolean isTrusted(Uri uri) {
        if (uri == null || uri.getHost() == null || frontend.getHost() == null) return false;
        return equalsIgnoreCase(frontend.getScheme(), uri.getScheme())
                && frontend.getHost().equalsIgnoreCase(uri.getHost())
                && portOf(frontend) == portOf(uri);
    }

    public boolean isTrusted(String url) {
        return url != null && isTrusted(Uri.parse(url));
    }

    // WebView 안에서 열어도 되는 주소: 프론트엔드 + 소셜 로그인(https)
    public boolean canOpenInWebView(Uri uri) {
        if (isTrusted(uri)) return true;
        if (uri == null || uri.getHost() == null || !"https".equalsIgnoreCase(uri.getScheme())) return false;

        String host = uri.getHost().toLowerCase(Locale.ROOT);
        for (String suffix : OAUTH_HOST_SUFFIXES) {
            if (host.equals(suffix) || host.endsWith("." + suffix)) return true;
        }
        return false;
    }

    public boolean canOpenExternally(Uri uri) {
        if (uri == null || uri.getScheme() == null) return false;
        for (String scheme : EXTERNAL_SCHEMES) {
            if (scheme.equalsIgnoreCase(uri.getScheme())) return true;
        }
        return false;
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        return a != null && a.equalsIgnoreCase(b);
    }

    private static int portOf(Uri uri) {
        if (uri.getPort() != -1) return uri.getPort();
        if ("https".equalsIgnoreCase(uri.getScheme())) return 443;
        if ("http".equalsIgnoreCase(uri.getScheme())) return 80;
        return -1;
    }
}
