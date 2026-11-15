package sms.com.sms.dto;

import com.fasterxml.jackson.databind.JsonNode;
import sms.com.sms.enums.TokenType;

import java.math.BigDecimal;

public class TokenPurchaseResponse {

    private final String transactionReference;
    private final String paymentUrl;
    private final TokenType tokenType;
    private final BigDecimal tokenQuantity;
    private final BigDecimal amount;
    private final String currency;
    private final String status;
    private final String accessCode;
    private final JsonNode tgiInitiationResponse;

    public TokenPurchaseResponse(String transactionReference, String paymentUrl, TokenType tokenType,
                                 BigDecimal tokenQuantity, BigDecimal amount, String currency, String status,
                                 String accessCode, JsonNode tgiInitiationResponse) {
        this.transactionReference = transactionReference;
        this.paymentUrl = paymentUrl;
        this.tokenType = tokenType;
        this.tokenQuantity = tokenQuantity;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.accessCode = accessCode;
        this.tgiInitiationResponse = tgiInitiationResponse;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public String getPaymentUrl() {
        return paymentUrl;
    }

    public TokenType getTokenType() {
        return tokenType;
    }

    public BigDecimal getTokenQuantity() {
        return tokenQuantity;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getStatus() {
        return status;
    }

    public String getAccessCode() {
        return accessCode;
    }

    public JsonNode getTgiInitiationResponse() {
        return tgiInitiationResponse;
    }
}
