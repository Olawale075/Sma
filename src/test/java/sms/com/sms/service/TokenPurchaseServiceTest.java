package sms.com.sms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import sms.com.sms.enums.TokenPurchaseStatus;
import sms.com.sms.enums.TokenType;
import sms.com.sms.model.TokenPurchase;
import sms.com.sms.model.Users;
import sms.com.sms.repository.TokenPurchaseRepository;
import sms.com.sms.repository.UsersRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenPurchaseServiceTest {

    @Test
    void resolvesPurchaseUserFromAuthenticatedPhoneNumber() {
        TokenPurchaseRepository purchaseRepository = mock(TokenPurchaseRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        TgiPaymentService tgiPaymentService = mock(TgiPaymentService.class);
        TokenPurchaseService service = new TokenPurchaseService(
                purchaseRepository, usersRepository, tgiPaymentService);
        String phoneNumber = "2348012345678";
        Users user = new Users();
        user.setPhonenumber(phoneNumber);
        user.setName("Frank Johnson");
        user.setEmail("frank@example.com");

        when(usersRepository.findById(phoneNumber)).thenReturn(Optional.of(user));
        when(tgiPaymentService.initiatePayment(eq(user), anyString(), eq(new BigDecimal("30.00"))))
                .thenAnswer(invocation -> {
                    var response = new ObjectMapper().createObjectNode();
                    response.put("status", true);
                    response.putObject("data")
                            .put("transactionReference", invocation.<String>getArgument(1))
                            .put("accessCode", "test-access-code")
                            .put("url", "https://merchant.tgipay.com/pay-now/test-access-code")
                            .put("amount", 30.00)
                            .put("currency", "NGN");
                    return response;
                });
        when(tgiPaymentService.validatePaymentDetails(any(JsonNode.class), anyString()))
                .thenAnswer(invocation -> invocation.<JsonNode>getArgument(0).path("data"));
        var response = service.initiatePurchase(phoneNumber, TokenType.AI_TOKEN, 3);
        var purchaseCaptor = forClass(TokenPurchase.class);
        var referenceCaptor = forClass(String.class);

        assertEquals(new BigDecimal("30.00"), response.getAmount());
        assertEquals("https://merchant.tgipay.com/pay-now/test-access-code", response.getPaymentUrl());
        assertEquals("test-access-code", response.getAccessCode());
        verify(usersRepository).findById(phoneNumber);
        verify(tgiPaymentService).initiatePayment(eq(user), referenceCaptor.capture(),
                eq(new BigDecimal("30.00")));
        assertEquals(referenceCaptor.getValue(), response.getTgiInitiationResponse()
                .path("data").path("transactionReference").asText());
        verify(purchaseRepository, org.mockito.Mockito.times(3)).saveAndFlush(purchaseCaptor.capture());
        assertEquals(phoneNumber, purchaseCaptor.getValue().getPhoneNumber());
        assertEquals(response.getTgiInitiationResponse().toString(),
                purchaseCaptor.getAllValues().get(1).getTgiInitiationResponse());
        assertEquals("test-access-code", purchaseCaptor.getAllValues().get(2).getTgiAccessCode());
        verify(tgiPaymentService, never()).getPaymentDetails(anyString());
    }

    @Test
    void rejectsPurchaseWhenAuthenticatedPhoneNumberCannotBeResolved() {
        TokenPurchaseRepository purchaseRepository = mock(TokenPurchaseRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        TgiPaymentService tgiPaymentService = mock(TgiPaymentService.class);
        TokenPurchaseService service = new TokenPurchaseService(
                purchaseRepository, usersRepository, tgiPaymentService);

        when(usersRepository.findById("2348012345678")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.initiatePurchase("2348012345678", TokenType.AI_TOKEN, 3));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verify(tgiPaymentService, never()).initiatePayment(any(), anyString(), any());
    }

    @Test
    void retrievesSavedInitiationResponseOnlyForTheOwningUser() {
        TokenPurchaseRepository purchaseRepository = mock(TokenPurchaseRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        TgiPaymentService tgiPaymentService = mock(TgiPaymentService.class);
        TokenPurchaseService service = new TokenPurchaseService(
                purchaseRepository, usersRepository, tgiPaymentService);
        String reference = "test-reference";
        String providerResponse = "{\"status\":true,\"data\":{\"transactionReference\":\"test-reference\"}}";
        TokenPurchase purchase = new TokenPurchase(reference, "2348012345678", TokenType.AI_TOKEN,
                BigDecimal.valueOf(10), new BigDecimal("100.00"));
        purchase.setTgiInitiationResponse(providerResponse);

        when(purchaseRepository.findByTransactionReferenceAndPhoneNumber(reference, "2348012345678"))
                .thenReturn(Optional.of(purchase));

        assertEquals(providerResponse,
                service.getInitiationResponse("2348012345678", reference));
    }

    @Test
    void retrievesSavedAccessCodeOnlyForTheOwningUser() {
        TokenPurchaseRepository purchaseRepository = mock(TokenPurchaseRepository.class);
        TokenPurchaseService service = new TokenPurchaseService(
                purchaseRepository, mock(UsersRepository.class), mock(TgiPaymentService.class));
        String reference = "test-reference";
        TokenPurchase purchase = new TokenPurchase(reference, "2348012345678", TokenType.AI_TOKEN,
                BigDecimal.valueOf(10), new BigDecimal("100.00"));
        purchase.setTgiAccessCode("test-access-code");

        when(purchaseRepository.findByTransactionReferenceAndPhoneNumber(reference, "2348012345678"))
                .thenReturn(Optional.of(purchase));

        assertEquals("test-access-code",
                service.getAccessCode("2348012345678", reference));
    }

    @Test
    void obtainsAndSavesCheckoutDetailsSeparatelyFromInitiation() {
        TokenPurchaseRepository purchaseRepository = mock(TokenPurchaseRepository.class);
        TgiPaymentService tgiPaymentService = mock(TgiPaymentService.class);
        TokenPurchaseService service = new TokenPurchaseService(
                purchaseRepository, mock(UsersRepository.class), tgiPaymentService);
        String reference = "test-reference";
        TokenPurchase purchase = new TokenPurchase(reference, "2348012345678", TokenType.AI_TOKEN,
                BigDecimal.TEN, new BigDecimal("100.00"));
        var details = new ObjectMapper().createObjectNode()
                .put("transactionReference", reference)
                .put("amount", 100.00)
                .put("currency", "NGN")
                .put("accessCode", "test-access-code")
                .put("url", "https://merchant.tgipay.com/pay-now/test-access-code");

        when(purchaseRepository.findByTransactionReferenceAndPhoneNumber(reference, "2348012345678"))
                .thenReturn(Optional.of(purchase));
        when(tgiPaymentService.getPaymentDetails(reference)).thenReturn(details);

        var response = service.getCheckoutDetails("2348012345678", reference);

        assertEquals("test-access-code", response.path("accessCode").asText());
        assertEquals("https://merchant.tgipay.com/pay-now/test-access-code",
                response.path("url").asText());
        assertEquals("test-access-code", purchase.getTgiAccessCode());
        verify(purchaseRepository).saveAndFlush(purchase);
    }

    @Test
    void pricesBothTokenTypesFromTheConfiguredRates() {
        assertEquals(new BigDecimal("100.00"),
                TokenPurchaseService.calculatePrice(TokenType.AI_TOKEN, BigDecimal.TEN));
        assertEquals(new BigDecimal("100.00"),
                TokenPurchaseService.calculatePrice(TokenType.SMS_TOKEN, BigDecimal.valueOf(35)));
        assertEquals(new BigDecimal("2.86"),
                TokenPurchaseService.calculatePrice(TokenType.SMS_TOKEN, BigDecimal.ONE));
    }

    @Test
    void onlyCreditsAiTokensOnceAfterVerifiedSuccess() throws Exception {
        TokenPurchaseRepository purchaseRepository = mock(TokenPurchaseRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        TgiPaymentService tgiPaymentService = mock(TgiPaymentService.class);
        TokenPurchaseService service = new TokenPurchaseService(
                purchaseRepository, usersRepository, tgiPaymentService);
        String reference = "test-reference";
        TokenPurchase purchase = new TokenPurchase(
                reference, "2348012345678", TokenType.AI_TOKEN,
                BigDecimal.valueOf(10), new BigDecimal("100.00"));
        var success = new ObjectMapper().readTree(
                "{\"data\":{\"transactionReference\":\"test-reference\",\"status\":\"success\","
                        + "\"amount\":100,\"currency\":\"NGN\"}}");

        when(purchaseRepository.findById(reference)).thenReturn(Optional.of(purchase));
        when(tgiPaymentService.getPaymentStatus(reference)).thenReturn(success);
        when(purchaseRepository.updateStatusIfCurrent(
                reference, TokenPurchaseStatus.PENDING, TokenPurchaseStatus.SUCCESS)).thenReturn(1, 0);
        when(purchaseRepository.findStatusByTransactionReference(reference))
                .thenReturn(Optional.of(TokenPurchaseStatus.SUCCESS));
        when(usersRepository.creditAiTokens("2348012345678", BigDecimal.TEN)).thenReturn(1);

        assertEquals(TokenPurchaseStatus.SUCCESS, service.processCallback(reference));
        assertEquals(TokenPurchaseStatus.SUCCESS, service.processCallback(reference));
        verify(usersRepository).creditAiTokens("2348012345678", BigDecimal.TEN);
        verify(usersRepository, never()).creditSmsTokens(eq("2348012345678"), eq(BigDecimal.TEN));
    }

    @Test
    void creditsSmsTokensOnlyOnceAfterVerifiedSuccess() throws Exception {
        TokenPurchaseRepository purchaseRepository = mock(TokenPurchaseRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        TgiPaymentService tgiPaymentService = mock(TgiPaymentService.class);
        TokenPurchaseService service = new TokenPurchaseService(
                purchaseRepository, usersRepository, tgiPaymentService);
        String reference = "sms-test-reference";
        TokenPurchase purchase = new TokenPurchase(
                reference, "2348012345678", TokenType.SMS_TOKEN,
                BigDecimal.valueOf(35), new BigDecimal("100.00"));
        var success = new ObjectMapper().readTree(
                "{\"data\":{\"transactionReference\":\"sms-test-reference\",\"status\":\"success\","
                        + "\"amount\":100,\"currency\":\"NGN\"}}");

        when(purchaseRepository.findById(reference)).thenReturn(Optional.of(purchase));
        when(tgiPaymentService.getPaymentStatus(reference)).thenReturn(success);
        when(purchaseRepository.updateStatusIfCurrent(
                reference, TokenPurchaseStatus.PENDING, TokenPurchaseStatus.SUCCESS)).thenReturn(1, 0);
        when(purchaseRepository.findStatusByTransactionReference(reference))
                .thenReturn(Optional.of(TokenPurchaseStatus.SUCCESS));
        when(usersRepository.creditSmsTokens("2348012345678", BigDecimal.valueOf(35))).thenReturn(1);

        assertEquals(TokenPurchaseStatus.SUCCESS, service.processCallback(reference));
        assertEquals(TokenPurchaseStatus.SUCCESS, service.processCallback(reference));
        verify(usersRepository).creditSmsTokens("2348012345678", BigDecimal.valueOf(35));
        verify(usersRepository, never()).creditAiTokens(
                eq("2348012345678"), eq(BigDecimal.valueOf(35)));
    }

    @Test
    void acceptsProviderReferenceFieldWhenVerifyingPaymentStatus() throws Exception {
        TokenPurchaseRepository purchaseRepository = mock(TokenPurchaseRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        TgiPaymentService tgiPaymentService = mock(TgiPaymentService.class);
        TokenPurchaseService service = new TokenPurchaseService(
                purchaseRepository, usersRepository, tgiPaymentService);
        String reference = "provider-reference-test";
        TokenPurchase purchase = new TokenPurchase(
                reference, "2348012345678", TokenType.AI_TOKEN,
                BigDecimal.TEN, new BigDecimal("100.00"));
        var success = new ObjectMapper().readTree(
                "{\"data\":{\"reference\":\"provider-reference-test\",\"status\":\"success\","
                        + "\"amount\":100,\"currency\":\"NGN\"}}");

        when(purchaseRepository.findById(reference)).thenReturn(Optional.of(purchase));
        when(tgiPaymentService.getPaymentStatus(reference)).thenReturn(success);
        when(purchaseRepository.updateStatusIfCurrent(
                reference, TokenPurchaseStatus.PENDING, TokenPurchaseStatus.SUCCESS)).thenReturn(1);
        when(usersRepository.creditAiTokens("2348012345678", BigDecimal.TEN)).thenReturn(1);

        assertEquals(TokenPurchaseStatus.SUCCESS, service.processCallback(reference));
        verify(usersRepository).creditAiTokens("2348012345678", BigDecimal.TEN);
    }

    @Test
    void processesSignedWebhookStatusAndDoesNotCreditDuplicateDelivery() throws Exception {
        TokenPurchaseRepository purchaseRepository = mock(TokenPurchaseRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        TgiPaymentService tgiPaymentService = mock(TgiPaymentService.class);
        TokenPurchaseService service = new TokenPurchaseService(
                purchaseRepository, usersRepository, tgiPaymentService);
        String reference = "test-webhook-reference";
        TokenPurchase purchase = new TokenPurchase(reference, "2348012345678", TokenType.AI_TOKEN,
                BigDecimal.valueOf(10), new BigDecimal("100.00"));
        var webhook = new ObjectMapper().readTree(
                "{\"event\":\"charge.success\",\"data\":{\"transactionReference\":\""
                        + reference + "\",\"status\":\"SUCCESS\",\"amount\":100,"
                        + "\"currency\":\"NGN\"}}");

        when(purchaseRepository.findById(reference)).thenReturn(Optional.of(purchase));
        when(purchaseRepository.updateStatusIfCurrent(
                reference, TokenPurchaseStatus.PENDING, TokenPurchaseStatus.SUCCESS)).thenReturn(1, 0);
        when(purchaseRepository.findStatusByTransactionReference(reference))
                .thenReturn(Optional.of(TokenPurchaseStatus.SUCCESS));
        when(usersRepository.creditAiTokens("2348012345678", BigDecimal.TEN)).thenReturn(1);

        assertEquals(TokenPurchaseStatus.SUCCESS,
                service.processWebhook(webhook, "charge.success", "webhook-event-123"));
        assertEquals(TokenPurchaseStatus.SUCCESS,
                service.processWebhook(webhook, "charge.success", "webhook-event-123"));
        verify(usersRepository).creditAiTokens("2348012345678", BigDecimal.TEN);
    }

    @Test
    void rejectsWebhookWhenEventAndPaymentStatusDoNotMatch() throws Exception {
        TokenPurchaseService service = new TokenPurchaseService(
                mock(TokenPurchaseRepository.class), mock(UsersRepository.class),
                mock(TgiPaymentService.class));
        var webhook = new ObjectMapper().readTree(
                "{\"event\":\"charge.failed\",\"data\":{\"transactionReference\":\"test-reference\","
                        + "\"status\":\"SUCCESS\",\"amount\":100,\"currency\":\"NGN\"}}");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.processWebhook(webhook, "charge.failed", "webhook-event-123"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
}
