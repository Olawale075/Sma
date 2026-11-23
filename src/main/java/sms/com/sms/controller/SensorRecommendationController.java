package sms.com.sms.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import sms.com.sms.dto.SensorReadingRequest;
import sms.com.sms.enums.Language;
import sms.com.sms.model.CropDeceaseDetector;
import sms.com.sms.model.DetectorReading;
import sms.com.sms.repository.DetectorReadingRepository;
import sms.com.sms.repository.GasDetectorRepository;
import sms.com.sms.repository.UsersRepository;

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
    private final UsersRepository usersRepository;

    public SensorRecommendationController(DetectorReadingRepository readingRepository,
                                          GasDetectorRepository gasDetectorRepository,
                                          UsersRepository usersRepository) {
        this.readingRepository = readingRepository;
        this.gasDetectorRepository = gasDetectorRepository;
        this.usersRepository = usersRepository;
    }

    @PostMapping("/environment/recommendation")
    public ResponseEntity<Map<String, Object>> getRecommendation(@Valid @RequestBody SensorReadingRequest request,
                                                                 @RequestParam(value = "id", required = false) Long id,
                                                                 @RequestParam(value = "mac", required = false) String mac) {

        CropDeceaseDetector detector = mac == null || mac.isBlank()
                ? null
                : gasDetectorRepository.findByMacAddress(mac.trim());
        Language language = getAuthenticatedUserPreferredLanguage();
        if (language == null) {
            language = request.getLanguage();
        }
        if (language == null) {
            language = getDetectorPreferredLanguage(detector);
        }
        if (language == null) {
            language = Language.YO;
        }

        // Build a DetectorReading from incoming DTO
        DetectorReading reading = DetectorReading.builder()
                .temperature(request.getTemperature())
                .humidity(request.getHumidity())
                .soilMoisture(request.getSoilMoisture())
                .aiLanguage(language)
                .receivedAt(Instant.now())
                .build();

        String temperatureStatus = statusForRange(reading.getTemperature(), 18.0, 30.0);
        String humidityStatus = statusForRange(reading.getHumidity(), 40.0, 70.0);
        String soilMoistureStatus = statusForRange(reading.getSoilMoisture(), 30.0, 60.0);

        boolean healthy = "ideal".equals(temperatureStatus) && "ideal".equals(humidityStatus) && "ideal".equals(soilMoistureStatus);
        String overallStatus = healthy ? "healthy" : "unhealthy";

        String message = getMessage(healthy, language);

        // Build recommendations from threshold results, then localize their user-facing text.
        List<String> actionKeys = new ArrayList<>();
        if ("low".equals(temperatureStatus)) actionKeys.add("temperature-low");
        if ("high".equals(temperatureStatus)) actionKeys.add("temperature-high");
        if ("low".equals(humidityStatus)) actionKeys.add("humidity-low");
        if ("high".equals(humidityStatus)) actionKeys.add("humidity-high");
        if ("low".equals(soilMoistureStatus)) actionKeys.add("soil-low");
        if ("high".equals(soilMoistureStatus)) actionKeys.add("soil-high");

        List<String> suggestedActions = new ArrayList<>();
        for (String actionKey : actionKeys) {
            suggestedActions.add(getAction(actionKey, language));
        }

        String recommendation = suggestedActions.isEmpty()
                ? getNoActionMessage(language)
                : String.join("; ", suggestedActions);

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
        if (detector != null) {
            reading.setDetector(detector);
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
        response.put("language", language.getCode());
        response.put("message", message);
        response.put("recommendation", recommendation);
        response.put("suggestedActions", suggestedActions);
        response.put("id", saved.getId());

        return ResponseEntity.ok(response);
    }

    private Language getAuthenticatedUserPreferredLanguage() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName())) {
            return usersRepository.findByPhonenumber(authentication.getName())
                    .map(user -> user.getPreferredLanguage())
                    .orElse(null);
        }

        return null;
    }

    private Language getDetectorPreferredLanguage(CropDeceaseDetector detector) {
        if (detector == null) {
            return null;
        }
        List<Language> linkedUserLanguages = usersRepository
                .findByCropDeceaseDetectors_MacAddress(detector.getMacAddress()).stream()
                .map(user -> user.getPreferredLanguage())
                .filter(language -> language != null)
                .distinct()
                .toList();
        return linkedUserLanguages.size() == 1 ? linkedUserLanguages.get(0) : null;
    }

    private String getMessage(boolean healthy, Language language) {
        return switch (language) {
            case YO -> healthy
                    ? "Àyíká wà láàrín àwọn ìwọ̀n tó yẹ."
                    : "Ọ̀kan tàbí jù bẹ́ẹ̀ lọ nínú àwọn ìwọ̀n kò sí láàrín ààlà tó yẹ.";
            case FR -> healthy
                    ? "L’environnement respecte les plages idéales."
                    : "Un ou plusieurs paramètres sont hors des plages idéales.";
            case EN -> healthy
                    ? "Environment is within ideal ranges."
                    : "One or more parameters are outside ideal ranges.";
        };
    }

    private String getNoActionMessage(Language language) {
        return switch (language) {
            case YO -> "Kò sí ohun tó yẹ kí o ṣe.";
            case FR -> "Aucune action nécessaire.";
            case EN -> "No action needed.";
        };
    }

    private String getAction(String actionKey, Language language) {
        return switch (language) {
            case YO -> switch (actionKey) {
                case "temperature-low" -> "Mu iwọn otutu ayika pọ si";
                case "temperature-high" -> "Dín ooru kù / mú kí atẹ́gùn máa wọlé dáadáa";
                case "humidity-low" -> "Mu ọriniinitutu pọ si (fífọ omi sí ewéko/ìrigásí)";
                case "humidity-high" -> "Dín ọriniinitutu kù / mú kí atẹ́gùn máa wọlé dáadáa";
                case "soil-low" -> "Fi omi bọ àwọn ewéko";
                case "soil-high" -> "Dín fífi omi bọ ewéko kù, kí omi tó pọ̀ lè máa sàn jáde";
                default -> throw new IllegalArgumentException("Unknown recommendation action: " + actionKey);
            };
            case FR -> switch (actionKey) {
                case "temperature-low" -> "Augmenter la température ambiante";
                case "temperature-high" -> "Réduire la chaleur / améliorer la ventilation";
                case "humidity-low" -> "Augmenter l’humidité (brumisation/irrigation)";
                case "humidity-high" -> "Réduire l’humidité / améliorer la ventilation";
                case "soil-low" -> "Arroser les plantes";
                case "soil-high" -> "Réduire l’arrosage et améliorer le drainage";
                default -> throw new IllegalArgumentException("Unknown recommendation action: " + actionKey);
            };
            case EN -> switch (actionKey) {
                case "temperature-low" -> "Increase ambient temperature";
                case "temperature-high" -> "Reduce heat / improve ventilation";
                case "humidity-low" -> "Increase humidity (misting/irrigation)";
                case "humidity-high" -> "Reduce humidity / improve ventilation";
                case "soil-low" -> "Irrigate plants";
                case "soil-high" -> "Reduce watering, improve drainage";
                default -> throw new IllegalArgumentException("Unknown recommendation action: " + actionKey);
            };
        };
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
