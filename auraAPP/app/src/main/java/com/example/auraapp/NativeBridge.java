package com.example.auraapp;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.CallLog;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Date;

//네이티브 브릿지 설정
public class NativeBridge {
    private final Context ctx;
    private final WebView webView;

    // 현재 페이지가 프론트엔드(신뢰하는 origin)인지. MainActivity가 페이지 이동 때마다 갱신
    // addJavascriptInterface는 어떤 페이지에서나 호출되므로 모든 메서드에서 확인해야 함
    private volatile boolean trustedPage = false;

    public NativeBridge(Context ctx, WebView webView) {
        this.ctx = ctx;
        this.webView = webView;
    }

    public void setTrustedPage(boolean trusted) {
        this.trustedPage = trusted;
    }

    public boolean isTrustedPage() {
        return trustedPage;
    }

    // 비동기 방식으로 네이티브에서 JS 함수 호출하여 전달 (권장: 대용량/비동기)
    @JavascriptInterface
    public void requestContacts() {
        if (!trustedPage) return;
        new Thread(() -> {
            final String json = buildContactsJson();
            // runOnUiThread 필요
            ((AppCompatActivity)ctx).runOnUiThread(() -> {
                // 조회하는 동안 다른 사이트로 이동했으면 전달하지 않음
                if (!trustedPage) return;
                // 웹 쪽에 함수가 있다면 호출
                webView.evaluateJavascript("window.populateContacts && window.populateContacts(" + JSONObject.quote(json) + ")", null);
                // 또는: window.populateContacts(JSON.parse(<json>))
                // webView.evaluateJavascript("window.populateContacts(JSON.parse(" + JSONObject.quote(json) + "));", null);
            });
        }).start();
    }

    private String buildContactsJson() {
        JSONArray arr = new JSONArray();
        Cursor c = ctx.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[] {
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                },
                null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        );
        if (c != null) {
            while (c.moveToNext()) {
                String name = c.getString(0);
                String number = c.getString(1);
                JSONObject o = new JSONObject();
                try { o.put("name", name); o.put("phoneNumber", number); } catch (Exception e){}
                arr.put(o);
            }
            c.close();
        }
        return arr.toString();
    }

    @JavascriptInterface
    public void requestRecentCalls() {
        if (!trustedPage) return;
        new Thread(() -> {
            final String json = buildRecentCallsJson();
            ((AppCompatActivity)ctx).runOnUiThread(() -> {
                if (!trustedPage) return;
                webView.evaluateJavascript("window.populateRecentCalls && window.populateRecentCalls(" + JSONObject.quote(json) + ")", null);
            });
        }).start();
    }

    private String buildRecentCallsJson() {
        JSONArray arr = new JSONArray();
        Cursor c = ctx.getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                null,
                null,
                null,
                CallLog.Calls.DATE + " DESC"
        );
        if (c != null) {
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                try {
                    String number = c.getString(c.getColumnIndexOrThrow(CallLog.Calls.NUMBER));
                    String type = c.getString(c.getColumnIndexOrThrow(CallLog.Calls.TYPE));
                    long date = c.getLong(c.getColumnIndexOrThrow(CallLog.Calls.DATE));
                    int duration = c.getInt(c.getColumnIndexOrThrow(CallLog.Calls.DURATION));
                    o.put("phoneNumber", number);
                    o.put("startTime", date);
                    o.put("duration", duration);
                    o.put("type", type);
                } catch (Exception e) {}
                arr.put(o);
            }
            c.close();
        }
        return arr.toString();
    }

    @JavascriptInterface
    public void makeCall(String phoneNumber) {
        if (!trustedPage) return;
        // 전화 걸기 (CALL_PHONE 권한 필요)
        try {
            Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + phoneNumber));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } catch (Exception e) {
            // 실패시 JS로 실패 알림
            ((AppCompatActivity)ctx).runOnUiThread(() -> {
                webView.evaluateJavascript("window.onCallResult && window.onCallResult(false, " + JSONObject.quote(e.getMessage()) + ")", null);
            });
        }
    }
}

