package sms.com.sms.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import sms.com.sms.dto.SensorReadingRequest;
import sms.com.sms.model.SensorReading;
import sms.com.sms.service.SensorReadingService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api")
@Validated
public class SensorRecommendationController {

    private final SensorReadingService sensorReadingService;

    public SensorRecommendationController(SensorReadingService sensorReadingService) {
        this.sensorReadingService = sensorReadingService;
    }

    @PostMapping("/environment/recommendation")
    public ResponseEntity<Map<String, Object>> getRecommendation(@Valid @RequestBody SensorReadingRequest request,
                                                                 @RequestParam(value = "id", required = false) String id) {

        // Create or update the reading via service
        SensorReading saved;
        if (id != null) {
            Optional<SensorReading> updated = sensorReadingService.update(id, request);
            saved = updated.orElseGet(() -> sensorReadingService.create(request));
        } else {
            saved = sensorReadingService.create(request);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("overallStatus", saved.getOverallStatus());
        response.put("temperature", saved.getTemperature());
        response.put("humidity", saved.getHumidity());
        response.put("soilMoisture", saved.getSoilMoisture());
        response.put("temperatureStatus", saved.getTemperatureStatus());
        response.put("humidityStatus", saved.getHumidityStatus());
        response.put("soilMoistureStatus", saved.getSoilMoistureStatus());
        response.put("healthy", saved.getHealthy());
        response.put("message", saved.getMessage());
        response.put("recommendation", saved.getRecommendation());
        response.put("suggestedActions", saved.getSuggestedActions());
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
