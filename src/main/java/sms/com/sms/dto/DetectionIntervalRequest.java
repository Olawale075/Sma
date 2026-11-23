package sms.com.sms.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Min;
import sms.com.sms.enums.Language;

public class DetectionIntervalRequest {

    @Min(value = 1, message = "Detection interval must be at least 1")
    private Integer detectionInterval;

    @JsonAlias("language")
    private Language preferredLanguage;

    public Integer getDetectionInterval() {
        return detectionInterval;
    }

    public void setDetectionInterval(Integer detectionInterval) {
        this.detectionInterval = detectionInterval;
    }

    public Language getPreferredLanguage() {
        return preferredLanguage;
    }

    public void setPreferredLanguage(Language preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
    }
}
