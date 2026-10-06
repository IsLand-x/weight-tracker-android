/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.freeyourgadget.weightrecorder;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class WebhookTransport {
    private WebhookTransport() {}
    public static String send(String endpoint, long timeMillis, double weight) throws Exception {
        if (!WebhookContract.validUrl(endpoint)) throw new IllegalArgumentException("Webhook 地址无效");
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            connection.setConnectTimeout(15000); connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(false); connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json"); connection.setDoOutput(true);
            byte[] body = WebhookContract.body(timeMillis, weight).getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream stream = connection.getOutputStream()) { stream.write(body); }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new java.io.IOException("HTTP " + status);
            try (InputStream stream = connection.getInputStream(); ByteArrayOutputStream response = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int length;
                while ((length = stream.read(buffer)) != -1) {
                    if (response.size() + length > 65536) throw new java.io.IOException("Webhook 响应过长");
                    response.write(buffer, 0, length);
                }
                return WebhookContract.successMessage(status, response.toString(StandardCharsets.UTF_8.name()));
            }
        } finally { connection.disconnect(); }
    }
}
