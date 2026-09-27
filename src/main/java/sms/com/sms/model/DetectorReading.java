package sms.com.sms.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"detector", "suggestedActions"})
@ToString(exclude = {"detector", "suggestedActions"})
@Entity
@Table(name = "detector_readings")
public class DetectorReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mac_address") // nullable by default so readings can be saved without linking
    private CropDeceaseDetector detector;

    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "humidity")
    private Double humidity;

    @Column(name = "soil")
    private Double soil;

    @Column(name = "soil_moisture")
    private Double soilMoisture;

    @Column(name = "gas")
    private Double gas;

    @Column(name = "co2")
    private Double co2;

    @Column(name = "received_at")
    private Instant receivedAt;

    // === Derived / response fields ===
    @Column(name = "overall_status")
    private String overallStatus;

    @Column(name = "temperature_status")
    private String temperatureStatus;

    @Column(name = "humidity_status")
    private String humidityStatus;

    @Column(name = "soil_moisture_status")
    private String soilMoistureStatus;

    @Column(name = "healthy")
    private Boolean healthy;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "recommendation", columnDefinition = "TEXT")
    private String recommendation;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "detector_reading_suggested_actions", joinColumns = @JoinColumn(name = "reading_id"))
    @Column(name = "action")
    private List<String> suggestedActions = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (this.receivedAt == null) {
            this.receivedAt = Instant.now();
        }
    }
}
