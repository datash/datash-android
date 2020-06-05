package io.rousan.datash;

import android.webkit.JavascriptInterface;

import timber.log.Timber;

public class JavaScriptInterface {
    MainActivity mainActivity;

    public JavaScriptInterface(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }

    @JavascriptInterface
    public void ping() {
    }

    @JavascriptInterface
    public void onStartFileDownload(String refId, String fromId, String fileName, String size, String mimeType) {
        mainActivity.onStartFileDownload(refId, fromId, fileName, Long.parseLong(size), mimeType);
    }

    @JavascriptInterface
    public void onCompleteFileDownload(String refId, String base64Data) {
        mainActivity.onCompleteFileDownload(refId, base64Data);
    }
}
