package org.freeyourgadget.weightrecorder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 28, application = android.app.Application.class)
public class WebhookTransportTest {
    @Test public void postsExactlyTheRequestedContractAndReturnsUnicodeMessage() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            CompletableFuture<String> captured = new CompletableFuture<>();
            Thread thread = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(10000);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    assertEquals("POST /hook HTTP/1.1", reader.readLine());
                    int contentLength = 0; String header;
                    while (!(header = reader.readLine()).isEmpty()) if (header.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) contentLength = Integer.parseInt(header.substring(15).trim());
                    char[] body = new char[contentLength]; int offset = 0;
                    while (offset < body.length) { int count = reader.read(body, offset, body.length - offset); if (count < 0) throw new java.io.IOException("Incomplete body"); offset += count; }
                    captured.complete(new String(body));
                    byte[] response = "{\"status\":0,\"message\":\"今日体重已记录\"}".getBytes(StandardCharsets.UTF_8);
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: " + response.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().write(response); socket.getOutputStream().flush();
                } catch (Throwable e) { captured.completeExceptionally(e); }
            }); thread.setDaemon(true); thread.start();
            String host = server.getInetAddress().getHostAddress(); if (host.contains(":")) host = "[" + host + "]";
            assertEquals("今日体重已记录", WebhookTransport.send("http://" + host + ":" + server.getLocalPort() + "/hook", 1791264000123L, 70.25));
            JSONObject sent = new JSONObject(captured.get(10, TimeUnit.SECONDS)); assertEquals(2, sent.length()); assertEquals(1791264000L, sent.getLong("timestamp")); assertEquals(70.25, sent.getDouble("weight"), .00001);
        }
    }
}
