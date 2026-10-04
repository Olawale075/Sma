package sms.com.sms.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sms.com.sms.model.CropDeceaseDetector;
import sms.com.sms.model.DetectorReading;
import sms.com.sms.repository.DetectorReadingRepository;
import sms.com.sms.repository.GasDetectorRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api")
public class ReportAnalyticsController {

    private final GasDetectorRepository detectorRepository;
    private final DetectorReadingRepository detectorReadingRepository;

    public ReportAnalyticsController(GasDetectorRepository detectorRepository,
                                   DetectorReadingRepository detectorReadingRepository) {
        this.detectorRepository = detectorRepository;
        this.detectorReadingRepository = detectorReadingRepository;
    }

    @GetMapping({"/detectors/filter", "/reports/filter", "/analytics/filter", "/reports", "/analytics"})
    public ResponseEntity<Map<String, Object>> getDetectorFilterReport(
            @RequestParam(required = false) String macAddress,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) Boolean status,
            @RequestParam(required = false) Boolean healthy,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        List<CropDeceaseDetector> detectors = detectorRepository.findAll();
        List<Map<String, Object>> filtered = detectors.stream()
                .filter(detector -> matchesFilter(detector, macAddress, location, status, healthy, from, to))
                .map(this::buildPayload)
                .skip((long) page * size)
                .limit(size)
                .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("page", page);
        response.put("size", size);
        response.put("count", filtered.size());
        response.put("total", detectors.stream()
                .filter(detector -> matchesFilter(detector, macAddress, location, status, healthy, from, to))
                .count());
        response.put("data", filtered);
        response.put("report", buildReport(filtered));
        response.put("analytics", buildAnalytics(filtered));
        return ResponseEntity.ok(response);
    }

    private boolean matchesFilter(CropDeceaseDetector detector, String macAddress, String location, Boolean status,
                                 Boolean healthy, String from, String to) {
        if (detector == null) {
            return false;
        }
        if (macAddress != null && !macAddress.isBlank() && !macAddress.equalsIgnoreCase(detector.getMacAddress())) {
            return false;
        }
        if (location != null && !location.isBlank() && !location.equalsIgnoreCase(detector.getLocation())) {
            return false;
        }
        if (status != null && !status.equals(detector.getStatus())) {
            return false;
        }

        DetectorReading lastReading = detectorReadingRepository.findTopByDetectorMacAddressOrderByReceivedAtDesc(detector.getMacAddress()).orElse(null);
        if (healthy != null) {
            if (lastReading == null || lastReading.getHealthy() == null) {
                return false;
            }
            if (!healthy.equals(lastReading.getHealthy())) {
                return false;
            }
        }

        if (from != null && !from.isBlank()) {
            try {
                Instant fromInstant = Instant.parse(from);
                if (lastReading == null || lastReading.getReceivedAt() == null || lastReading.getReceivedAt().isBefore(fromInstant)) {
                    return false;
                }
            } catch (Exception ignored) {
                return false;
            }
        }

        if (to != null && !to.isBlank()) {
            try {
                Instant toInstant = Instant.parse(to);
                if (lastReading == null || lastReading.getReceivedAt() == null || lastReading.getReceivedAt().isAfter(toInstant)) {
                    return false;
                }
            } catch (Exception ignored) {
                return false;
            }
        }

        return true;
    }

    private Map<String, Object> buildPayload(CropDeceaseDetector detector) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("macAddress", detector.getMacAddress());
        payload.put("status", detector.getStatus());
        payload.put("location", detector.getLocation());
        payload.put("co2", detector.getCo2());
        payload.put("co2Threshold", detector.getCo2Threshold());
        payload.put("wifiSsid", detector.getWifiSsid());
        payload.put("phoneNumbers", detector.getUsers() == null ? new ArrayList<>() : detector.getUsers().stream()
                .map(user -> user.getPhonenumber())
                .filter(phone -> phone != null && !phone.isBlank())
                .collect(Collectors.toList()));

        DetectorReading lastReading = detectorReadingRepository.findTopByDetectorMacAddressOrderByReceivedAtDesc(detector.getMacAddress()).orElse(null);
        if (lastReading == null) {
            payload.put("lastReading", null);
            payload.put("readingCount", 0);
            payload.put("healthy", null);
            return payload;
        }

        Map<String, Object> reading = new LinkedHashMap<>();
        reading.put("id", lastReading.getId());
        reading.put("temperature", lastReading.getTemperature());
        reading.put("humidity", lastReading.getHumidity());
        reading.put("soil", lastReading.getSoil());
        reading.put("soilMoisture", lastReading.getSoilMoisture());
        reading.put("gas", lastReading.getGas());
        reading.put("co2", lastReading.getCo2());
        reading.put("receivedAt", lastReading.getReceivedAt());
        reading.put("overallStatus", lastReading.getOverallStatus());
        reading.put("temperatureStatus", lastReading.getTemperatureStatus());
        reading.put("humidityStatus", lastReading.getHumidityStatus());
        reading.put("soilMoistureStatus", lastReading.getSoilMoistureStatus());
        reading.put("healthy", lastReading.getHealthy());
        reading.put("message", lastReading.getMessage());
        reading.put("recommendation", lastReading.getRecommendation());
        reading.put("suggestedActions", lastReading.getSuggestedActions());

        payload.put("lastReading", reading);
        payload.put("readingCount", detectorReadingRepository.findByDetectorMacAddressOrderByReceivedAtDesc(detector.getMacAddress()).size());
        payload.put("healthy", lastReading.getHealthy());
        return payload;
    }

    private Map<String, Object> buildReport(List<Map<String, Object>> data) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("totalRecords", data.size());
        report.put("activeDetectors", data.stream().filter(entry -> Boolean.TRUE.equals(entry.get("status"))).count());
        report.put("inactiveDetectors", data.stream().filter(entry -> Boolean.FALSE.equals(entry.get("status"))).count());
        report.put("healthyDetectors", data.stream().filter(entry -> Boolean.TRUE.equals(entry.get("healthy"))).count());
        report.put("unhealthyDetectors", data.stream().filter(entry -> Boolean.FALSE.equals(entry.get("healthy"))).count());
        return report;
    }

    private Map<String, Object> buildAnalytics(List<Map<String, Object>> data) {
        Map<String, Object> analytics = new LinkedHashMap<>();

        List<Double> co2Values = data.stream()
                .map(entry -> (Map<String, Object>) entry.get("lastReading"))
                .filter(reading -> reading != null)
                .map(reading -> reading.get("co2"))
                .filter(value -> value instanceof Number)
                .map(value -> ((Number) value).doubleValue())
                .collect(Collectors.toList());

        List<Double> humidityValues = data.stream()
                .map(entry -> (Map<String, Object>) entry.get("lastReading"))
                .filter(reading -> reading != null)
                .map(reading -> reading.get("humidity"))
                .filter(value -> value instanceof Number)
                .map(value -> ((Number) value).doubleValue())
                .collect(Collectors.toList());

        List<Double> temperatureValues = data.stream()
                .map(entry -> (Map<String, Object>) entry.get("lastReading"))
                .filter(reading -> reading != null)
                .map(reading -> reading.get("temperature"))
                .filter(value -> value instanceof Number)
                .map(value -> ((Number) value).doubleValue())
                .collect(Collectors.toList());

        analytics.put("averageCo2", co2Values.isEmpty() ? 0.0 : co2Values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
        analytics.put("averageHumidity", humidityValues.isEmpty() ? 0.0 : humidityValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
        analytics.put("averageTemperature", temperatureValues.isEmpty() ? 0.0 : temperatureValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
        analytics.put("maxCo2", co2Values.isEmpty() ? 0.0 : co2Values.stream().mapToDouble(Double::doubleValue).max().orElse(0.0));
        analytics.put("locations", data.stream()
                .collect(Collectors.groupingBy(entry -> entry.get("location") == null ? "Unknown" : String.valueOf(entry.get("location")), Collectors.counting())));
        return analytics;
    }
}
