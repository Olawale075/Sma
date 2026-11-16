package sms.com.sms.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sms.com.sms.dto.TokenPurchaseRequest;
import sms.com.sms.dto.TokenPurchaseResponse;
import sms.com.sms.enums.TokenPurchaseStatus;
import sms.com.sms.service.TokenPurchaseService;
import sms.com.sms.service.TgiWebhookService;

import java.util.Map;
import java.net.URI;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final TokenPurchaseService purchaseService;
    private final TgiWebhookService webhookService;
    private final String frontendUrl;

    public PaymentController(TokenPurchaseService purchaseService,
                             TgiWebhookService webhookService,
                             @Value("${app.frontend-url:}") String frontendUrl) {
        this.purchaseService = purchaseService;
        this.webhookService = webhookService;
        this.frontendUrl = frontendUrl;
    }

    @PostMapping("/initiate")
    public ResponseEntity<TokenPurchaseResponse> initiatePayment(
            @AuthenticationPrincipal(expression = "username") String phoneNumber,
            @Valid @RequestBody TokenPurchaseRequest request) {
        return ResponseEntity.ok(purchaseService.initiatePurchase(
                phoneNumber, request.getTokenType(), request.getTokenQuantity()));
    }

    @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> paymentWebhook(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-TGIPAY-Signature", required = false) String signature,
            @RequestHeader(value = "X-TGIPAY-Event", required = false) String event,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        webhookService.process(rawBody, signature, event, idempotencyKey);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/status/{transactionReference}")
    public ResponseEntity<JsonNode> getPaymentStatus(
            @AuthenticationPrincipal(expression = "username") String phoneNumber,
            @PathVariable String transactionReference) {
        return ResponseEntity.ok(purchaseService.checkPaymentStatus(
                phoneNumber, transactionReference));
    }

    @PutMapping("/status/{transactionReference}")
    public ResponseEntity<JsonNode> updatePaymentStatus(
            @AuthenticationPrincipal(expression = "username") String phoneNumber,
            @PathVariable String transactionReference) {
        return ResponseEntity.ok(purchaseService.checkPaymentStatus(
                phoneNumber, transactionReference));
    }

    @PostMapping("/{transactionReference}/checkout")
    public ResponseEntity<Map<String, String>> createCheckout(
            @AuthenticationPrincipal(expression = "username") String phoneNumber,
            @PathVariable String transactionReference) {
        JsonNode paymentDetails = purchaseService.getCheckoutDetails(phoneNumber, transactionReference);
        return ResponseEntity.ok(Map.of(
                "transactionReference", transactionReference,
                "accessCode", paymentDetails.path("accessCode").asText(),
                "paymentUrl", paymentDetails.path("url").asText()));
    }

    @GetMapping(value = "/{transactionReference}/initiation-response",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getInitiationResponse(
            @AuthenticationPrincipal(expression = "username") String phoneNumber,
            @PathVariable String transactionReference) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(purchaseService.getInitiationResponse(phoneNumber, transactionReference));
    }

    @GetMapping("/{transactionReference}/access-code")
    public ResponseEntity<Map<String, String>> getAccessCode(
            @AuthenticationPrincipal(expression = "username") String phoneNumber,
            @PathVariable String transactionReference) {
        return ResponseEntity.ok(Map.of(
                "transactionReference", transactionReference,
                "accessCode", purchaseService.getAccessCode(phoneNumber, transactionReference)));
    }

    @GetMapping("/callback")
    public ResponseEntity<?> paymentCallback(
            @RequestParam(required = false) String ref,
            @RequestParam(required = false) String transactionReference,
            @RequestHeader(value = "Accept", required = false) String accept) {
        String reference = ref == null || ref.isBlank() ? transactionReference : ref;
        if (reference == null || reference.isBlank() || reference.length() > 160) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid transaction reference"));
        }

        TokenPurchaseStatus verifiedStatus = purchaseService.processCallback(reference);
        Map<String, String> result = Map.of(
                "transactionReference", reference,
                "status", verifiedStatus.name().toLowerCase());
        if (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE)) {
            return ResponseEntity.ok(result);
        }
        if (frontendUrl == null || frontendUrl.isBlank()) {
            return ResponseEntity.ok(result);
        }
        URI redirect = UriComponentsBuilder.fromUriString(frontendUrl)
                .path("/settings")
                .queryParam("paymentStatus", verifiedStatus.name().toLowerCase())
                .queryParam("transactionReference", reference)
                .build()
                .encode()
                .toUri();
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(redirect).build();
    }
}
