package sms.com.sms.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)  // Ignores extra fields like "size"
public class RegisterRequest {
 // Maps JSON "phoneNumbers" to this field
    private String phoneNumber;

    private String fullName;

    private String email;
    private String password;
    private String crop;
    private String farmName;
    private String farmLocation;
    private String farmSize;

}