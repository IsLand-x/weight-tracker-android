/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.freeyourgadget.weightrecorder;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = android.app.Application.class)
public class AppUpdateManagerTest {
    @Test
    public void versionComparisonWorksCorrectly() {
        assertEquals(0, AppUpdateManager.compareVersions("v1.2.0", "1.2.0"));
        assertEquals(0, AppUpdateManager.compareVersions("1.2.0", "1.2.0-preview"));
        assertTrue(AppUpdateManager.compareVersions("v1.2.1", "1.2.0") > 0);
        assertTrue(AppUpdateManager.compareVersions("v1.3.0", "1.2.9") > 0);
        assertTrue(AppUpdateManager.compareVersions("v2.0.0", "1.9.9") > 0);
        assertTrue(AppUpdateManager.compareVersions("v1.2.0", "1.2.1") < 0);
        assertTrue(AppUpdateManager.compareVersions("v1.2.0", "1.3.0") < 0);
    }

    @Test
    public void parsesCosReleaseCorrectly() {
        String json = "{\"version_name\":\"1.3.2\",\"download_url\":\"https://example.com/latest.apk\",\"body\":\"更新说明\"}";
        AppUpdateManager.UpdateInfo info = AppUpdateManager.parseCosRelease(json);
        assertNotNull(info);
        assertEquals("v1.3.2", info.tagName);
        assertEquals("https://example.com/latest.apk", info.downloadUrl);
        assertEquals("更新说明", info.body);

        // Fallback default url and body
        String minimalJson = "{\"version_name\":\"1.3.2\"}";
        AppUpdateManager.UpdateInfo minimal = AppUpdateManager.parseCosRelease(minimalJson);
        assertNotNull(minimal);
        assertEquals("v1.3.2", minimal.tagName);
        assertEquals(AppUpdateManager.COS_LATEST_APK, minimal.downloadUrl);
    }

    @Test
    public void parsesGithubReleaseCorrectly() {
        String json = "{\"tag_name\":\"v1.3.1\",\"body\":\"日志\",\"assets\":[{\"name\":\"weight-recorder-1.3.1-release.apk\",\"browser_download_url\":\"https://github.com/apk\"}]}";
        AppUpdateManager.UpdateInfo info = AppUpdateManager.parseGithubRelease(json);
        assertNotNull(info);
        assertEquals("v1.3.1", info.tagName);
        assertEquals("https://github.com/apk", info.downloadUrl);
        assertEquals("日志", info.body);
    }
}
