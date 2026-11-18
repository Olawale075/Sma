package sms.com.sms.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import sms.com.sms.model.Users;

import java.time.Duration;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
public class TgiPaymentService {

    private static final Logger logger = LoggerFactory.getLogger(TgiPaymentService.class);
    private static final String PAYMENT_ENDPOINT =
            "https://integration-service.tgipay.com/integration/api/v1/payment";
    private static final int MAX_ERROR_DETAILS_LENGTH = 500;

    private final RestTemplate restTemplate;
    private final String integrationKey;

    @Autowired
    public TgiPaymentService(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${tgi.integration-key:}") String integrationKey) {
        this(restTemplateBuilder
                        .setConnectTimeout(Duration.ofSeconds(5))
                        .setReadTimeout(Duration.ofSeconds(10))
                        .build(),
                integrationKey);
    }

    TgiPaymentService(RestTemplate restTemplate, String integrationKey) {
        this.restTemplate = restTemplate;
        this.integrationKey = integrationKey;
    }

    public JsonNode initiatePayment(Users user, String transactionReference, java.math.BigDecimal amount) {
        ensureConfigured();

        String[] name = user.getName().trim().split("\\s+", 2);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customerFirstName", name[0]);
        payload.put("customerLastName", name.length > 1 ? name[1] : "");
        payload.put("customerEmail", user.getEmail());
        payload.put("amount", amount);
        payload.put("transactionReference", transactionReference);
        payload.put("currency", "NGN");

        JsonNode response = exchange(URI.create(PAYMENT_ENDPOINT + "/initiate"), HttpMethod.POST, payload);
        JsonNode responseData = response.path("data");
        logger.info("TGI payment initiation response: status={}, message={}, transactionReference={}",
                response.path("status").asText(""),
                response.path("message").asText(""),
                responseData.path("transactionReference")
                        .asText(response.path("transactionReference").asText("")));
        if (response.has("status") && response.get("status").isBoolean()
                && !response.get("status").asBoolean()) {
            throw new ResponseStatusException(BAD_GATEWAY, "TGI rejected the payment initiation");
        }
        return response;
    }

    public JsonNode getPaymentDetails(String transactionReference) {
        JsonNode response = exchange(paymentUri(transactionReference), HttpMethod.GET, null);
        return validatePaymentDetails(response, transactionReference);
    }

    public JsonNode validatePaymentDetails(JsonNode response, String transactionReference) {
        if (!response.path("status").asBoolean(false)) {
            throw new ResponseStatusException(BAD_GATEWAY, "TGI could not return payment details");
        }
        JsonNode data = response.path("data");
        if (!transactionReference.equals(data.path("transactionReference").asText())) {
            throw new ResponseStatusException(BAD_GATEWAY, "TGI returned a mismatched transaction reference");
        }
        String paymentUrl = data.path("url").asText();
        if (data.path("accessCode").asText().isBlank()) {
            throw new ResponseStatusException(BAD_GATEWAY, "TGI did not return a payment access code");
        }
        try {
            URI uri = URI.create(paymentUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !"merchant.tgipay.com".equalsIgnoreCase(uri.getHost())) {
                throw new IllegalArgumentException("Untrusted payment URL");
            }
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_GATEWAY, "TGI returned an invalid payment URL", exception);
        }
        return data;
    }

//    public JsonNode getPaymentStatus(String transactionReference) {
//        URI uri = UriComponentsBuilder.fromUriString(PAYMENT_ENDPOINT)
//                .pathSegment("status", transactionReference)
//                .build()
//                .encode()
//                .toUri();
//        return exchange(uri, HttpMethod.GET, null);
//    }
    public JsonNode getPaymentStatus(String transactionReference) {

        URI uri = UriComponentsBuilder.fromUriString(PAYMENT_ENDPOINT)
                .pathSegment("status", transactionReference)
                .build()
                .encode()
                .toUri();

        logger.info("TGIPAY STATUS URL: {}", uri);
        logger.info("TGIPAY REQUEST REFERENCE: {}", transactionReference);

        JsonNode response = exchange(uri, HttpMethod.GET, null);

        logger.info("TGIPAY RAW STATUS RESPONSE: {}",
                response != null ? response.toPrettyString() : "NULL");

        return response;
    }
    private JsonNode exchange(URI uri, HttpMethod method, Object body) {
        ensureConfigured();
        HttpHeaders headers = new HttpHeaders();
        headers.set("integration-key", integrationKey);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }

        try {
            JsonNode response = restTemplate.exchange(
                    uri,
                    method,
                    new HttpEntity<>(body, headers),
                    JsonNode.class).getBody();
            if (response == null) {
                throw new ResponseStatusException(BAD_GATEWAY, "TGI returned an empty response");
            }
            return response;
        } catch (HttpStatusCodeException exception) {
            logger.warn("TGI returned HTTP {}: {}",
                    exception.getStatusCode().value(),
                    sanitizeProviderError(exception.getResponseBodyAsString(), body));
            throw new ResponseStatusException(BAD_GATEWAY,
                    "TGI returned an error response (HTTP " + exception.getStatusCode().value()
                            + "). Check server logs for provider details.",
                    exception);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(BAD_GATEWAY, "Unable to communicate with TGI", exception);
        }
    }

    private String sanitizeProviderError(String responseBody, Object requestBody) {
        String details = responseBody == null ? "" : responseBody;
        if (requestBody instanceof Map<?, ?>) {
            for (Object value : ((Map<?, ?>) requestBody).values()) {
                if (value != null && !value.toString().isBlank()) {
                    details = details.replace(value.toString(), "[redacted]");
                }
            }
        }
        details = details.replace(integrationKey, "[redacted]")
                .replaceAll("[\\r\\n\\t]+", " ")
                .trim();
        if (details.length() > MAX_ERROR_DETAILS_LENGTH) {
            return details.substring(0, MAX_ERROR_DETAILS_LENGTH) + "...";
        }
        return details.isBlank() ? "No response body" : details;
    }

    private URI paymentUri(String transactionReference) {
        return UriComponentsBuilder.fromUriString(PAYMENT_ENDPOINT)
                .pathSegment(transactionReference)
                .build()
                .encode()
                .toUri();
    }

    private void ensureConfigured() {
        if (integrationKey == null || integrationKey.isBlank()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "TGI integration is not configured");
        }
    }
}
