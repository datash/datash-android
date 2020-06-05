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
    public void onStartFileDownload(final String refId, final String fromId, final String fileName, final String size, final String mimeType) {
        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mainActivity.onStartFileDownload(refId, fromId, fileName, Long.parseLong(size), mimeType);
            }
        });
    }

    @JavascriptInterface
    public void onCompleteFileDownload(final String refId, final String base64Data) {
        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mainActivity.onCompleteFileDownload(refId, base64Data);
            }
        });
    }

    @JavascriptInterface
    public void onWebAppMount() {
        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mainActivity.onWebAppMount();
            }
        });
    }
}
