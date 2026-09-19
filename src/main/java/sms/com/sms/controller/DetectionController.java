package sms.com.sms.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import sms.com.sms.ObjectDetectionService;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/detect")
// No @CrossOrigin — handled globally in SecurityConfig
public class DetectionController {

    private static final Logger log = LoggerFactory.getLogger(DetectionController.class);

    private static final int FRONTEND_MAX_GEMINI_ATTEMPTS = 2;
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024; // 10 MB
    private static final String DEFAULT_FOCUS = "General";

    private final ObjectDetectionService detectionService;

    public DetectionController(ObjectDetectionService detectionService) {
        this.detectionService = detectionService;
    }

    @PostMapping({"", "/", "/image"})
    public ResponseEntity<?> detectImage(
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "focus", required = false) String focus,
            @RequestParam(value = "cropType", required = false) String legacyCropType) {

        // ---- validation ----
        if (image == null || image.isEmpty()) {
            return badRequest("image is required");
        }
        if (image.getSize() > MAX_IMAGE_BYTES) {
            return badRequest("image must be <= 10 MB");
        }
        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return badRequest("unsupported content type: " + contentType);
        }

        // ---- resolve focus (new field first, legacy fallback) ----
        String effectiveFocus =
                isNotBlank(focus) ? focus.trim()
                        : isNotBlank(legacyCropType) ? legacyCropType.trim()
                          : DEFAULT_FOCUS;

        try {
            List<ObjectDetectionService.DetectionResult> results =
                    detectionService.detectEverything(
                            image.getBytes(),
                            effectiveFocus,
                            FRONTEND_MAX_GEMINI_ATTEMPTS);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", results.size());
            response.put("focus", effectiveFocus);
            response.put("cropType", effectiveFocus); // legacy alias
            response.put("aiMode", detectionService.getLastAiModeUsed());
            response.put("aiStack", detectionService.getAiStackInfo());
            response.put("availableAiModes", detectionService.getInstalledAiModes());
            response.put("djlEngines", detectionService.getDjlEngines());
            response.put("results", results);

            if (!results.isEmpty()) {
                ObjectDetectionService.DetectionResult top = results.get(0);

                response.put("topResult", top);
                response.put("topClassName", top.className);
                response.put("topDomain", top.domain);
                response.put("confidence", top.probability);
                response.put("confidencePercent", Math.round(top.probability * 100));
                response.put("healthStatus", top.healthStatus);
                response.put("severity", top.severity);
                response.put("requiresAction",
                        top.severity == ObjectDetectionService.Severity.CRITICAL ||
                                top.severity == ObjectDetectionService.Severity.HIGH);
                response.put("conditionName", top.conditionName);
                response.put("treatment", top.treatment != null ? top.treatment : null);
                response.put("whenToAct", top.whenToAct);
            }

            return ResponseEntity.ok(response);

        } catch (ObjectDetectionService.GeminiServiceUnavailableException e) {
            log.warn("AI provider unavailable: {}", e.getMessage());
            return serviceUnavailable(e.getMessage());
        } catch (IllegalStateException e) {
            log.error("AI provider not configured", e);
            return serviceUnavailable(e.getMessage());
        } catch (IOException e) {
            log.error("Detection I/O error", e);
            return serverError(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected detection error", e);
            return serverError("Unexpected error during detection");
        }
    }

    // ---- helpers ----

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static ResponseEntity<Map<String, Object>> badRequest(String msg) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("error", msg);
        return ResponseEntity.badRequest().body(body);
    }

    private static ResponseEntity<Map<String, Object>> serviceUnavailable(String msg) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("error", msg);
        return ResponseEntity.status(503).body(body);
    }

    private static ResponseEntity<Map<String, Object>> serverError(String msg) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("error", msg);
        return ResponseEntity.internalServerError().body(body);
    }
}