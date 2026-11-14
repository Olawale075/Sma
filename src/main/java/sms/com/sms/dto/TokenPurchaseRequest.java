package sms.com.sms.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Max;
import sms.com.sms.enums.TokenType;

public class TokenPurchaseRequest {

    @NotNull
    private TokenType tokenType;

    @NotNull
    @Positive
    @Max(100000)
    private Integer tokenQuantity;

    public TokenType getTokenType() {
        return tokenType;
    }

    public void setTokenType(TokenType tokenType) {
        this.tokenType = tokenType;
    }

    public Integer getTokenQuantity() {
        return tokenQuantity;
    }

    public void setTokenQuantity(Integer tokenQuantity) {
        this.tokenQuantity = tokenQuantity;
    }
}
