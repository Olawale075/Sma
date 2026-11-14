package sms.com.sms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TgiWebhookServiceTest {

    private static final String SECRET = "webhook-test-secret";
    private static final String PAYLOAD = "{\"event\":\"charge.success\",\"data\":{"
            + "\"transactionReference\":\"test-reference\",\"status\":\"SUCCESS\","
            + "\"amount\":100.00,\"currency\":\"NGN\"}}";

    @Test
    void verifiesRawBodySignatureAndForwardsTheNotification() throws Exception {
        TokenPurchaseService purchaseService = mock(TokenPurchaseService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        TgiWebhookService service = new TgiWebhookService(purchaseService, objectMapper, SECRET);
        byte[] rawBody = PAYLOAD.getBytes(StandardCharsets.UTF_8);
        JsonNode payload = objectMapper.readTree(rawBody);

        service.process(rawBody, sign(rawBody), "charge.success", "event-123");

        verify(purchaseService).processWebhook(payload, "charge.success", "event-123");
    }

    @Test
    void rejectsInvalidSignatureWithoutProcessingPayment() {
        TokenPurchaseService purchaseService = mock(TokenPurchaseService.class);
        TgiWebhookService service = new TgiWebhookService(
                purchaseService, new ObjectMapper(), SECRET);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.process(PAYLOAD.getBytes(StandardCharsets.UTF_8),
                        "0".repeat(128), "charge.success", "event-123"));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verify(purchaseService, never()).processWebhook(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    private String sign(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return HexFormat.of().formatHex(mac.doFinal(body));
    }
}
