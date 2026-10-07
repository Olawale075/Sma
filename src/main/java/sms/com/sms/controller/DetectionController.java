package sms.com.sms.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import sms.com.sms.ObjectDetectionService;
import sms.com.sms.model.CropDeceaseDetector;
import sms.com.sms.model.Users;
import sms.com.sms.repository.GasDetectorRepository;
import sms.com.sms.repository.UsersRepository;
import sms.com.sms.service.EmailService;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    private final EmailService emailService;
    private final UsersRepository usersRepository;
    private final GasDetectorRepository gasDetectorRepository;

    public DetectionController(ObjectDetectionService detectionService, EmailService emailService,
                              UsersRepository usersRepository, GasDetectorRepository gasDetectorRepository) {
        this.detectionService = detectionService;
        this.emailService = emailService;
        this.usersRepository = usersRepository;
        this.gasDetectorRepository = gasDetectorRepository;
    }

    @PostMapping("/image")
    public ResponseEntity<?> detectImage(
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "focus", required = false) String focus,
            @RequestParam(value = "cropType", required = false) String legacyCropType,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "deviceId", required = false) String deviceId,
            @RequestParam(value = "location", required = false) String location) {

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
            ResponseEntity<Map<String, Object>> tokenCheck = deductAiTokenForPrompt(email, deviceId);
            if (tokenCheck != null) {
                return tokenCheck;
            }

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

                if (shouldSendCropAlert(results)) {
                    try {
                        sendCropAlertEmail(image, results, email, deviceId, effectiveFocus, location);
                    } catch (Exception ex) {
                        log.warn("Crop alert email could not be sent for image: {}", ex.getMessage());
                    }
                }
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

    private ResponseEntity<Map<String, Object>> deductAiTokenForPrompt(String email, String deviceId) {
        Users user = resolveAuthenticatedUserOrLookup(email, deviceId);
        if (user == null) {
            return null;
        }

        BigDecimal balance = user.getAITokenBalance() == null ? BigDecimal.ZERO : user.getAITokenBalance();
        BigDecimal deduction = BigDecimal.TEN;
        if (balance.compareTo(deduction) < 0) {
            Map<String, Object> body = new HashMap<>();
            body.put("success", false);
            body.put("error", "Insufficient AI token balance. Please top up your AITokenBalance.");
            return ResponseEntity.status(402).body(body);
        }

        user.setAITokenBalance(balance.subtract(deduction));
        usersRepository.save(user);
        return null;
    }

    private Users resolveAuthenticatedUserOrLookup(String email, String deviceId) {
        Object principal = SecurityContextHolder.getContext() != null
                ? SecurityContextHolder.getContext().getAuthentication() != null
                        ? SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                        : null
                : null;

        if (principal instanceof Users) {
            return (Users) principal;
        }

        if (isNotBlank(email)) {
            Users byEmail = usersRepository.findByEmail(email.trim()).orElse(null);
            if (byEmail != null) {
                return byEmail;
            }
        }

        if (isNotBlank(deviceId)) {
            return usersRepository.findByPhonenumber(deviceId.trim()).orElse(null);
        }

        return null;
    }

    private void sendCropAlertEmail(MultipartFile image,
                                   List<ObjectDetectionService.DetectionResult> results,
                                   String email,
                                   String deviceId,
                                   String focus,
                                   String location) throws IOException {
        if (image == null || image.isEmpty()) {
            return;
        }

        String recipient = resolveRecipientEmail(email, deviceId);
        if (recipient == null || recipient.isBlank()) {
            return;
        }

        ObjectDetectionService.DetectionResult alertResult = results.stream()
                .filter(this::isCropAlertResult)
                .findFirst()
                .orElse(null);
        if (alertResult == null) {
            return;
        }

        String subject = "Crop Alert: " + alertResult.className + " detected";
        String imageMimeType = image.getContentType() != null && image.getContentType().startsWith("image/")
                ? image.getContentType() : "image/jpeg";
        byte[] imageBytes = image.getBytes();
        String imageDataUri = "data:" + imageMimeType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);

        String html = "<h2>Crop health alert</h2>"
                + "<p><strong>Detected issue:</strong> " + escapeHtml(alertResult.className) + "</p>"
                + "<p><strong>Severity:</strong> " + alertResult.severity + "</p>"
                + "<p><strong>Health status:</strong> " + (alertResult.healthStatus != null ? alertResult.healthStatus : "unknown") + "</p>"
                + "<p><strong>Focus:</strong> " + escapeHtml(focus) + "</p>"
                + "<p><strong>Location:</strong> " + escapeHtml(location != null ? location : "Not provided") + "</p>"
                + "<p><strong>Confidence:</strong> " + Math.round(alertResult.probability * 100) + "%</p>"
                + "<p><strong>Recommendation:</strong> " + escapeHtml(alertResult.treatment != null ? alertResult.treatment : "Please inspect the crop immediately.") + "</p>"
                + "<p><strong>Action window:</strong> " + escapeHtml(alertResult.whenToAct != null ? alertResult.whenToAct : "Inspect as soon as possible.") + "</p>"
                + "<div style='margin-top:16px;'><img src='" + imageDataUri + "' alt='Crop alert image' style='max-width:100%; border-radius:8px; border:1px solid #ddd;' /></div>";

        emailService.sendEmail(recipient, subject, html);
        log.info("Sent crop alert email to {} for device {} with issue {}", recipient, deviceId, alertResult.className);
    }

    private String resolveRecipientEmail(String email, String deviceId) {
        if (isNotBlank(email)) {
            return email.trim();
        }
        if (isNotBlank(deviceId)) {
            Users user = usersRepository.findByPhonenumber(deviceId).orElse(null);
            if (user != null && isNotBlank(user.getEmail())) {
                return user.getEmail();
            }

            CropDeceaseDetector detector = gasDetectorRepository.findByMacAddress(deviceId);
            if (detector != null && detector.getUsers() != null) {
                return detector.getUsers().stream()
                        .filter(u -> u != null && isNotBlank(u.getEmail()))
                        .map(Users::getEmail)
                        .findFirst()
                        .orElse(null);
            }
        }
        return null;
    }

    private boolean shouldSendCropAlert(List<ObjectDetectionService.DetectionResult> results) {
        return results != null && results.stream().anyMatch(this::isCropAlertResult);
    }

    private boolean isCropAlertResult(ObjectDetectionService.DetectionResult result) {
        if (result == null) {
            return false;
        }

        String className = result.className == null ? "" : result.className.toLowerCase(Locale.ROOT);
        String healthStatus = result.healthStatus == null ? "" : result.healthStatus.toLowerCase(Locale.ROOT);

        if ("diseased".equals(healthStatus) || "injured".equals(healthStatus)) {
            return true;
        }
        if (result.severity == ObjectDetectionService.Severity.HIGH || result.severity == ObjectDetectionService.Severity.CRITICAL) {
            return true;
        }
        if (className.contains("disease") || className.contains("damage") || className.contains("broken")
                || className.contains("leaf") && (className.contains("spot") || className.contains("blight") || className.contains("rot") || className.contains("damage"))
                || className.contains("pest") || className.contains("fungus") || className.contains("rot")) {
            return true;
        }
        if (className.contains("person") || className.contains("human") || className.contains("animal")
                || className.contains("vehicle") || className.contains("intruder") || className.contains("thief")) {
            return true;
        }
        return false;
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
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