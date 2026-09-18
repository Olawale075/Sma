package sms.com.sms.dto;

import java.util.HashSet;
import java.util.Set;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import sms.com.sms.enums.NotificationPreference;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {

    @NotBlank(message = "Phone number is required")
    @Size(min = 10, max = 15, message = "Phone number must be between 10 and 15 digits")
    private String phoneNumbers;

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @Email(message = "Invalid email format")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    private String email;

    private NotificationPreference notificationPreference;

//    @Builder.Default
//    private Set<String> macAddresses = new HashSet<>();

    private String crop;
    private String farmName;
    private String farmLocation;
    private String farmSize;

//    // Convenience methods for macAddresses
//    public void addMacAddress(String macAddress) {
//        if (this.macAddresses == null) {
//            this.macAddresses = new HashSet<>();
//        }
//        this.macAddresses.add(macAddress);
//    }
//
//    public void removeMacAddress(String macAddress) {
//        if (this.macAddresses != null) {
//            this.macAddresses.remove(macAddress);
//        }
//    }
//
//    public boolean hasMacAddresses() {
//        return this.macAddresses != null && !this.macAddresses.isEmpty();
//    }
}