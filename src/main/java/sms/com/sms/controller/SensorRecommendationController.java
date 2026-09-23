package sms.com.sms.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import sms.com.sms.dto.SensorReadingRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api")
@Validated
public class SensorRecommendationController {

    @PostMapping({"/sensor/recommendation", "/sensor/advice", "/environment/recommendation"})
    public ResponseEntity<Map<String, Object>> getRecommendation(@Valid @RequestBody SensorReadingRequest request) {
        String temperatureStatus = statusForRange(request.getTemperature(), 18.0, 30.0);
        String humidityStatus = statusForRange(request.getHumidity(), 45.0, 70.0);
        String soilStatus = statusForRange(request.getSoilMoisture(), 35.0, 60.0);

        boolean isHealthy = "ideal".equals(temperatureStatus)
                && "ideal".equals(humidityStatus)
                && "ideal".equals(soilStatus);

        List<String> actions = new ArrayList<>();
        if ("low".equals(temperatureStatus)) {
            actions.add("Raise the temperature by improving sunlight exposure or using a greenhouse cover.");
        } else if ("high".equals(temperatureStatus)) {
            actions.add("Reduce heat stress by increasing shade, ventilation, or irrigation timing.");
        }

        if ("low".equals(humidityStatus)) {
            actions.add("Increase humidity with misting, humidification, or watering schedules.");
        } else if ("high".equals(humidityStatus)) {
            actions.add("Improve airflow and reduce excess moisture to prevent fungal issues.");
        }

        if ("low".equals(soilStatus)) {
            actions.add("Irrigate the field and check for drainage problems in dry zones.");
        } else if ("high".equals(soilStatus)) {
            actions.add("Reduce watering and improve soil drainage to prevent root stress.");
        }

        if (actions.isEmpty()) {
            actions.add("Keep the current watering and ventilation schedule steady for healthy crop growth.");
        }

        String overallStatus = isHealthy ? "Healthy" : "Attention Needed";
        String message = isHealthy
                ? "The crop environment is in a stable and suitable range for healthy growth."
                : "The current field conditions are outside the ideal range for crop health, so corrective action is recommended.";

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("overallStatus", overallStatus);
        response.put("temperature", request.getTemperature());
        response.put("humidity", request.getHumidity());
        response.put("soilMoisture", request.getSoilMoisture());
        response.put("temperatureStatus", temperatureStatus);
        response.put("humidityStatus", humidityStatus);
        response.put("soilMoistureStatus", soilStatus);
        response.put("healthy", isHealthy);
        response.put("message", message);
        response.put("recommendation", String.join(" ", actions));
        response.put("suggestedActions", actions);
        return ResponseEntity.ok(response);
    }

    private String statusForRange(Double value, Double min, Double max) {
        if (value < min) {
            return "low";
        }
        if (value > max) {
            return "high";
        }
        return "ideal";
    }
}
