package sms.com.sms.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import sms.com.sms.dto.SensorReadingRequest;
import sms.com.sms.model.CropDeceaseDetector;
import sms.com.sms.model.DetectorReading;
import sms.com.sms.repository.DetectorReadingRepository;
import sms.com.sms.repository.GasDetectorRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api")
@Validated
public class SensorRecommendationController {

    private final DetectorReadingRepository readingRepository;
    private final GasDetectorRepository gasDetectorRepository;

    public SensorRecommendationController(DetectorReadingRepository readingRepository, GasDetectorRepository gasDetectorRepository) {
        this.readingRepository = readingRepository;
        this.gasDetectorRepository = gasDetectorRepository;
    }

    @PostMapping("/environment/recommendation")
    public ResponseEntity<Map<String, Object>> getRecommendation(@Valid @RequestBody SensorReadingRequest request,
                                                                 @RequestParam(value = "id", required = false) Long id,
                                                                 @RequestParam(value = "mac", required = false) String mac) {

        // Build a DetectorReading from incoming DTO
        DetectorReading reading = DetectorReading.builder()
                .temperature(request.getTemperature())
                .humidity(request.getHumidity())
                .soilMoisture(request.getSoilMoisture())
                .receivedAt(Instant.now())
                .build();

        String temperatureStatus = statusForRange(reading.getTemperature(), 18.0, 30.0);
        String humidityStatus = statusForRange(reading.getHumidity(), 40.0, 70.0);
        String soilMoistureStatus = statusForRange(reading.getSoilMoisture(), 30.0, 60.0);

        boolean healthy = "ideal".equals(temperatureStatus) && "ideal".equals(humidityStatus) && "ideal".equals(soilMoistureStatus);
        String overallStatus = healthy ? "healthy" : "unhealthy";

        String message = healthy ? "Environment is within ideal ranges." : "One or more parameters are outside ideal ranges.";

        // Build simple recommendations based on which metrics are off
        List<String> suggestedActions = new ArrayList<>();
        if ("low".equals(temperatureStatus)) suggestedActions.add("Increase ambient temperature");
        if ("high".equals(temperatureStatus)) suggestedActions.add("Reduce heat / improve ventilation");
        if ("low".equals(humidityStatus)) suggestedActions.add("Increase humidity (misting/irrigation)");
        if ("high".equals(humidityStatus)) suggestedActions.add("Reduce humidity / improve ventilation");
        if ("low".equals(soilMoistureStatus)) suggestedActions.add("Irrigate plants");
        if ("high".equals(soilMoistureStatus)) suggestedActions.add("Reduce watering, improve drainage");

        String recommendation = suggestedActions.isEmpty() ? "No action needed" : String.join("; ", suggestedActions);

        // Update reading with derived / response fields so it can be persisted
        reading.setOverallStatus(overallStatus);
        reading.setTemperatureStatus(temperatureStatus);
        reading.setHumidityStatus(humidityStatus);
        reading.setSoilMoistureStatus(soilMoistureStatus);
        reading.setHealthy(healthy);
        reading.setMessage(message);
        reading.setRecommendation(recommendation);
        reading.setSuggestedActions(suggestedActions);

        // Link detector when mac provided
        if (mac != null && !mac.trim().isEmpty()) {
            gasDetectorRepository.findById(mac).ifPresent(reading::setDetector);
        }

        // Persist the reading
        DetectorReading saved = readingRepository.save(reading);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("overallStatus", overallStatus);
        response.put("temperature", reading.getTemperature());
        response.put("humidity", reading.getHumidity());
        response.put("soilMoisture", reading.getSoilMoisture());
        response.put("temperatureStatus", temperatureStatus);
        response.put("humidityStatus", humidityStatus);
        response.put("soilMoistureStatus", soilMoistureStatus);
        response.put("healthy", healthy);
        response.put("message", message);
        response.put("recommendation", recommendation);
        response.put("suggestedActions", suggestedActions);
        response.put("id", saved.getId());

        return ResponseEntity.ok(response);
    }

    private String statusForRange(Double value, Double min, Double max) {
        if (value == null) return "unknown";
        if (value < min) {
            return "low";
        }
        if (value > max) {
            return "high";
        }
        return "ideal";
    }
}
