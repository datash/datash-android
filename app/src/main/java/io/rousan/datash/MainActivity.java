package io.rousan.datash;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.FileProvider;
import im.delight.android.webview.AdvancedWebView;
import timber.log.Timber;

import com.google.android.material.snackbar.Snackbar;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.listener.DexterError;
import com.karumi.dexter.listener.PermissionRequestErrorListener;
import com.karumi.dexter.listener.multi.DialogOnAnyDeniedMultiplePermissionsListener;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.view.View;
import android.webkit.WebSettings;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MainActivity extends AppCompatActivity implements AdvancedWebView.Listener {
    private AdvancedWebView webView;
    private HashMap<String, HashMap<String, String>> fileDownloadsMap = new HashMap<>();
    private ExecutorService worker;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestPermissions();

        worker = Executors.newFixedThreadPool(2);

        webView = findViewById(R.id.webview);

        webView.addJavascriptInterface(new JavaScriptInterface(this), "Android");

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(true);

        webView.setListener(this, this);
        webView.loadUrl(Constants.DATASH_BASE_URL, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onPause() {
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        webView.removeJavascriptInterface("Android");
        webView.onDestroy();
        super.onDestroy();
        worker.shutdown();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        webView.onActivityResult(requestCode, resultCode, intent);
    }

    @Override
    public void onBackPressed() {
        if (!webView.onBackPressed()) {
            return;
        }
        super.onBackPressed();
    }

    public void showSnackbar(String msg, boolean isSuccess) {
        if (isSuccess) {
            Snackbar.make(findViewById(android.R.id.content), msg, Snackbar.LENGTH_SHORT)
                    .show();
        } else {
            final Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content), msg, Snackbar.LENGTH_INDEFINITE);
            snackbar.setAction("Close", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    snackbar.dismiss();
                }
            });
            snackbar.show();
        }
    }

    private void requestPermissions() {
        MultiplePermissionsListener dialogMultiplePermissionsListener =
                DialogOnAnyDeniedMultiplePermissionsListener.Builder
                        .withContext(this)
                        .withTitle("Read & Write Storage Permission")
                        .withMessage("Both Read and Write Storage permissions are needed to read and save files during sending/receiving.")
                        .withButtonText(android.R.string.ok)
                        .withIcon(R.drawable.ic_launcher)
                        .build();

        Dexter.withContext(this)
                .withPermissions(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                ).withListener(dialogMultiplePermissionsListener)
                .onSameThread()
                .withErrorListener(new PermissionRequestErrorListener() {
                    @Override
                    public void onError(DexterError error) {
                        Timber.d("%s", error.toString());
                    }
                })
                .check();
    }

    public void onStartFileDownload(String refId, String fromId, String fileName, long size, String mimeType) {
        Timber.d("onStartFileDownload: %s %s %s %d", refId, fromId, fileName, size);

        HashMap<String, String> attr = new HashMap<>();
        attr.put("fromId", fromId);
        attr.put("fileName", fileName);
        attr.put("size", size + "");
        attr.put("mimeType", mimeType);
        fileDownloadsMap.put(refId, attr);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, Constants.NOTIF_CHANNEL_DOWNLOAD_FILE);
        builder.setContentTitle("File Download")
                .setContentText(fileName)
                .setSmallIcon(R.drawable.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setProgress(0, 0, true)
                .setOngoing(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(refId.hashCode(), builder.build());
    }

    public void onCompleteFileDownload(final String refId, final String base64Data) {
        Timber.d("onCompleteFileDownload: %s %s", refId, base64Data);

        final HashMap<String, String> attr = fileDownloadsMap.get(refId);
        worker.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String fileName = attr.get("fileName");
                    String fileExt = FilenameUtils.getExtension(fileName);
                    String fileNameWithoutExt;
                    if (fileExt.isEmpty()) {
                        fileNameWithoutExt = fileName;
                    } else {
                        fileNameWithoutExt = fileName.replace("." + fileExt, "");
                    }

                    File downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsoluteFile();
                    int counter = 0;
                    String newFileName;
                    while (true) {
                        if (counter == 0) {
                            newFileName = fileName;
                        } else {
                            if (fileExt.isEmpty()) {
                                newFileName = String.format("%s%s", fileNameWithoutExt, counter + "");
                            } else {
                                newFileName = String.format("%s%s.%s", fileNameWithoutExt, counter + "", fileExt);
                            }
                        }

                        File file = new File(downloadsFolder, newFileName);
                        if (file.exists()) {
                            counter += 1;
                        } else {
                            break;
                        }
                    }

                    if (!downloadsFolder.exists()) {
                        FileUtils.forceMkdir(downloadsFolder);
                    }

                    final File outputFile = new File(downloadsFolder, newFileName);
                    outputFile.createNewFile();
                    byte[] data = Base64.decode(base64Data, Base64.DEFAULT);
                    FileUtils.writeByteArrayToFile(outputFile, data);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Intent openFileIntent = new Intent(Intent.ACTION_VIEW);
                            openFileIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            openFileIntent.setDataAndType(
                                    FileProvider.getUriForFile(MainActivity.this, getApplicationContext().getPackageName() + ".provider", outputFile),
                                    attr.get("mimeType")
                            );
                            openFileIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                            NotificationCompat.Builder builder = new NotificationCompat.Builder(MainActivity.this, Constants.NOTIF_CHANNEL_DOWNLOAD_FILE);
                            builder.setContentTitle("File Download")
                                    .setContentText(String.format("Download complete: %s", outputFile.getName()))
                                    .setSmallIcon(R.drawable.ic_launcher)
                                    .setPriority(NotificationCompat.PRIORITY_LOW)
                                    .setProgress(0, 0, false)
                                    .setContentIntent(PendingIntent.getActivity(MainActivity.this, 0, openFileIntent, 0))
                                    .setAutoCancel(true)
                                    .setOngoing(false);

                            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(MainActivity.this);
                            notificationManager.notify(refId.hashCode(), builder.build());

                            showSnackbar(String.format("Download complete: %s", outputFile.getName()), true);
                        }
                    });
                } catch (Exception exp) {
                    Timber.d(exp);
                    showSnackbar(exp.getMessage(), false);
                }
            }
        });
    }

    private void injectJs() {
        try {
            InputStream inputStream = getAssets().open("script.js");
            byte[] buffer = new byte[inputStream.available()];
            inputStream.read(buffer);
            inputStream.close();

            String encoded = Base64.encodeToString(buffer, Base64.NO_WRAP);
            webView.loadUrl("javascript:(function() {" +
                    "var parent = document.getElementsByTagName('head').item(0);" +
                    "var script = document.createElement('script');" +
                    "script.type = 'text/javascript';" +
                    "script.innerHTML = window.atob('" + encoded + "');" +
                    "parent.appendChild(script)" +
                    "})()");
        } catch (Exception e) {
            Timber.d(e);
            showSnackbar(e.toString(), false);
        }
    }

    @Override
    public void onPageStarted(String url, Bitmap favicon) {
        Timber.d("Page loading started");
    }

    @Override
    public void onPageFinished(String url) {
        Timber.d("Page loading finished");
        injectJs();
    }

    @Override
    public void onPageError(int errorCode, String description, String failingUrl) {
        Timber.d("Page error: %d, %s, %s", errorCode, description, failingUrl);
        showSnackbar(String.format("Page error: %s", description), false);
    }

    @Override
    public void onDownloadRequested(String url, String suggestedFilename, String mimeType, long contentLength, String contentDisposition, String userAgent) {
        Timber.d("Download request: %s", suggestedFilename);

//        try {
//            if (AdvancedWebView.handleDownload(this, url, suggestedFilename)) {
//                showSnackbar(String.format("%s is downloaded", suggestedFilename), true);
//            } else {
//                showSnackbar("Couldn't download the file", false);
//            }
//        } catch (Exception exp) {
//            showSnackbar(String.format("Couldn't download the file: %s", exp), false);
//        }


//        webView.evaluateJavascript(String.format("javascript:noop(\"%s\")", "Hi"), new ValueCallback<String>() {
//            @Override
//            public void onReceiveValue(String value) {
//                Timber.d(value);
//            }
//        });
    }

    @Override
    public void onExternalPageRequest(String url) {
        Timber.d("onExternalPageRequest");
    }
}
