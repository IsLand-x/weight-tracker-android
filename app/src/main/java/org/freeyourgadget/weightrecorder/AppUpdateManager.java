/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.freeyourgadget.weightrecorder;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppUpdateManager {
    public static final String COS_RELEASE_URL = "https://island-agent-images-1251186405.cos.ap-singapore.myqcloud.com/weight-recorder/release.json";
    public static final String COS_LATEST_APK = "https://island-agent-images-1251186405.cos.ap-singapore.myqcloud.com/weight-recorder/weight-recorder-latest.apk";
    public static final String GITHUB_RELEASES_API = "https://api.github.com/repos/IsLand-x/weight-tracker-android/releases/latest";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static Handler handler;
    private static boolean checking = false;

    private static synchronized Handler getHandler() {
        if (handler == null) handler = new Handler(Looper.getMainLooper());
        return handler;
    }

    public static class UpdateInfo {
        public final String tagName;
        public final String downloadUrl;
        public final String body;

        public UpdateInfo(String tagName, String downloadUrl, String body) {
            this.tagName = tagName;
            this.downloadUrl = downloadUrl;
            this.body = body;
        }
    }

    public static UpdateInfo parseCosRelease(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) return null;
        try {
            JSONObject obj = new JSONObject(jsonString);
            String versionName = obj.optString("version_name", "");
            if (versionName.isEmpty()) return null;
            String tagName = versionName.startsWith("v") || versionName.startsWith("V") ? versionName : "v" + versionName;
            String downloadUrl = obj.optString("download_url", COS_LATEST_APK);
            String body = obj.optString("body", "腾讯云对象存储（COS）最新版本 APK");
            return new UpdateInfo(tagName, downloadUrl, body);
        } catch (Exception e) {
            return null;
        }
    }

    public static UpdateInfo parseGithubRelease(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) return null;
        try {
            JSONObject release = new JSONObject(jsonString);
            String tagName = release.optString("tag_name", "");
            String body = release.optString("body", "");
            String downloadUrl = null;
            JSONArray assets = release.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    String name = asset.optString("name", "");
                    if (name.endsWith(".apk")) {
                        downloadUrl = asset.optString("browser_download_url", "");
                        break;
                    }
                }
            }
            if (downloadUrl == null) return null;
            return new UpdateInfo(tagName, downloadUrl, body);
        } catch (Exception e) {
            return null;
        }
    }

    private static String fetchHttp(String urlStr, int timeoutMs) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "WeightTracker-Android-Updater");
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        if (conn.getResponseCode() != 200) return null;
        try (Scanner scanner = new Scanner(conn.getInputStream(), "UTF-8").useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    public static void checkForUpdates(Activity activity, boolean silent) {
        if (checking) return;
        checking = true;

        executor.execute(() -> {
            try {
                UpdateInfo update = null;
                // 1. First try Tencent Cloud COS release.json
                try {
                    String cosJson = fetchHttp(COS_RELEASE_URL, 5000);
                    if (cosJson != null) {
                        update = parseCosRelease(cosJson);
                    }
                } catch (Exception ignored) {}

                // 2. Fallback to GitHub Releases API if COS is unavailable
                if (update == null) {
                    try {
                        String ghJson = fetchHttp(GITHUB_RELEASES_API, 8000);
                        if (ghJson != null) {
                            update = parseGithubRelease(ghJson);
                        }
                    } catch (Exception ignored) {}
                }

                checking = false;
                if (update != null && isNewerVersion(activity, update.tagName)) {
                    final UpdateInfo finalUpdate = update;
                    getHandler().post(() -> {
                        if (!activity.isFinishing()) {
                            showUpdateDialog(activity, finalUpdate);
                        }
                    });
                } else if (!silent) {
                    getHandler().post(() -> Toast.makeText(activity, "当前已是最新版本", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                checking = false;
                if (!silent) {
                    getHandler().post(() -> Toast.makeText(activity, "检查更新失败，请稍后再试", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    public static boolean isNewerVersion(Context context, String remoteTag) {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            String currentVersion = pInfo.versionName;
            return compareVersions(remoteTag, currentVersion) > 0;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static int compareVersions(String remote, String current) {
        String cleanRemote = remote.replaceAll("^[vV]", "").trim();
        String cleanCurrent = current.replaceAll("^[vV]", "").replaceAll("-preview$", "").trim();
        String[] rParts = cleanRemote.split("\\.");
        String[] cParts = cleanCurrent.split("\\.");
        int length = Math.max(rParts.length, cParts.length);
        for (int i = 0; i < length; i++) {
            int r = i < rParts.length ? parseNumber(rParts[i]) : 0;
            int c = i < cParts.length ? parseNumber(cParts[i]) : 0;
            if (r != c) return Integer.compare(r, c);
        }
        return 0;
    }

    private static int parseNumber(String part) {
        try {
            return Integer.parseInt(part.replaceAll("\\D.*", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private static void showUpdateDialog(Activity activity, UpdateInfo update) {
        String message = "发现新版本：" + update.tagName + "\n\n" + (update.body.isEmpty() ? "点击即可下载并自动安装更新。" : update.body);
        new MaterialAlertDialogBuilder(activity)
                .setTitle("版本更新")
                .setMessage(message)
                .setPositiveButton("立即更新", (dialog, which) -> downloadAndInstall(activity, update))
                .setNegativeButton("稍后", null)
                .show();
    }

    private static void downloadAndInstall(Activity activity, UpdateInfo update) {
        Toast.makeText(activity, "正在后台下载更新安装包...", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            try {
                File dir = new File(activity.getCacheDir(), "updates");
                if (!dir.exists()) dir.mkdirs();
                File apkFile = new File(dir, "update.apk");
                if (apkFile.exists()) apkFile.delete();

                URL url = new URL(update.downloadUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.connect();

                // Handle redirect manually if needed
                int status = conn.getResponseCode();
                if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                    String newUrl = conn.getHeaderField("Location");
                    conn = (HttpURLConnection) new URL(newUrl).openConnection();
                    conn.connect();
                }

                try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(apkFile)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }

                getHandler().post(() -> installApk(activity, apkFile));
            } catch (Exception e) {
                getHandler().post(() -> Toast.makeText(activity, "更新包下载失败，请稍后再试", Toast.LENGTH_LONG).show());
            }
        });
    }

    public static void installApk(Activity activity, File apkFile) {
        if (!apkFile.exists()) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Uri apkUri;
            if (Build.VERSION.SDK_INT >= 24) {
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                apkUri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", apkFile);
            } else {
                apkUri = Uri.fromFile(apkFile);
            }
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            activity.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(activity, "启动安装失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
