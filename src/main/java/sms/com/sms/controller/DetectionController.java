package sms.com.sms.controller;



import org.springframework.beans.factory.annotation.Autowired;
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
@CrossOrigin("*")
public class DetectionController {

    private static final int FRONTEND_MAX_GEMINI_ATTEMPTS = 2;

    @Autowired
    private ObjectDetectionService detectionService;

    @PostMapping({"", "/", "/image"})
    @CrossOrigin("*")
    public ResponseEntity<?> detectImage(
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "cropType", required = false, defaultValue = "Cassava Leaf") String cropType) {
        try {
            List<ObjectDetectionService.DetectionResult> results =
                    detectionService.detectEverything(image.getBytes(), cropType, FRONTEND_MAX_GEMINI_ATTEMPTS);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("results", results);
            response.put("count", results.size());
            response.put("cropType", cropType);
            response.put("aiMode", detectionService.getLastAiModeUsed());
            response.put("aiStack", detectionService.getAiStackInfo());
            response.put("availableAiModes", detectionService.getInstalledAiModes());
            response.put("djlEngines", detectionService.getDjlEngines());

            if (!results.isEmpty()) {
                ObjectDetectionService.DetectionResult topResult = results.get(0);
                response.put("topResult", topResult);
                response.put("disease", topResult.className);
                response.put("confidence", topResult.probability);
                response.put("treatment", topResult.treatment != null ? topResult.treatment : "--");
                response.put(
                        "status",
                        topResult.className != null && topResult.className.toLowerCase().contains("healthy")
                                ? "HEALTHY"
                                : "DETECTED"
                );
            }

            return ResponseEntity.ok(response);
        } catch (ObjectDetectionService.GeminiServiceUnavailableException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.status(503).body(error);
        } catch (IllegalStateException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.status(503).body(error);
        } catch (IOException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
}
