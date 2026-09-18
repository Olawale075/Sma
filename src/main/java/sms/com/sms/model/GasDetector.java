package sms.com.sms.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.DynamicUpdate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = "users")
@ToString(exclude = "users")
@Entity
@DynamicUpdate
@Table(name = "gas_detectors")
public class GasDetector {

    // ==================================================
    // PRIMARY KEY
    // ==================================================
    @Id
    @Column(name = "mac_address", nullable = false, unique = true)
    private String macAddress;

    // ==================================================
    // SENSOR DATA
    // ==================================================
    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "humidity")
    private Double humidity;

    @Column(name = "co2")
    private Double co2;

    @Column(name = "status")
    private Boolean status;

    // ==================================================
    // CONFIGURATION
    // ==================================================
    @Column(name = "co2_threshold")
    private Double co2Threshold;

    @Column(name = "wifi_ssid")
    private String wifiSsid;

    @Column(name = "wifi_password")
    private String wifiPassword;

    // ==================================================
    // LOCATION
    // ==================================================
    @Column(name = "location")
    private String location;

    // ==================================================
    // RELATIONSHIP (INVERSE SIDE)
    // ==================================================
    @ManyToMany(mappedBy = "gasDetectors", fetch = FetchType.LAZY)
    private Set<Users> users = new HashSet<>();

    // ==================================================
    // HELPER METHOD
    // ==================================================
    /**
     * Add a user to this detector
     */
    public void addUser(Users user) {
        if (this.users == null) {
            this.users = new HashSet<>();
        }
        this.users.add(user);
    }

    /**
     * Remove a user from this detector
     */
    public void removeUser(Users user) {
        if (this.users != null) {
            this.users.remove(user);
        }
    }

    /**
     * Check if detector is in hazardous state
     */
    public boolean isHazardous() {
        return this.status != null && this.status;
    }

    /**
     * Check if CO2 exceeds threshold
     */
    public boolean isCo2Exceeded() {
        if (this.co2 == null || this.co2Threshold == null) {
            return false;
        }
        return this.co2 > this.co2Threshold;
    }
}