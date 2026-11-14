package sms.com.sms.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import sms.com.sms.enums.TokenPurchaseStatus;
import sms.com.sms.enums.TokenType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "token_purchases")
public class TokenPurchase {

    @Id
    @Column(nullable = false, updatable = false)
    private String transactionReference;

    @Column(nullable = false)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TokenType tokenType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal tokenQuantity;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TokenPurchaseStatus status = TokenPurchaseStatus.PENDING;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected TokenPurchase() {
    }

    public TokenPurchase(String transactionReference, String phoneNumber, TokenType tokenType,
                         BigDecimal tokenQuantity, BigDecimal amount) {
        this.transactionReference = transactionReference;
        this.phoneNumber = phoneNumber;
        this.tokenType = tokenType;
        this.tokenQuantity = tokenQuantity;
        this.amount = amount;
        this.status = TokenPurchaseStatus.PENDING;
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

    public TokenPurchaseStatus getStatus() {
        return status;
    }

    public void setStatus(TokenPurchaseStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
