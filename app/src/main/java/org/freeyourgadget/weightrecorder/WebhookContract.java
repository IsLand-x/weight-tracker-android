/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.freeyourgadget.weightrecorder;

import org.json.JSONException;
import org.json.JSONObject;
import java.net.URI;

public final class WebhookContract {
    private WebhookContract() {}
    public static boolean validUrl(String url) {
        try {
            URI uri = new URI(url.trim());
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null && uri.getFragment() == null;
        } catch (Exception e) { return false; }
    }
    public static String body(long timeMillis, double weightKg) throws JSONException {
        if (timeMillis <= 0 || !Double.isFinite(weightKg) || weightKg <= 0) throw new IllegalArgumentException("Invalid measurement");
        return new JSONObject().put("timestamp", timeMillis / 1000L).put("weight", weightKg).toString();
    }
    public static String successMessage(int httpStatus, String text) throws JSONException {
        if (httpStatus < 200 || httpStatus >= 300) throw new JSONException("HTTP " + httpStatus);
        JSONObject response = new JSONObject(text);
        Object status = response.get("status");
        Object message = response.get("message");
        if (!(status instanceof Number) || ((Number) status).doubleValue() != 0) throw new JSONException("接口未成功：" + message);
        if (!(message instanceof String)) throw new JSONException("message 必须是文本");
        return (String) message;
    }
}
