package org.freeyourgadget.weightrecorder;
import org.json.JSONObject;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 28, application = android.app.Application.class)
public class WebhookContractTest {
    @Test public void requestHasOnlyTwoNumericFieldsAndUnixSeconds() throws Exception {
        JSONObject body = new JSONObject(WebhookContract.body(1791264000123L, 70.25));
        assertEquals(2, body.length()); assertEquals(1791264000L, body.getLong("timestamp")); assertTrue(body.get("weight") instanceof Number); assertEquals(70.25, body.getDouble("weight"), .00001);
    }
    @Test public void successRequiresHttpSuccessAndNumericZeroStatus() throws Exception {
        assertEquals("今日已记录", WebhookContract.successMessage(200, "{\"status\":0,\"message\":\"今日已记录\"}"));
        for (String response : new String[]{"{\"status\":1,\"message\":\"失败\"}", "{\"status\":\"0\",\"message\":\"文本状态\"}", "{\"status\":0}", "not json"}) {
            try { WebhookContract.successMessage(200, response); fail("Must reject: " + response); } catch (JSONException expected) {}
        }
        try { WebhookContract.successMessage(500, "{\"status\":0,\"message\":\"no\"}"); fail(); } catch (JSONException expected) {}
    }
    @Test public void onlyValidHttpEndpointsAreAccepted() {
        assertTrue(WebhookContract.validUrl("https://example.com/hook?token=abc")); assertTrue(WebhookContract.validUrl("http://192.168.1.2:8080/hook"));
        assertFalse(WebhookContract.validUrl("file:///tmp/a")); assertFalse(WebhookContract.validUrl("https://user:pass@example.com")); assertFalse(WebhookContract.validUrl("bad"));
    }
}
