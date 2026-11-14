package sms.com.sms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class TgiWebhookService {

    private static final String SIGNATURE_ALGORITHM = "HmacSHA512";

    private final TokenPurchaseService purchaseService;
    private final ObjectMapper objectMapper;
    private final String webhookSecret;

    public TgiWebhookService(TokenPurchaseService purchaseService,
                             ObjectMapper objectMapper,
                             @Value("${tgi.webhook-secret:}") String webhookSecret) {
        this.purchaseService = purchaseService;
        this.objectMapper = objectMapper;
        this.webhookSecret = webhookSecret;
    }

    public void process(byte[] rawBody, String signature, String eventHeader, String idempotencyKey) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "TGI webhook is not configured");
        }
        if (rawBody == null || rawBody.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TGI webhook body is required");
        }
        verifySignature(rawBody, signature);

        JsonNode webhook;
        try {
            webhook = objectMapper.readTree(rawBody);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid TGI webhook JSON", exception);
        }
        if (webhook == null || !webhook.isObject()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid TGI webhook payload");
        }
        purchaseService.processWebhook(webhook, eventHeader, idempotencyKey);
    }

    private void verifySignature(byte[] rawBody, String signature) {
        if (signature == null || !signature.matches("(?i)[0-9a-f]{128}")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid TGI webhook signature");
        }

        try {
            Mac mac = Mac.getInstance(SIGNATURE_ALGORITHM);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    SIGNATURE_ALGORITHM));
            byte[] expected = mac.doFinal(rawBody);
            byte[] supplied = HexFormat.of().parseHex(signature);
            if (!MessageDigest.isEqual(expected, supplied)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid TGI webhook signature");
            }
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA512 is unavailable", exception);
        }
    }
}
