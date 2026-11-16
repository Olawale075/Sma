package sms.com.sms.dto;

import sms.com.sms.enums.TokenPurchaseStatus;
import sms.com.sms.enums.TokenType;
import sms.com.sms.model.TokenPurchase;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentSummaryResponse {

    private final String transactionReference;
    private final String phoneNumber;
    private final TokenType tokenType;
    private final BigDecimal tokenQuantity;
    private final BigDecimal amount;
    private final String currency;
    private final TokenPurchaseStatus status;
    private final LocalDateTime createdAt;

    public PaymentSummaryResponse(TokenPurchase purchase) {
        this.transactionReference = purchase.getTransactionReference();
        this.phoneNumber = purchase.getPhoneNumber();
        this.tokenType = purchase.getTokenType();
        this.tokenQuantity = purchase.getTokenQuantity();
        this.amount = purchase.getAmount();
        this.currency = "NGN";
        this.status = purchase.getStatus();
        this.createdAt = purchase.getCreatedAt();
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public String getPhoneNumber() {
        return phoneNumber;
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

    public TokenPurchaseStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
