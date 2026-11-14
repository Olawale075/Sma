package sms.com.sms.dto;

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

    public TokenPurchaseResponse(String transactionReference, String paymentUrl, TokenType tokenType,
                                 BigDecimal tokenQuantity, BigDecimal amount, String currency, String status) {
        this.transactionReference = transactionReference;
        this.paymentUrl = paymentUrl;
        this.tokenType = tokenType;
        this.tokenQuantity = tokenQuantity;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
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
}
