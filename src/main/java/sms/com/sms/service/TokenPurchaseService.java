package sms.com.sms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import sms.com.sms.dto.TokenPurchaseResponse;
import sms.com.sms.enums.TokenPurchaseStatus;
import sms.com.sms.enums.TokenType;
import sms.com.sms.model.TokenPurchase;
import sms.com.sms.model.Users;
import sms.com.sms.repository.TokenPurchaseRepository;
import sms.com.sms.repository.UsersRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.UUID;

@Service
public class TokenPurchaseService {

    private static final BigDecimal MAX_TOKEN_QUANTITY = BigDecimal.valueOf(100_000);
    private static final BigDecimal AI_TOKEN_PRICE = BigDecimal.TEN;
    private static final BigDecimal SMS_PRICE_PER_TOKEN = BigDecimal.valueOf(100)
            .divide(BigDecimal.valueOf(35), 8, RoundingMode.HALF_UP);

    private final TokenPurchaseRepository purchaseRepository;
    private final UsersRepository usersRepository;
    private final TgiPaymentService tgiPaymentService;

    public TokenPurchaseService(TokenPurchaseRepository purchaseRepository,
                                UsersRepository usersRepository,
                                TgiPaymentService tgiPaymentService) {
        this.purchaseRepository = purchaseRepository;
        this.usersRepository = usersRepository;
        this.tgiPaymentService = tgiPaymentService;
    }

    public TokenPurchaseResponse initiatePurchase(
            String phoneNumber, TokenType tokenType, Integer requestedQuantity) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Authenticated user could not be resolved");
        }
        Users user = usersRepository.findById(phoneNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Authenticated user could not be resolved"));
        if (tokenType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "tokenType is required and must be AI_TOKEN or SMS_TOKEN");
        }
        if (requestedQuantity == null || requestedQuantity <= 0
                || requestedQuantity > MAX_TOKEN_QUANTITY.intValue()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "tokenQuantity must be a whole number between 1 and 100000");
        }
        if (user.getName() == null || user.getName().isBlank()
                || user.getEmail() == null || user.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A name and email address are required before purchasing tokens");
        }

        BigDecimal quantity = BigDecimal.valueOf(requestedQuantity);
        BigDecimal amount = calculatePrice(tokenType, quantity);
        String reference = UUID.randomUUID().toString();
        TokenPurchase purchase = new TokenPurchase(
                reference, user.getPhonenumber(), tokenType, quantity, amount);
        purchaseRepository.saveAndFlush(purchase);

        JsonNode initiation = tgiPaymentService.initiatePayment(user, reference, amount);
        purchase.setTgiInitiationResponse(initiation.toString());
        purchaseRepository.saveAndFlush(purchase);

        String providerReference = initiation.path("data").path("transactionReference")
                .asText(initiation.path("transactionReference").asText(""));
        if (providerReference.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "TGI did not return a transaction reference after initiating payment");
        }
        if (!reference.equals(providerReference)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "TGI returned a mismatched transaction reference");
        }
        JsonNode paymentDetails = tgiPaymentService.validatePaymentDetails(initiation, providerReference);
        verifyAmountIfProvided(purchase, paymentDetails);
        String paymentUrl = paymentDetails.path("url").asText();
        String accessCode = paymentDetails.path("accessCode").asText();
        purchase.setTgiAccessCode(accessCode);
        purchase.setTgiPaymentUrl(paymentUrl);
        purchaseRepository.saveAndFlush(purchase);

        return new TokenPurchaseResponse(reference, paymentUrl, tokenType, quantity, amount, "NGN",
                TokenPurchaseStatus.PENDING.name(), accessCode, initiation);
    }

    @Transactional
    public JsonNode getCheckoutDetails(String phoneNumber, String transactionReference) {
        TokenPurchase purchase = getOwnedPurchase(phoneNumber, transactionReference);
        if (purchase.getTgiAccessCode() != null && !purchase.getTgiAccessCode().isBlank()
                && purchase.getTgiPaymentUrl() != null && !purchase.getTgiPaymentUrl().isBlank()) {
            return JsonNodeFactory.instance.objectNode()
                    .put("transactionReference", purchase.getTransactionReference())
                    .put("accessCode", purchase.getTgiAccessCode())
                    .put("url", purchase.getTgiPaymentUrl())
                    .put("amount", purchase.getAmount())
                    .put("currency", "NGN");
        }
        JsonNode paymentDetails = tgiPaymentService.getPaymentDetails(transactionReference);
        verifyAmountIfProvided(purchase, paymentDetails);
        String accessCode = paymentDetails.path("accessCode").asText();
        if (accessCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "TGI did not return a payment access code");
        }
        purchase.setTgiAccessCode(accessCode);
        purchase.setTgiPaymentUrl(paymentDetails.path("url").asText());
        purchaseRepository.saveAndFlush(purchase);
        return paymentDetails;
    }

    @Transactional
    public JsonNode checkPaymentStatus(String phoneNumber, String transactionReference) {
        TokenPurchase purchase = getOwnedPurchase(phoneNumber, transactionReference);
        JsonNode status = tgiPaymentService.getPaymentStatus(transactionReference);
        applyVerifiedStatus(purchase, status);
        return status;
    }

    public String getInitiationResponse(String phoneNumber, String transactionReference) {
        TokenPurchase purchase = getOwnedPurchase(phoneNumber, transactionReference);
        String response = purchase.getTgiInitiationResponse();
        if (response == null || response.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "TGI initiation response is not available for this purchase");
        }
        return response;
    }

    public String getAccessCode(String phoneNumber, String transactionReference) {
        TokenPurchase purchase = getOwnedPurchase(phoneNumber, transactionReference);
        if (purchase.getTgiAccessCode() == null || purchase.getTgiAccessCode().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "TGI access code is not available for this purchase");
        }
        return purchase.getTgiAccessCode();
    }

    @Transactional
    public TokenPurchaseStatus processCallback(String transactionReference) {
        TokenPurchase purchase = purchaseRepository.findById(transactionReference)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
        JsonNode status = tgiPaymentService.getPaymentStatus(transactionReference);
        return applyVerifiedStatus(purchase, status);
    }

    @Transactional
    public TokenPurchaseStatus processWebhook(JsonNode webhook, String eventHeader, String idempotencyKey) {
        if (eventHeader == null || eventHeader.isBlank()
                || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "TGI webhook event and idempotency headers are required");
        }
        String event = webhook.path("event").asText("");
        if (!event.equals(eventHeader)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "TGI webhook event header does not match the payload");
        }

        JsonNode data = webhook.path("data");
        String transactionReference = data.path("transactionReference").asText("");
        String providerStatus = data.path("status").asText("").toUpperCase(Locale.ROOT);
        boolean successfulEvent = "charge.success".equals(event) && "SUCCESS".equals(providerStatus);
        boolean failedEvent = "charge.failed".equals(event)
                && ("FAILED".equals(providerStatus) || "FAILURE".equals(providerStatus));
        if (!successfulEvent && !failedEvent) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "TGI webhook event and transaction status are invalid or unsupported");
        }
        if (transactionReference.isBlank() || transactionReference.length() > 160) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "TGI webhook transaction reference is invalid");
        }
        if (!data.path("amount").isNumber()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "TGI webhook amount is required and must be numeric");
        }
        if (!"NGN".equalsIgnoreCase(data.path("currency").asText())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "TGI webhook currency must be NGN");
        }

        TokenPurchase purchase = purchaseRepository.findById(transactionReference)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
        return applyVerifiedStatus(purchase, webhook);
    }

    TokenPurchaseStatus applyVerifiedStatus(TokenPurchase purchase, JsonNode response) {
        JsonNode data = response.path("data");
        if (!purchase.getTransactionReference().equals(data.path("transactionReference").asText())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "TGI returned a mismatched transaction reference");
        }
        verifyAmountIfProvided(purchase, data);

        String providerStatus = data.path("status").asText("").toLowerCase(Locale.ROOT);
        if ("success".equals(providerStatus)) {
            if (purchaseRepository.updateStatusIfCurrent(purchase.getTransactionReference(),
                    TokenPurchaseStatus.PENDING, TokenPurchaseStatus.SUCCESS) == 1) {
                int credited = purchase.getTokenType() == TokenType.AI_TOKEN
                        ? usersRepository.creditAiTokens(purchase.getPhoneNumber(), purchase.getTokenQuantity())
                        : usersRepository.creditSmsTokens(purchase.getPhoneNumber(), purchase.getTokenQuantity());
                if (credited != 1) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Token purchase user was not found");
                }
                purchase.setStatus(TokenPurchaseStatus.SUCCESS);
            } else {
                purchase.setStatus(purchaseRepository.findStatusByTransactionReference(
                                purchase.getTransactionReference())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"))
                        );
            }
        } else if ("failed".equals(providerStatus) || "failure".equals(providerStatus)) {
            if (purchaseRepository.updateStatusIfCurrent(purchase.getTransactionReference(),
                    TokenPurchaseStatus.PENDING, TokenPurchaseStatus.FAILED) == 1) {
                purchase.setStatus(TokenPurchaseStatus.FAILED);
            } else {
                purchase.setStatus(purchaseRepository.findStatusByTransactionReference(
                                purchase.getTransactionReference())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"))
                        );
            }
        }
        return purchase.getStatus();
    }

    static BigDecimal calculatePrice(TokenType tokenType, BigDecimal quantity) {
        BigDecimal amount = tokenType == TokenType.AI_TOKEN
                ? quantity.multiply(AI_TOKEN_PRICE)
                : quantity.multiply(SMS_PRICE_PER_TOKEN);
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private TokenPurchase getOwnedPurchase(String phoneNumber, String transactionReference) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Authenticated user could not be resolved");
        }
        if (transactionReference == null || transactionReference.isBlank()
                || transactionReference.length() > 160) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid transaction reference");
        }
        return purchaseRepository.findByTransactionReferenceAndPhoneNumber(transactionReference, phoneNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
    }

    private void verifyAmountIfProvided(TokenPurchase purchase, JsonNode data) {
        JsonNode amount = data.get("amount");
        if (amount != null && amount.isNumber()
                && amount.decimalValue().compareTo(purchase.getAmount()) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "TGI payment amount does not match the purchase");
        }
        JsonNode currency = data.get("currency");
        if (currency != null && !currency.asText().equalsIgnoreCase("NGN")) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "TGI payment currency does not match the purchase");
        }
    }
}
