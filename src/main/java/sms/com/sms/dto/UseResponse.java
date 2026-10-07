package sms.com.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UseResponse {
    private String name;
    private String email;
    private String phoneNumber;
    private String size;
    private  String crop;
    private String farmName;
    private BigDecimal AITokenBalance;
    private BigDecimal sMSTokenBalance;
    private String farmLocation;
    private String notificationPreference;
    private String farmSize;


}
