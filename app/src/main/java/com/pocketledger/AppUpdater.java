package com.pocketledger;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Checks public release metadata and installs only newer APKs signed by this app's key. */
final class AppUpdater {
    interface Listener {
        void onStatusChanged();
        void onUpdateAvailable(String version, Runnable accept);
        void onInstallPermissionNeeded(Runnable openSettings);
        void onMessage(String title, String message);
    }

    private static final String RELEASE_API = "https://api.github.com/repos/gatofile/Pocket-Ledger/releases/latest";
    private static final String DOWNLOAD_PREFIX = "https://github.com/gatofile/Pocket-Ledger/releases/download/";
    private static final long MAX_APK_BYTES = 100L * 1024 * 1024;
    private final Activity activity;
    private final Listener listener;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final SharedPreferences pending;
    private final File apk;
    private volatile HttpURLConnection connection;
    private volatile boolean destroyed;
    private boolean resumed, checkOnResume = true, busy, offerPending, installPending, waitingForPermission, installerOpen;
    private Release available;
    private String status = "Updates are checked when the app opens.";
    private int progress = -1;

    AppUpdater(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        pending = activity.getSharedPreferences("pocket-ledger-updater", Activity.MODE_PRIVATE);
        apk = UpdateApkProvider.apkFile(activity);
        try {
            if (compareVersions(pending.getString("version", "0"), installed().versionName) <= 0) {
                apk.delete();
                pending.edit().clear().apply();
            }
        } catch (Exception ignored) { }
    }

    String status() { return status; }
    int progress() { return progress; }
    boolean isBusy() { return busy; }

    void onResume() {
        resumed = true;
        if (waitingForPermission) {
            waitingForPermission = false;
            if (Build.VERSION.SDK_INT < 26 || activity.getPackageManager().canRequestPackageInstalls()) {
                installApk();
            } else {
                fail("Allow app installations to install the update.", true);
            }
            return;
        }
        if (installPending) { requestInstallation(); return; }
        if (installerOpen) {
            installerOpen = false;
            setStatus("Update ready to install.", false);
        }
        if (checkOnResume && !busy) { checkOnResume = false; check(false); }
        else presentOffer();
    }

    void onPause() { resumed = false; }

    void onStop() {
        if (!waitingForPermission && !installerOpen && !busy) checkOnResume = true;
    }

    void close() {
        destroyed = true;
        HttpURLConnection active = connection;
        if (active != null) active.disconnect();
        worker.shutdownNow();
    }

    void check(boolean manual) {
        if (destroyed || busy) return;
        setStatus("Checking for updates…", true);
        worker.execute(() -> {
            try {
                Release release = latestRelease();
                boolean newer = compareVersions(release.version, installed().versionName) > 0;
                post(() -> {
                    available = newer ? release : null;
                    setStatus(newer ? "A new update is available." : "Pocket Ledger is up to date.", false);
                    offerPending = newer;
                    if (newer) presentOffer();
                    else if (manual && resumed) listener.onMessage("Up to date", "Pocket Ledger is up to date.");
                });
            } catch (Exception exception) {
                Release saved = savedRelease();
                if (saved != null) {
                    post(() -> {
                        available = saved;
                        setStatus("Update ready to install.", false);
                        offerPending = true;
                        presentOffer();
                    });
                } else {
                    post(() -> fail("Could not check for updates. Check your connection and try again later.", manual));
                }
            } finally { disconnect(); }
        });
    }

    private void presentOffer() {
        if (!resumed || busy || !offerPending || available == null) return;
        offerPending = false;
        Release selected = available;
        listener.onUpdateAvailable(selected.version, () -> download(selected));
    }

    private void download(Release release) {
        if (busy || destroyed) return;
        setStatus("Downloading update…", true);
        worker.execute(() -> {
            File temporary = new File(apk.getParentFile(), "download.part");
            try {
                if (!apk.getParentFile().isDirectory() && !apk.getParentFile().mkdirs()) throw new IOException("Storage unavailable");
                if (apk.isFile() && release.version.equals(pending.getString("version", ""))) {
                    verifyApk(apk, release);
                } else {
                    downloadFile(release, temporary);
                    verifyApk(temporary, release);
                    if (destroyed) throw new IOException("Cancelled");
                    if (apk.exists() && !apk.delete()) throw new IOException("Cannot replace update");
                    if (!temporary.renameTo(apk)) throw new IOException("Cannot save update");
                }
                if (!pending.edit().putString("version", release.version).putString("url", release.url)
                        .putString("digest", release.digest).putLong("size", release.size).commit()) {
                    throw new IOException("Cannot save update information");
                }
                post(() -> {
                    setStatus("Update ready to install.", false);
                    installPending = true;
                    if (resumed) requestInstallation();
                });
            } catch (Exception exception) {
                apk.delete();
                pending.edit().clear().apply();
                post(() -> fail("Could not download or verify the update. Please try again.", true));
            } finally {
                temporary.delete();
                disconnect();
            }
        });
    }

    private void downloadFile(Release release, File destination) throws Exception {
        HttpURLConnection request = open(release.url, false);
        long total = 0;
        int lastPercent = -5;
        try (InputStream input = request.getInputStream(); FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[32768];
            int length;
            while ((length = input.read(buffer)) != -1) {
                if (destroyed || Thread.currentThread().isInterrupted()) throw new IOException("Cancelled");
                total += length;
                if (total > MAX_APK_BYTES || total > release.size) throw new IOException("Invalid APK size");
                output.write(buffer, 0, length);
                int percent = (int) (total * 100 / release.size);
                if (percent >= lastPercent + 5) {
                    lastPercent = percent;
                    post(() -> { progress = percent; listener.onStatusChanged(); });
                }
            }
            output.getFD().sync();
        }
        if (total != release.size) throw new IOException("Incomplete download");
    }

    private void verifyApk(File file, Release release) throws Exception {
        if (file.length() != release.size || file.length() > MAX_APK_BYTES) throw new IOException("Invalid APK size");
        if (!release.digest.isEmpty()) {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new FileInputStream(file)) {
                byte[] buffer = new byte[32768];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (destroyed) throw new IOException("Cancelled");
                    hash.update(buffer, 0, count);
                }
            }
            StringBuilder hex = new StringBuilder("sha256:");
            for (byte value : hash.digest()) hex.append(String.format(Locale.US, "%02x", value & 0xff));
            if (!hex.toString().equals(release.digest)) throw new IOException("Digest mismatch");
        }
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo candidate = activity.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(), flags);
        PackageInfo current = installed();
        if (candidate == null || !activity.getPackageName().equals(candidate.packageName)
                || !release.version.equals(candidate.versionName) || versionCode(candidate) <= versionCode(current)
                || !Arrays.equals(signers(current), signers(candidate))) throw new IOException("APK is not a trusted update");
    }

    private static String[] signers(PackageInfo info) throws IOException {
        Signature[] certificates = Build.VERSION.SDK_INT >= 28
                ? (info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners()) : info.signatures;
        if (certificates == null || certificates.length == 0) throw new IOException("Missing APK signature");
        String[] result = new String[certificates.length];
        for (int index = 0; index < result.length; index++) result[index] = certificates[index].toCharsString();
        Arrays.sort(result);
        return result;
    }

    private PackageInfo installed() throws PackageManager.NameNotFoundException {
        return activity.getPackageManager().getPackageInfo(activity.getPackageName(),
                Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES);
    }

    private static long versionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    private Release latestRelease() throws Exception {
        HttpURLConnection request = open(RELEASE_API, true);
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        try (InputStream input = request.getInputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (destroyed || result.size() + count > 1024 * 1024) throw new IOException("Invalid release response");
                result.write(buffer, 0, count);
            }
        }
        JSONObject json = new JSONObject(new String(result.toByteArray(), StandardCharsets.UTF_8));
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) throw new IOException("Not a stable release");
        String version = json.getString("tag_name").replaceFirst("^[vV]", "");
        if (!version.matches("[0-9]+(?:\\.[0-9]+){1,3}")) throw new IOException("Invalid version");
        JSONArray assets = json.getJSONArray("assets");
        for (int index = 0; index < assets.length(); index++) {
            JSONObject asset = assets.getJSONObject(index);
            if (("Pocket-Ledger-Android-" + version + ".apk").equals(asset.optString("name"))
                    && "uploaded".equals(asset.optString("state"))) {
                String url = asset.getString("browser_download_url");
                long size = asset.getLong("size");
                String digest = asset.optString("digest", "");
                if (digest.equals("null")) digest = "";
                if (!url.startsWith(DOWNLOAD_PREFIX) || size <= 0 || size > MAX_APK_BYTES
                        || (!digest.isEmpty() && !digest.matches("sha256:[a-f0-9]{64}"))) throw new IOException("Invalid asset");
                return new Release(version, url, digest, size);
            }
        }
        throw new IOException("Release APK unavailable");
    }

    private HttpURLConnection open(String address, boolean api) throws IOException {
        URL url = new URL(address);
        for (int redirects = 0; redirects < 6; redirects++) {
            String host = url.getHost();
            if (!"https".equals(url.getProtocol()) || !(host.equals("api.github.com") || host.equals("github.com")
                    || host.equals("release-assets.githubusercontent.com") || host.equals("objects.githubusercontent.com"))) {
                throw new IOException("Untrusted download host");
            }
            if (destroyed) throw new IOException("Cancelled");
            HttpURLConnection request = (HttpURLConnection) url.openConnection();
            connection = request;
            request.setConnectTimeout(10000);
            request.setReadTimeout(20000);
            request.setUseCaches(false);
            request.setInstanceFollowRedirects(false);
            request.setRequestProperty("User-Agent", "Pocket-Ledger-Android");
            request.setRequestProperty("Accept", api ? "application/vnd.github+json" : "application/octet-stream");
            int code = request.getResponseCode();
            if (code == 200) return request;
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String location = request.getHeaderField("Location");
                request.disconnect();
                if (location == null) throw new IOException("Missing redirect");
                url = new URL(url, location);
            } else {
                request.disconnect();
                throw new IOException("GitHub response " + code);
            }
        }
        throw new IOException("Too many redirects");
    }

    private Release savedRelease() {
        try {
            String version = pending.getString("version", "0");
            if (!apk.isFile() || compareVersions(version, installed().versionName) <= 0) return null;
            return new Release(version, pending.getString("url", ""), pending.getString("digest", ""), pending.getLong("size", 0));
        } catch (Exception ignored) { return null; }
    }

    private void requestInstallation() {
        installPending = false;
        if (!apk.isFile()) { fail("Could not download or verify the update. Please try again.", true); return; }
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            listener.onInstallPermissionNeeded(() -> {
                try {
                    waitingForPermission = true;
                    activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.getPackageName())));
                } catch (Exception exception) {
                    waitingForPermission = false;
                    fail("Could not open Android's update installer.", true);
                }
            });
        } else installApk();
    }

    private void installApk() {
        try {
            Uri uri = new Uri.Builder().scheme("content").authority(activity.getPackageName() + ".updates")
                    .appendPath("update.apk").build();
            Intent install = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            install.setClipData(ClipData.newRawUri("Pocket Ledger update", uri));
            installerOpen = true;
            setStatus("Complete the update in Android.", false);
            activity.startActivity(install);
        } catch (Exception exception) {
            installerOpen = false;
            fail("Could not open Android's update installer.", true);
        }
    }

    private void setStatus(String value, boolean running) {
        status = value;
        busy = running;
        progress = -1;
        listener.onStatusChanged();
    }

    private void fail(String message, boolean show) {
        setStatus(message, false);
        if (show && resumed) listener.onMessage("App updates", message);
    }

    private void post(Runnable callback) {
        activity.runOnUiThread(() -> {
            if (!destroyed && !activity.isFinishing() && !activity.isDestroyed()) callback.run();
        });
    }

    private void disconnect() {
        HttpURLConnection active = connection;
        if (active != null) active.disconnect();
        connection = null;
    }

    static int compareVersions(String left, String right) {
        String[] a = left.split("\\."), b = right.split("\\.");
        for (int index = 0; index < Math.max(a.length, b.length); index++) {
            int difference = Long.compare(index < a.length ? Long.parseLong(a[index]) : 0,
                    index < b.length ? Long.parseLong(b[index]) : 0);
            if (difference != 0) return difference;
        }
        return 0;
    }

    private static final class Release {
        final String version, url, digest;
        final long size;
        Release(String version, String url, String digest, long size) {
            this.version = version; this.url = url; this.digest = digest; this.size = size;
        }
    }
}
