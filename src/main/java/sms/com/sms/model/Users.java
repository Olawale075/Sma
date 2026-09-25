package sms.com.sms.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import sms.com.sms.enums.NotificationPreference;
import sms.com.sms.enums.UserRole;

import java.time.LocalDateTime;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"gasDetectors","sensorReadings"})
@ToString(exclude = {"gasDetectors","sensorReadings"})
@Entity
@DynamicUpdate
@Table(name = "users")
public class Users implements UserDetails {

    @Id
    @Column(name = "phone_number", nullable = false, unique = true)
    @NotNull
    private String phonenumber;

    @Column(nullable = false)
    private String name;

    @Column( unique = true)
    private String email;
    @Column(nullable = false)
    private String crop;
    @Column(nullable = false)
    private String farmSize;
    @Column(nullable = false)
    private String farmName;
    @Column(nullable = false)
    private String farmLocation;

    public boolean getIsVerified() {
        return isVerified;
    }

    public void setIsVerified(boolean isVerified) {
        this.isVerified = isVerified;
    }

    public NotificationPreference getNotificationPreference() {
        return notificationPreference;
    }

    public void setNotificationPreference(NotificationPreference notificationPreference) {
        this.notificationPreference = notificationPreference;
    }
    @Column(nullable = false)
    private Boolean isVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    private NotificationPreference notificationPreference;

    @Transient
    private String otp;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_gas_detector", joinColumns = @JoinColumn(name = "user_phonenumber", referencedColumnName = "phone_number"), inverseJoinColumns = @JoinColumn(name = "gas_detector_mac", referencedColumnName = "mac_address"))
    private Set<GasDetector> gasDetectors = new HashSet<>();

    public void addGasDetector(GasDetector gasDetector) {
        this.gasDetectors.add(gasDetector);
        gasDetector.getUsers().add(this);
    }

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SensorReading> sensorReadings = new ArrayList<>();

    public void addSensorReading(SensorReading reading) {
        this.sensorReadings.add(reading);
        reading.setUser(this);
    }

    public List<SensorReading> getSensorReadings() {
        return sensorReadings;
    }




    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createDateTime;

    public String getPhonenumber() {
        return phonenumber;
    }
private String emailVerificationCode;
@Column(name = "email_verified")
private boolean emailVerified = false;
private String resetToken;
    public void setIsVerified(Boolean isVerified) {
    this.isVerified = isVerified;
}
    @UpdateTimestamp
    private LocalDateTime updateDateTime;

    // --- Manually added methods used in controller/service ---

    public String getOtp() {
        return otp;
    }
public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
    void setOtp(String otp) {
        this.otp = otp;
    }

    public void setPhonenumber(String phonenumber) {
        this.phonenumber = phonenumber;
    }

    public Set<GasDetector> getGasDetectors() {
        return gasDetectors;
    }

    // --- Spring Security UserDetails Implementation ---
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.name()));
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    @Override
    public String getUsername() {
        return phonenumber;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
public String getEmailVerificationCode() {
    return emailVerificationCode;
}

public void setEmailVerificationCode(String emailVerificationCode) {
    this.emailVerificationCode = emailVerificationCode;
}

public boolean isEmailVerified() {
    return emailVerified;
}

public void setEmailVerified(boolean emailVerified) {
    this.emailVerified = emailVerified;
}

public String getResetToken() {
    return resetToken;
}

public void setResetToken(String resetToken) {
    this.resetToken = resetToken;
}

    public String getFarmSize() {
        return farmSize;
    }

    public String getFarmName() {
        return farmName;
    }

    public String getFarmLocation() {
        return farmLocation;
    }

    public String getCrop() {
        return crop;
    }

    public void setCrop(String crop) {
        this.crop = crop;
    }

    public void setFarmSize(String farmSize) {
        this.farmSize = farmSize;
    }

    public void setFarmName(String farmName) {
        this.farmName = farmName;
    }

    public void setFarmLocation(String farmLocation) {
        this.farmLocation = farmLocation;
    }
}
