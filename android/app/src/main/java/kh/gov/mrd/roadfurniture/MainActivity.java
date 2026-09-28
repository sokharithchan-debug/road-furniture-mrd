package kh.gov.mrd.roadfurniture;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.KeyEvent;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * A shell around the survey page.
 *
 * The page itself is the same single HTML file that runs on the web — it is not
 * rewritten for Android. This class only supplies the four things a plain
 * WebView will not do on its own:
 *
 *   1. serves the page from a real https:// origin, so the browser counts it as
 *      secure and allows GPS and localStorage;
 *   2. passes the GPS permission through to the page;
 *   3. opens the camera when the page asks for a photo;
 *   4. catches the CSV / Excel downloads and the print request, which a WebView
 *      otherwise drops on the floor.
 */
public class MainActivity extends Activity {

    private static final String ORIGIN = "https://appassets.androidplatform.net";
    private static final String HOME = ORIGIN + "/assets/index.html";
    private static final int REQ_PERMS = 10;
    private static final int REQ_FILE = 11;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private Uri photoUri;
    private long lastBackPress = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        setContentView(web);
        // a survey runs with the phone on the dashboard — letting it sleep would
        // stop the chainage counting
        web.setKeepScreenOn(true);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            s.setSafeBrowsingEnabled(false);   // nothing here is fetched from the web
        }

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                              @NonNull WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(@NonNull WebView view,
                                                    @NonNull WebResourceRequest request) {
                Uri u = request.getUrl();
                if (u != null && ORIGIN.equals(u.getScheme() + "://" + u.getAuthority())) {
                    return false;               // our own page — let it load
                }
                return true;                    // anything else is not ours to open
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(BRIDGE_JS, null);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin,
                                                           GeolocationPermissions.Callback callback) {
                // the page is the app; if Android has granted us location, it has it
                callback.invoke(origin, hasPermission(Manifest.permission.ACCESS_FINE_LOCATION), false);
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                return openPhotoPicker();
            }
        });

        web.addJavascriptInterface(new Bridge(), "MrdHost");
        askForPermissions();

        if (savedInstanceState == null) {
            web.loadUrl(HOME);
        } else {
            web.restoreState(savedInstanceState);
        }
    }

    // ------------------------------------------------------------------ permissions
    private boolean hasPermission(String p) {
        return ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED;
    }

    private void askForPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                && hasPermission(Manifest.permission.CAMERA)) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQ_PERMS);
        } else {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.CAMERA
            }, REQ_PERMS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_PERMS && !hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            Toast.makeText(this,
                    "ត្រូវការសិទ្ធិទីតាំង ដើម្បីរាប់ចំណុច គ.ម",
                    Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------------ photos
    private boolean openPhotoPicker() {
        try {
            File dir = new File(getCacheDir(), "photos");
            if (!dir.exists() && !dir.mkdirs()) throw new Exception("no cache dir");
            File f = new File(dir, "photo_" + System.currentTimeMillis() + ".jpg");
            photoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);

            Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            camera.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            camera.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
            pick.setType("image/*");
            pick.addCategory(Intent.CATEGORY_OPENABLE);

            Intent chooser = Intent.createChooser(pick, "រូបថត");
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{camera});
            startActivityForResult(chooser, REQ_FILE);
            return true;
        } catch (Exception e) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = null;
            photoUri = null;
            return false;
        }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code != REQ_FILE) return;
        if (fileCallback == null) return;

        Uri[] out = null;
        if (result == RESULT_OK) {
            if (data != null && data.getData() != null) {
                out = new Uri[]{data.getData()};        // picked from the gallery
            } else if (photoUri != null) {
                out = new Uri[]{photoUri};              // taken with the camera
            }
        }
        fileCallback.onReceiveValue(out);
        fileCallback = null;
        photoUri = null;
    }

    // ------------------------------------------------------------------ the page's side
    private class Bridge {

        /** CSV and Excel exports arrive here as base64 and land in Downloads. */
        @JavascriptInterface
        public void save(final String filename, final String mime, final String base64) {
            final String name = (filename == null || filename.isEmpty()) ? "export" : filename;
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                        writeToDownloads(name, mime, bytes);
                        Toast.makeText(MainActivity.this,
                                "រក្សាទុកក្នុង Downloads៖ " + name, Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this,
                                "រក្សាទុកមិនបាន៖ " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
            });
        }

        /** window.print() from the page opens Android's print / save-as-PDF sheet. */
        @JavascriptInterface
        public void print() {
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
                        String job = getString(R.string.app_name);
                        PrintDocumentAdapter adapter = web.createPrintDocumentAdapter(job);
                        pm.print(job, adapter, new PrintAttributes.Builder()
                                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                .build());
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this,
                                "បោះពុម្ពមិនបាន៖ " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
            });
        }
    }

    private void writeToDownloads(String name, String mime, byte[] bytes) throws Exception {
        String type = (mime == null || mime.isEmpty()) ? "application/octet-stream" : mime;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, name);
            v.put(MediaStore.Downloads.MIME_TYPE, type);
            v.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri item = getContentResolver()
                    .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (item == null) throw new Exception("Downloads refused the file");
            OutputStream os = getContentResolver().openOutputStream(item);
            if (os == null) throw new Exception("Downloads refused the file");
            os.write(bytes);
            os.close();
            v.clear();
            v.put(MediaStore.Downloads.IS_PENDING, 0);
            getContentResolver().update(item, v, null, null);
        } else {
            File dir = Environment
                    .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!dir.exists() && !dir.mkdirs()) throw new Exception("no Downloads folder");
            FileOutputStream fos = new FileOutputStream(new File(dir, name));
            fos.write(bytes);
            fos.close();
        }
    }

    /**
     * Injected into the page after it loads. It leaves the page's own code alone
     * and only redirects the two things that need the phone: a download link the
     * page clicks, and window.print().
     */
    private static final String BRIDGE_JS =
        "(function(){" +
        "  if(window.__mrdHostWired || !window.MrdHost) return; window.__mrdHostWired=1;" +
        // the page revokes its blob the instant it clicks it, which is too soon
        // for us to read it back — hold the revoke for a few seconds
        "  var revoke=URL.revokeObjectURL.bind(URL);" +
        "  URL.revokeObjectURL=function(u){ setTimeout(function(){ try{revoke(u);}catch(e){} }, 15000); };" +
        "  document.addEventListener('click', function(ev){" +
        "    var t=ev.target; var a=null;" +
        "    while(t && t!==document){ if(t.tagName==='A' && t.hasAttribute('download')){ a=t; break; } t=t.parentNode; }" +
        "    if(!a) return; var href=a.getAttribute('href')||a.href;" +
        "    if(!href || href.indexOf('blob:')!==0) return;" +
        "    ev.preventDefault(); ev.stopPropagation();" +
        "    var name=a.getAttribute('download')||'export';" +
        "    fetch(href).then(function(r){ return r.blob(); }).then(function(b){" +
        "      var fr=new FileReader();" +
        "      fr.onload=function(){" +
        "        var s=String(fr.result); var i=s.indexOf(',');" +
        "        MrdHost.save(name, b.type||'application/octet-stream', s.slice(i+1));" +
        "      };" +
        "      fr.readAsDataURL(b);" +
        "    }).catch(function(e){ alert('នាំចេញមិនបាន៖ '+e); });" +
        "  }, true);" +
        "  window.print=function(){ MrdHost.print(); };" +
        "})();";

    // ------------------------------------------------------------------ housekeeping
    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // a stray back swipe mid-survey should not close the app
            long now = System.currentTimeMillis();
            if (now - lastBackPress < 2500) return super.onKeyDown(keyCode, event);
            lastBackPress = now;
            Toast.makeText(this, "ចុចថយក្រោយម្ដងទៀត ដើម្បីចាកចេញ", Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
