package sms.com.sms.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sms.com.sms.model.DetectionHistory;
import sms.com.sms.repository.DetectionHistoryRepository;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/detections")
public class DetectionHistoryController {

    private final DetectionHistoryRepository detectionHistoryRepository;
    private final ObjectMapper objectMapper;

    public DetectionHistoryController(DetectionHistoryRepository detectionHistoryRepository,
                                      ObjectMapper objectMapper) {
        this.detectionHistoryRepository = detectionHistoryRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping({"/report", "/analytics"})
    public ResponseEntity<Map<String, Object>> getDetectionReport(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String focus,
            @RequestParam(required = false) String className,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        if (page < 0 || size < 1 || size > 200 || (from != null && to != null && from.isAfter(to))) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Use page >= 0, size between 1 and 200, and from <= to"));
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Authentication is required to view detection history"));
        }

        String userPhone = authentication.getName();
        String normalizedFocus = normalize(focus);
        String normalizedClassName = normalize(className);
        String normalizedSeverity = normalize(severity);
        Page<DetectionHistory> historyPage = detectionHistoryRepository.findForReport(
                userPhone, from != null, from, to != null, to,
                normalizedFocus, normalizedClassName, normalizedSeverity,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("page", page);
        response.put("size", size);
        response.put("count", historyPage.getNumberOfElements());
        response.put("total", historyPage.getTotalElements());
        response.put("data", historyPage.getContent().stream().map(this::toPayload).toList());
        response.put("report", Map.of(
                "totalAnalyses", historyPage.getTotalElements(),
                "pageCount", historyPage.getNumberOfElements()));
        response.put("analytics", Map.of(
                "byClass", toCounts(detectionHistoryRepository.countByClassForReport(
                        userPhone, from != null, from, to != null, to,
                        normalizedFocus, normalizedClassName, normalizedSeverity)),
                "bySeverity", toCounts(detectionHistoryRepository.countBySeverityForReport(
                        userPhone, from != null, from, to != null, to,
                        normalizedFocus, normalizedClassName, normalizedSeverity))));
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toPayload(DetectionHistory history) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", history.getId());
        payload.put("createdAt", history.getCreatedAt());
        payload.put("deviceId", history.getDeviceId());
        payload.put("location", history.getLocation());
        payload.put("focus", history.getFocus());
        payload.put("language", history.getLanguage());
        payload.put("temperature", history.getTemperature());
        payload.put("humidity", history.getHumidity());
        payload.put("summary", history.getSummary());
        payload.put("className", history.getClassName());
        payload.put("domain", history.getDomain());
        payload.put("confidence", history.getConfidence());
        payload.put("healthStatus", history.getHealthStatus());
        payload.put("severity", history.getSeverity());
        payload.put("conditionName", history.getConditionName());
        payload.put("treatment", history.getTreatment());
        payload.put("whenToAct", history.getWhenToAct());
        try {
            payload.put("results", objectMapper.readValue(
                    history.getResultsJson(), new TypeReference<List<Map<String, Object>>>() {}));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read stored detection results", e);
        }
        return payload;
    }

    private List<Map<String, Object>> toCounts(List<DetectionHistoryRepository.ReportCount> counts) {
        return counts.stream().map(count -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("label", count.getLabel());
            item.put("count", count.getCount());
            return item;
        }).toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
