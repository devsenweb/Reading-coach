package com.dhil.readingcoach;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shows the Ivana School Prep page (Reading Coach, Greek & Latin, Mad Minute, weekly tests). On every launch it quietly downloads the newest
 * index.html from GitHub; if it changed, the app switches to it (right away when
 * Ivana is on the home screen, otherwise next launch). No reinstall needed.
 */
public class MainActivity extends Activity {
    private static final String BASE = "https://readingcoach.app/"; // fixed origin keeps progress in localStorage
    private WebView web;
    private String current;
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private volatile boolean ttsReady;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#fbf7ef"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setTextZoom(100);
        setContentView(web);
        // Lets the page speak praise out loud ("Great job!") with the phone's own voice
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.SUCCESS) return;
                tts.setLanguage(Locale.US);
                tts.setPitch(1.15f);
                ttsReady = true;
            }
        });
        web.addJavascriptInterface(new Speaker(), "AndroidVoice");
        web.addJavascriptInterface(new Updater(), "AndroidApp");
        current = loadPage();
        show(current);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkForUpdate();
    }

    // The page picks the voice, pitch and speed, so they can be tuned without a reinstall
    private class Speaker {
        @JavascriptInterface
        public void speak(String text) {
            if (ttsReady && text != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cheer");
        }

        @JavascriptInterface
        public void speakWith(String text, float pitch, float rate) {
            if (!ttsReady || text == null) return;
            tts.setPitch(pitch);
            tts.setSpeechRate(rate);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cheer");
        }

        /** English voices installed on the phone, one per line, offline ones first. */
        @JavascriptInterface
        public String voices() {
            if (!ttsReady) return "";
            StringBuilder local = new StringBuilder(), online = new StringBuilder();
            try {
                Set<Voice> all = tts.getVoices();
                if (all == null) return "";
                for (Voice v : all) {
                    if (v.getLocale() == null || !"en".equals(v.getLocale().getLanguage())) continue;
                    (v.isNetworkConnectionRequired() ? online : local).append(v.getName()).append('\n');
                }
            } catch (Exception ignored) {
                return "";
            }
            return local.toString() + online;
        }

        @JavascriptInterface
        public boolean setVoice(String name) {
            if (!ttsReady || name == null) return false;
            try {
                for (Voice v : tts.getVoices()) {
                    if (name.equals(v.getName())) return tts.setVoice(v) == TextToSpeech.SUCCESS;
                }
            } catch (Exception ignored) {
            }
            return false;
        }
    }

    // In-app update of the app itself: the page shows an Update button when build.gradle on main
    // has a newer versionCode; tapping it downloads the APK and opens Android's install screen.
    private static final String GRADLE_URL = "https://raw.githubusercontent.com/devsenweb/reading-coach/main/app/build.gradle";
    private static final String APK_URL = "https://github.com/devsenweb/reading-coach/releases/download/latest/ReadingCoach.apk";
    private static final String INSTALL_ACTION = "com.dhil.readingcoach.INSTALL_STATUS";
    private volatile boolean installing;

    private class Updater {
        @JavascriptInterface
        public int version() { return BuildConfig.VERSION_CODE; }

        /** Calls window.onAppUpdate(newVersion) in the page when a newer app is on main. */
        @JavascriptInterface
        public void check() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        HttpURLConnection c = (HttpURLConnection) new URL(GRADLE_URL + "?t=" + System.currentTimeMillis()).openConnection();
                        c.setConnectTimeout(8000);
                        c.setReadTimeout(8000);
                        c.setUseCaches(false);
                        if (c.getResponseCode() != 200) return;
                        Matcher m = Pattern.compile("versionCode\\s+(\\d+)").matcher(read(c.getInputStream()));
                        if (!m.find()) return;
                        final int latest = Integer.parseInt(m.group(1));
                        if (latest <= BuildConfig.VERSION_CODE) return;
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                web.evaluateJavascript("window.onAppUpdate&&onAppUpdate(" + latest + ")", null);
                            }
                        });
                    } catch (Exception ignored) {
                        // offline: try again next launch
                    }
                }
            }).start();
        }

        @JavascriptInterface
        public void install() {
            if (installing) return;
            if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        toast("Turn on \"Allow from this source\", then come back and tap Update again");
                        startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
                    }
                });
                return;
            }
            installing = true;
            toast("Downloading the update…");
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        installApk();
                    } catch (Exception e) {
                        toast("Update failed. Check the internet and try again.");
                    } finally {
                        installing = false;
                    }
                }
            }).start();
        }
    }

    private void installApk() throws Exception {
        File apk = new File(getCacheDir(), "update.apk");
        HttpURLConnection c = (HttpURLConnection) new URL(APK_URL + "?t=" + System.currentTimeMillis()).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
        try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(apk)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        // build.gradle can be ahead of the release for a minute while GitHub builds it
        PackageInfo info = getPackageManager().getPackageArchiveInfo(apk.getPath(), 0);
        if (info == null) throw new Exception("bad apk");
        if (info.versionCode <= BuildConfig.VERSION_CODE) {
            toast("The update is still being built. Try again in a minute.");
            return;
        }
        PackageInstaller pi = getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(getPackageName());
        int id = pi.createSession(params);
        try (PackageInstaller.Session session = pi.openSession(id)) {
            try (InputStream in = new FileInputStream(apk); OutputStream out = session.openWrite("ReadingCoach.apk", 0, apk.length())) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                session.fsync(out);
            }
            Intent i = new Intent(this, MainActivity.class).setAction(INSTALL_ACTION);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            session.commit(PendingIntent.getActivity(this, 0, i, flags).getIntentSender());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null || !INSTALL_ACTION.equals(intent.getAction()) || intent.getExtras() == null) return;
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = (Intent) intent.getExtras().get(Intent.EXTRA_INTENT);
            if (confirm != null) startActivity(confirm);
        } else if (status != PackageInstaller.STATUS_SUCCESS) {
            toast("Update didn't install. Try again.");
        }
    }

    private void toast(final String msg) {
        main.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }

    private File cacheFile() { return new File(getFilesDir(), "index.html"); }

    private String loadPage() {
        try {
            File f = cacheFile();
            if (f.exists()) return read(new FileInputStream(f));
            return read(getAssets().open("index.html"));
        } catch (Exception e) {
            return "<h1>Could not load School Prep</h1>";
        }
    }

    private void show(String html) {
        web.loadDataWithBaseURL(BASE, html, "text/html", "utf-8", null);
    }

    private void checkForUpdate() {
        final String url = BuildConfig.UPDATE_URL;
        if (url == null || url.isEmpty()) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                downloadUpdate(url);
            }
        }).start();
    }

    private void downloadUpdate(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url + "?t=" + System.currentTimeMillis()).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setUseCaches(false);
            if (c.getResponseCode() != 200) return;
            String fresh = read(c.getInputStream());
            if (!(fresh.contains("School Prep") || fresh.contains("Reading Coach")) || fresh.equals(current)) return;
            try (FileOutputStream out = new FileOutputStream(cacheFile())) {
                out.write(fresh.getBytes(StandardCharsets.UTF_8));
            }
            main.post(new Runnable() {
                @Override
                public void run() {
                    web.evaluateJavascript(
                            "(function(){var h=document.getElementById('home');return !!(h&&!h.hidden);})()",
                            new ValueCallback<String>() {
                                @Override
                                public void onReceiveValue(String onHome) {
                                    if ("true".equals(onHome)) {
                                        current = fresh;
                                        show(fresh);
                                        Toast.makeText(MainActivity.this, "School Prep updated", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                }
            });
        } catch (Exception ignored) {
            // offline: keep the copy we have
        }
    }

    private static String read(InputStream in) throws Exception {
        try (InputStream i = in; ByteArrayOutputStream o = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = i.read(buf)) > 0) o.write(buf, 0, n);
            return o.toString("UTF-8");
        }
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript(
                "(function(){return window.goBack?window.goBack():'exit';})()",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String r) {
                        if (r != null && r.contains("exit")) finish();
                    }
                });
    }
}
