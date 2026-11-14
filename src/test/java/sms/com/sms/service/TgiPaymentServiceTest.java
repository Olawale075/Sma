package sms.com.sms.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;
import sms.com.sms.model.Users;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class TgiPaymentServiceTest {

    @Test
    void checksStatusWithEncodedReferenceAndServerSideIntegrationKey() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        String reference = "TGI|20261021115200|ABC12345";

        server.expect(requestTo("https://integration-service.tgipay.com/integration/api/v1/payment/status/"
                        + "TGI%7C20261021115200%7CABC12345"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("integration-key", "test-public-key"))
                .andRespond(withSuccess("{\"status\":\"SUCCESS\"}", MediaType.APPLICATION_JSON));

        TgiPaymentService service = new TgiPaymentService(restTemplate, "test-public-key");

        assertEquals("SUCCESS", service.getPaymentStatus(reference).get("status").asText());
        server.verify();
    }

    @Test
    void rejectsStatusChecksWhenIntegrationKeyIsNotConfigured() {
        TgiPaymentService service = new TgiPaymentService(new RestTemplate(), "");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getPaymentStatus("TGI|reference"));

        assertEquals(SERVICE_UNAVAILABLE, exception.getStatusCode());
    }

    @Test
    void reportsProviderHttpErrorsSeparatelyFromConnectionFailures() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://integration-service.tgipay.com/integration/api/v1/payment/initiate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Amount is invalid\"}"));

        Users user = new Users();
        user.setName("Frank Johnson");
        user.setEmail("frank@example.com");
        TgiPaymentService service = new TgiPaymentService(restTemplate, "test-public-key");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.initiatePayment(user, "test-reference", new BigDecimal("1000000.00")));

        assertEquals(BAD_GATEWAY, exception.getStatusCode());
        assertTrue(exception.getReason().contains("HTTP 400"));
        server.verify();
    }

    @Test
    void initiatesPaymentFromBackendAndFetchesTheTgiCheckoutUrl() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        String reference = "557dd54d-d27f-4ae2-96f0-eeb72f0c15c1";

        server.expect(requestTo("https://integration-service.tgipay.com/integration/api/v1/payment/initiate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("integration-key", "test-public-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"customerFirstName\":\"Frank\",\"customerLastName\":\"Johnson\","
                        + "\"customerEmail\":\"frank@example.com\",\"amount\":100.00,"
                        + "\"transactionReference\":\"" + reference + "\",\"currency\":\"NGN\"}"))
                .andRespond(withSuccess("{\"transactionReference\":\"" + reference + "\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://integration-service.tgipay.com/integration/api/v1/payment/" + reference))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("integration-key", "test-public-key"))
                .andRespond(withSuccess("{\"status\":true,\"data\":{\"transactionReference\":\""
                        + reference + "\",\"url\":\"https://merchant.tgipay.com/pay-now/access\"}}",
                        MediaType.APPLICATION_JSON));

        Users user = new Users();
        user.setName("Frank Johnson");
        user.setEmail("frank@example.com");
        TgiPaymentService service = new TgiPaymentService(restTemplate, "test-public-key");

        service.initiatePayment(user, reference, new BigDecimal("100.00"));
        assertEquals("https://merchant.tgipay.com/pay-now/access", service.getPaymentUrl(reference));
        server.verify();
    }
}
