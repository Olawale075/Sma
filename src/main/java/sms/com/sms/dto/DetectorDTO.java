package sms.com.sms.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.util.Set;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DetectorDTO {

    @NotBlank(message = "MAC address cannot be blank")
    private String macAddress;

    private Boolean status;

    @Min(value = -50, message = "Temperature cannot be less than -50°C")
    @Max(value = 150, message = "Temperature cannot be more than 150°C")
    private Double temperature;

    @Min(value = 0, message = "Humidity cannot be less than 0%")
    @Max(value = 100, message = "Humidity cannot be more than 100%")
    private Double humidity;

    @Min(value = 0, message = "CO2 cannot be negative")
    private Double co2;

    @Min(value = 50, message = "CO2 Threshold must be at least 50 ppm")
    @Max(value = 10000, message = "CO2 Threshold cannot exceed 10000 ppm")
    private Double co2Threshold;

    private String wifiSsid;

    private String wifiPassword;

    private String location;

    private Set<String> phoneNumbers;

    // ==================================================
    // CONSTRUCTORS
    // ==================================================
    public DetectorDTO() {}

    public DetectorDTO(String macAddress, Boolean status, Double temperature, Double humidity,
                       Double co2, Double co2Threshold, String wifiSsid, String wifiPassword,
                       String location, Set<String> phoneNumbers) {
        this.macAddress = macAddress;
        this.status = status;
        this.temperature = temperature;
        this.humidity = humidity;
        this.co2 = co2;
        this.co2Threshold = co2Threshold;
        this.wifiSsid = wifiSsid;
        this.wifiPassword = wifiPassword;
        this.location = location;
        this.phoneNumbers = phoneNumbers;
    }

    // ==================================================
    // GETTERS
    // ==================================================
    public String getMacAddress() {
        return macAddress;
    }

    public Boolean getStatus() {
        return status;
    }

    public Double getTemperature() {
        return temperature;
    }

    public Double getHumidity() {
        return humidity;
    }

    public Double getCo2() {
        return co2;
    }

    public Double getCo2Threshold() {
        return co2Threshold;
    }

    public String getWifiSsid() {
        return wifiSsid;
    }

    public String getWifiPassword() {
        return wifiPassword;
    }

    public String getLocation() {
        return location;
    }

    public Set<String> getPhoneNumbers() {
        return phoneNumbers;
    }

    // ==================================================
    // SETTERS
    // ==================================================
    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public void setHumidity(Double humidity) {
        this.humidity = humidity;
    }

    public void setCo2(Double co2) {
        this.co2 = co2;
    }

    public void setCo2Threshold(Double co2Threshold) {
        this.co2Threshold = co2Threshold;
    }

    public void setWifiSsid(String wifiSsid) {
        this.wifiSsid = wifiSsid;
    }

    public void setWifiPassword(String wifiPassword) {
        this.wifiPassword = wifiPassword;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public void setPhoneNumbers(Set<String> phoneNumbers) {
        this.phoneNumbers = phoneNumbers;
    }

    // ==================================================
    // toString
    // ==================================================
    @Override
    public String toString() {
        return "DetectorDTO{" +
                "macAddress='" + macAddress + '\'' +
                ", status=" + status +
                ", temperature=" + temperature +
                ", humidity=" + humidity +
                ", co2=" + co2 +
                ", co2Threshold=" + co2Threshold +
                ", wifiSsid='" + wifiSsid + '\'' +
                ", wifiPassword='" + wifiPassword + '\'' +
                ", location='" + location + '\'' +
                ", phoneNumbers=" + phoneNumbers +
                '}';
    }

    // ==================================================
    // equals and hashCode
    // ==================================================
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DetectorDTO that = (DetectorDTO) o;

        if (macAddress != null ? !macAddress.equals(that.macAddress) : that.macAddress != null)
            return false;
        if (status != null ? !status.equals(that.status) : that.status != null) return false;
        if (temperature != null ? !temperature.equals(that.temperature) : that.temperature != null)
            return false;
        if (humidity != null ? !humidity.equals(that.humidity) : that.humidity != null)
            return false;
        if (co2 != null ? !co2.equals(that.co2) : that.co2 != null) return false;
        if (co2Threshold != null ? !co2Threshold.equals(that.co2Threshold) : that.co2Threshold != null)
            return false;
        if (wifiSsid != null ? !wifiSsid.equals(that.wifiSsid) : that.wifiSsid != null)
            return false;
        if (wifiPassword != null ? !wifiPassword.equals(that.wifiPassword) : that.wifiPassword != null)
            return false;
        if (location != null ? !location.equals(that.location) : that.location != null)
            return false;
        return phoneNumbers != null ? phoneNumbers.equals(that.phoneNumbers) : that.phoneNumbers == null;
    }

    @Override
    public int hashCode() {
        int result = macAddress != null ? macAddress.hashCode() : 0;
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (temperature != null ? temperature.hashCode() : 0);
        result = 31 * result + (humidity != null ? humidity.hashCode() : 0);
        result = 31 * result + (co2 != null ? co2.hashCode() : 0);
        result = 31 * result + (co2Threshold != null ? co2Threshold.hashCode() : 0);
        result = 31 * result + (wifiSsid != null ? wifiSsid.hashCode() : 0);
        result = 31 * result + (wifiPassword != null ? wifiPassword.hashCode() : 0);
        result = 31 * result + (location != null ? location.hashCode() : 0);
        result = 31 * result + (phoneNumbers != null ? phoneNumbers.hashCode() : 0);
        return result;
    }
}