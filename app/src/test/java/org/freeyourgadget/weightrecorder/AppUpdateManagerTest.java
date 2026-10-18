/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.freeyourgadget.weightrecorder;

import org.junit.Test;
import static org.junit.Assert.*;

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
}
