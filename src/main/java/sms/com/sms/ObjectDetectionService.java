package sms.com.sms;

import ai.djl.engine.Engine;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sms.com.sms.config.CameraWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ObjectDetectionService {

    private static final Logger logger = LoggerFactory.getLogger(ObjectDetectionService.class);
    private static final DecimalFormat df = new DecimalFormat("#.#");

    // ================================================================
    // UNIVERSAL DETECTION CONSTANTS
    // ================================================================

    private static final String DEFAULT_FOCUS = "General";
    private static final String AI_MODE_GEMINI = "Gemini";
    private static final String AI_MODE_OPENAI = "OpenAI";
    private static final String AI_MODE_UNAVAILABLE = "Unavailable";

    /** Minimum confidence for any detection to be kept. */
    private static final double MIN_CONFIDENCE = 0.20;

    /** Maximum number of detections returned per image. */
    private static final int MAX_DETECTIONS = 10;

    /** Max output tokens for the AI response. */
    private static final int MAX_OUTPUT_TOKENS = 2048;

    public List<Boolean> getDjlEngines() {
        // Make sure Engine.getAllEngines() really returns List<Engine>
        return Engine.getAllEngines()
                .stream()
                .map(engine -> engine.regionMatches(0, "DLJ", 0, 3)) // explicit lambda avoids confusion
                .collect(Collectors.toList());
    }

    /**
     * Domain categories the AI can classify into.
     */
    public enum Domain {
        LIVING_THING,   // people, animals, insects, birds, fish
        PLANT,          // crops, trees, flowers, leaves (incl. diseases)
        FOOD,           // fruits, vegetables, cooked dishes, drinks
        VEHICLE,        // cars, bikes, trucks, boats, planes
        BUILDING,       // houses, bridges, structures
        ELECTRONICS,    // phones, laptops, TVs, appliances
        FURNITURE,      // chairs, tables, beds
        CLOTHING,       // shirts, shoes, hats
        TOOL,           // hammers, hoes, machines
        NATURE,         // sky, water, mountains, soil, clouds
        SCENE,          // indoor, outdoor, street, market, farm
        DOCUMENT,       // text, signs, labels, QR codes
        MEDICAL,        // wounds, skin conditions, x-rays (non-diagnostic)
        UNKNOWN
    }

    /**
     * Severity is only meaningful for health/disease/injury detections.
     */
    public enum Severity {
        CRITICAL("Immediate action required"),
        HIGH("Severe - act soon"),
        MODERATE("Monitor and act"),
        LOW("Early signs - monitor"),
        NONE("No issue");

        public final String description;
        Severity(String description) { this.description = description; }
    }

    // Thread pool for async detection
    private final ExecutorService detectionExecutor = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() - 1)
    );

    private final Map<String, List<DetectionResult>> detectionCache = new ConcurrentHashMap<>();
    private final Map<String, DetectionResult> lastDetection = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CameraWebSocketHandler webSocketHandler;

    @Value("${gemini.api-key:${GEMINI_API_KEY:}}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-1.5-flash}")
    private String geminiModel;

    @Value("${gemini.endpoint:https://generativelanguage.googleapis.com/v1beta/models}")
    private String geminiEndpoint;

    @Value("${gemini.max-retries:3}")
    private int geminiMaxRetries;

    @Value("${gemini.retry-initial-delay-ms:1000}")
    private long geminiRetryInitialDelayMs;

    @Value("${gemini.retry-max-delay-ms:30000}")
    private long geminiRetryMaxDelayMs;

    @Value("${openai.api-key:${OPENAI_API_KEY:}}")
    private String openAiApiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String openAiModel;

    @Value("${openai.endpoint:https://api.openai.com/v1}")
    private String openAiEndpoint;

    @Value("${openai.max-retries:3}")
    private int openAiMaxRetries;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    // State
    private volatile boolean modelLoaded = false;
    private volatile String lastAiModeUsed = AI_MODE_UNAVAILABLE;

    private final java.util.concurrent.atomic.AtomicLong totalDetections = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong successfulDetections = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong failedDetections = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong detectionTimeSum = new java.util.concurrent.atomic.AtomicLong();
    private volatile long lastDetectionTime = 0;

    public ObjectDetectionService(CameraWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    // ================================================================
    // INIT
    // ================================================================

    @PostConstruct
    public void init() {
        logger.info("🌍 Initializing UNIVERSAL AI detection service...");
        logger.info("   Gemini model: {}", geminiModel);
        logger.info("   Gemini endpoint: {}", geminiEndpoint);
        logger.info("   Gemini retries: {}", geminiMaxRetries);
        logger.info("   OpenAI model: {}", openAiModel);
        logger.info("   OpenAI endpoint: {}", openAiEndpoint);
        logger.info("   Default focus: {}", DEFAULT_FOCUS);
        logger.info("   Max detections per image: {}", MAX_DETECTIONS);
        logger.info("   Min confidence: {}", MIN_CONFIDENCE);
        logger.info("   Capabilities: people, animals, plants, food, vehicles, buildings,");
        logger.info("                 electronics, furniture, clothing, tools, nature, scenes,");
        logger.info("                 text/documents, medical signs, crop diseases, pests.");

        if (geminiApiKey != null) {
            geminiApiKey = geminiApiKey.trim();
            if (geminiApiKey.startsWith("Bearer ")) {
                geminiApiKey = geminiApiKey.substring("Bearer ".length()).trim();
            }
        }
        if (openAiApiKey != null) {
            openAiApiKey = openAiApiKey.trim();
            if (openAiApiKey.startsWith("Bearer ")) {
                openAiApiKey = openAiApiKey.substring("Bearer ".length()).trim();
            }
        }

        boolean geminiReady = isGeminiConfigured();
        boolean openAiReady = isOpenAiConfigured();
        lastAiModeUsed = geminiReady ? AI_MODE_GEMINI : openAiReady ? AI_MODE_OPENAI : AI_MODE_UNAVAILABLE;

        logAiModeInventory(geminiReady, openAiReady);

        if (geminiReady) {
            modelLoaded = true;
        } else if (openAiReady) {
            modelLoaded = true;
            logger.info("Gemini API key is not configured. Falling back to OpenAI provider.");
        } else {
            logger.warn("No AI provider is configured. Set gemini.api-key/GEMINI_API_KEY or openai.api-key/OPENAI_API_KEY.");
            modelLoaded = false;
        }

        startCacheCleanup();

        logger.info("✅ ObjectDetectionService initialized.");
        logger.info("   Model loaded: {}", modelLoaded);
        logger.info("   AI providers enabled: Gemini={}, OpenAI={}", geminiReady, openAiReady);
        logger.info("   Installed AI modes: {}", String.join(", ", getInstalledAiModes()));
    }

    // ================================================================
    // MAIN DETECTION METHODS
    // ================================================================

    public void processAndSendDetection(byte[] imageData) {
        processAndSendDetection(imageData, null, null, DEFAULT_FOCUS);
    }

    /** Legacy alias. focus may be any hint like "General", "Dog", "Plant", "Food", "Cassava Leaf". */
    public void processAndSendDetection(byte[] imageData, String deviceId, String location, String focus) {
        if (imageData == null || imageData.length == 0) {
            logger.warn("Empty image data received from device: {}", deviceId);
            return;
        }

        detectionExecutor.submit(() -> {
            try {
                long startTime = System.nanoTime();
                String normalizedFocus = normalizeFocus(focus);

                List<DetectionResult> results = detectEverything(imageData, normalizedFocus);

                long detectionTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

                totalDetections.incrementAndGet();
                detectionTimeSum.addAndGet(detectionTime);
                lastDetectionTime = detectionTime;

                List<DetectionResult> filtered = results.stream()
                        .filter(r -> r.probability >= MIN_CONFIDENCE)
                        .sorted((a, b) -> Double.compare(
                                getFocusScore(b, normalizedFocus),
                                getFocusScore(a, normalizedFocus)))
                        .collect(Collectors.toList());

                if (!filtered.isEmpty()) {
                    successfulDetections.incrementAndGet();
                    DetectionResult top = filtered.get(0);

                    logger.info("🔍 Device {}: {} items in {}ms [Top: {} ({}%) | Domain: {}]",
                            deviceId != null ? deviceId : "unknown",
                            filtered.size(),
                            detectionTime,
                            top.className,
                            df.format(top.probability * 100),
                            top.domain);

                    if (isEarlyLifeStage(top.className)) {
                        logger.warn("   🌱 Early life stage detected!");
                    }

                    List<DetectionResult> healthResults = filtered.stream()
                            .filter(r -> isHealthIssue(r.className)
                                    || "diseased".equalsIgnoreCase(r.healthStatus)
                                    || "injured".equalsIgnoreCase(r.healthStatus))
                            .collect(Collectors.toList());

                    if (!healthResults.isEmpty()) {
                        logger.info("   ⚕️ Health issues: {}",
                                healthResults.stream()
                                        .map(r -> r.className)
                                        .collect(Collectors.joining(", ")));
                    }

                    if (deviceId != null) {
                        lastDetection.put(deviceId, top);
                    }
                } else {
                    logger.debug("No significant detections from device: {}", deviceId);
                    if (deviceId != null) lastDetection.remove(deviceId);
                }

                String detectionJson = buildComprehensiveDetectionJson(
                        filtered, deviceId, location, normalizedFocus);
                webSocketHandler.sendDetectionToClients(detectionJson);

                if (deviceId != null) {
                    detectionCache.put(deviceId, filtered);
                }

            } catch (Exception e) {
                failedDetections.incrementAndGet();
                logger.error("❌ Error processing detection: {}", e.getMessage(), e);
                String emptyJson = String.format(
                        "{\"type\":\"detection\",\"deviceId\":\"%s\",\"count\":0,\"results\":[],\"error\":\"%s\",\"timestamp\":%d}",
                        deviceId != null ? deviceId : "unknown",
                        escapeJson(e.getMessage()),
                        System.currentTimeMillis());
                webSocketHandler.sendDetectionToClients(emptyJson);
            }
        });
    }

    public List<DetectionResult> detectEverything(byte[] imageData) throws IOException {
        return detectEverything(imageData, DEFAULT_FOCUS);
    }

    public List<DetectionResult> detectEverything(byte[] imageData, String focus) throws IOException {
        return detectEverything(imageData, focus, null);
    }

    public List<DetectionResult> detectEverything(byte[] imageData, String focus, Integer maxAttemptsOverride) throws IOException {
        String normalizedFocus = normalizeFocus(focus);

        if (!modelLoaded) {
            throw new IllegalStateException(
                    "No AI provider is configured. Set gemini.api-key/GEMINI_API_KEY or openai.api-key/OPENAI_API_KEY.");
        }

        logger.info(
                "Detection request received. focus={}, imageBytes={}, installedAiModes={}, configuredProviders={}",
                normalizedFocus,
                imageData != null ? imageData.length : 0,
                String.join(", ", getInstalledAiModes()),
                String.join(", ", getConfiguredAiProviders()));

        if (isGeminiConfigured()) {
            try {
                lastAiModeUsed = AI_MODE_GEMINI;
                logger.info("Running detection with AI mode: {}", lastAiModeUsed);
                String responseText = callGeminiVisionApi(imageData, normalizedFocus, maxAttemptsOverride);
                return parseDetections(responseText, normalizedFocus);
            } catch (IOException geminiException) {
                if (!isOpenAiConfigured()) throw geminiException;
                logger.warn("Gemini provider failed. Falling back to OpenAI: {}", geminiException.getMessage());
            }
        }

        lastAiModeUsed = AI_MODE_OPENAI;
        logger.info("Running detection with AI mode: {}", lastAiModeUsed);
        String responseText = callOpenAIVisionApi(imageData, normalizedFocus, maxAttemptsOverride);
        return parseDetections(responseText, normalizedFocus);
    }

    public static class GeminiServiceUnavailableException extends IOException {
        public GeminiServiceUnavailableException(String message) {
            super(message);
        }
    }

    // ================================================================
    // PROVIDER CHECKS / INFO
    // ================================================================

    private boolean isGeminiConfigured() {
        return geminiApiKey != null && !geminiApiKey.isBlank();
    }

    private boolean isOpenAiConfigured() {
        return openAiApiKey != null && !openAiApiKey.isBlank();
    }

    private void logAiModeInventory(boolean geminiReady, boolean openAiReady) {
        logger.info(
                "AI stack inventory: configuredProviders={}, installedModes={}",
                String.join(", ", getConfiguredAiProviders(geminiReady, openAiReady)),
                String.join(", ", getInstalledAiModes()));
    }

    private List<String> getConfiguredAiProviders(boolean geminiReady, boolean openAiReady) {
        List<String> configuredProviders = new ArrayList<>();
        if (geminiReady) configuredProviders.add(AI_MODE_GEMINI);
        if (openAiReady) configuredProviders.add(AI_MODE_OPENAI);
        if (configuredProviders.isEmpty()) configuredProviders.add("none");
        return configuredProviders;
    }

    public List<String> getConfiguredAiProviders() {
        return getConfiguredAiProviders(isGeminiConfigured(), isOpenAiConfigured());
    }

    public List<String> getInstalledAiModes() {
        List<String> installedModes = new ArrayList<>();
        if (isGeminiConfigured()) installedModes.add(AI_MODE_GEMINI);
        if (isOpenAiConfigured()) installedModes.add(AI_MODE_OPENAI);
        if (installedModes.isEmpty()) installedModes.add("none");
        return installedModes;
    }

    public String getLastAiModeUsed() {
        return lastAiModeUsed;
    }

    public Map<String, Object> getAiStackInfo() {
        Map<String, Object> aiStack = new LinkedHashMap<>();
        aiStack.put("activeMode", lastAiModeUsed);
        aiStack.put("configuredProviders", getConfiguredAiProviders());
        aiStack.put("installedModes", getInstalledAiModes());
        return aiStack;
    }

    // ================================================================
    // GEMINI API CALL
    // ================================================================

    private String callGeminiVisionApi(byte[] imageData, String focus, Integer maxAttemptsOverride) throws IOException {
        try {
            String mimeType = detectImageMimeType(imageData);
            String base64Image = Base64.getEncoder().encodeToString(imageData);

            Map<String, Object> inlineData = new LinkedHashMap<>();
            inlineData.put("mime_type", mimeType);
            inlineData.put("data", base64Image);

            Map<String, Object> textPart = new LinkedHashMap<>();
            textPart.put("text", buildPrompt(focus));

            Map<String, Object> imagePart = new LinkedHashMap<>();
            imagePart.put("inline_data", inlineData);

            Map<String, Object> content = new LinkedHashMap<>();
            content.put("role", "user");
            content.put("parts", Arrays.asList(textPart, imagePart));

            Map<String, Object> generationConfig = new LinkedHashMap<>();
            generationConfig.put("temperature", 0.2);
            generationConfig.put("topP", 0.8);
            generationConfig.put("maxOutputTokens", MAX_OUTPUT_TOKENS);
            generationConfig.put("responseMimeType", "application/json");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("contents", Collections.singletonList(content));
            body.put("generationConfig", generationConfig);

            String requestBody = objectMapper.writeValueAsString(body);
            String url = buildGeminiGenerateContentUrl();
            int maxAttempts = Math.max(1, maxAttemptsOverride != null ? maxAttemptsOverride : geminiMaxRetries);

            Integer lastRetriableStatus = null;
            String lastRetriableBody = null;

            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(60))
                        .header("Content-Type", "application/json")
                        .header("x-goog-api-key", geminiApiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                String rawResponseBody = response.body();
                int statusCode = response.statusCode();
                logger.debug("Raw Gemini response: {}", rawResponseBody);

                if (statusCode >= 200 && statusCode < 300) {
                    return extractGeminiText(rawResponseBody);
                }

                if (rawResponseBody != null && rawResponseBody.contains("ACCESS_TOKEN_TYPE_UNSUPPORTED")) {
                    throw new IOException("Gemini authentication failed: credential type is unsupported. "
                            + "Use a Gemini API key from Google AI Studio in gemini.api-key or GEMINI_API_KEY. "
                            + "Raw response: " + rawResponseBody);
                }

                if (statusCode == 429 || statusCode == 503) {
                    lastRetriableStatus = statusCode;
                    lastRetriableBody = rawResponseBody;
                }

                if ((statusCode == 429 || statusCode == 503) && attempt < maxAttempts) {
                    long delayMs = getRetryDelayMs(attempt, response.headers().firstValue("Retry-After"));
                    logger.warn("Gemini API HTTP {} on attempt {}/{}. Retrying in {} ms.",
                            statusCode, attempt, maxAttempts, delayMs);
                    Thread.sleep(delayMs);
                    continue;
                }

                throw new IOException("Gemini API returned HTTP " + statusCode + ": " + rawResponseBody);
            }

            if (lastRetriableStatus != null) {
                throw new GeminiServiceUnavailableException(
                        "Gemini service is temporarily unavailable (HTTP " + lastRetriableStatus
                                + ") after " + maxAttempts + " attempts. Please try again in a moment. "
                                + "Last response: " + lastRetriableBody);
            }

            throw new IOException("Gemini API retries exhausted without a successful response.");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Gemini API request was interrupted", e);
        }
    }

    // ================================================================
    // OPENAI API CALL
    // ================================================================

    private String callOpenAIVisionApi(byte[] imageData, String focus, Integer maxAttemptsOverride) throws IOException {
        try {
            String mimeType = detectImageMimeType(imageData);
            String base64Image = Base64.getEncoder().encodeToString(imageData);

            Map<String, Object> requestBodyMap = Map.of(
                    "model", normalizeOpenAiModel(),
                    "temperature", 0.2,
                    "max_tokens", MAX_OUTPUT_TOKENS,
                    "messages", List.of(Map.of(
                            "role", "user",
                            "content", List.of(
                                    Map.of("type", "text", "text", buildPrompt(focus)),
                                    Map.of("type", "image_url",
                                            "image_url", Map.of("url",
                                                    "data:" + mimeType + ";base64," + base64Image))
                            )
                    ))
            );
            String requestBody = objectMapper.writeValueAsString(requestBodyMap);

            String url = buildOpenAiChatCompletionsUrl();
            int maxAttempts = Math.max(1, maxAttemptsOverride != null ? maxAttemptsOverride : openAiMaxRetries);
            Integer lastRetriableStatus = null;
            String lastRetriableBody = null;

            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(60))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + openAiApiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                String rawResponseBody = response.body();
                int statusCode = response.statusCode();
                logger.debug("Raw OpenAI response: {}", rawResponseBody);

                if (statusCode >= 200 && statusCode < 300) {
                    return extractOpenAiText(rawResponseBody);
                }

                if (statusCode == 429 || statusCode == 503) {
                    lastRetriableStatus = statusCode;
                    lastRetriableBody = rawResponseBody;
                }

                if ((statusCode == 429 || statusCode == 503) && attempt < maxAttempts) {
                    long delayMs = getRetryDelayMs(attempt, response.headers().firstValue("Retry-After"));
                    logger.warn("OpenAI API HTTP {} on attempt {}/{}. Retrying in {} ms.",
                            statusCode, attempt, maxAttempts, delayMs);
                    Thread.sleep(delayMs);
                    continue;
                }

                throw new IOException("OpenAI API returned HTTP " + statusCode + ": " + rawResponseBody);
            }

            if (lastRetriableStatus != null) {
                throw new GeminiServiceUnavailableException(
                        "OpenAI service is temporarily unavailable (HTTP " + lastRetriableStatus
                                + ") after " + maxAttempts + " attempts. Please try again in a moment. "
                                + "Last response: " + lastRetriableBody);
            }

            throw new IOException("OpenAI API retries exhausted without a successful response.");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("OpenAI API request was interrupted", e);
        }
    }

    private String normalizeOpenAiModel() {
        String model = openAiModel == null ? "" : openAiModel.trim();
        return model.isBlank() ? "gpt-4o-mini" : model;
    }

    private String buildOpenAiChatCompletionsUrl() {
        String endpoint = openAiEndpoint == null ? "https://api.openai.com/v1" : openAiEndpoint.trim();
        if (endpoint.isBlank()) endpoint = "https://api.openai.com/v1";
        endpoint = endpoint.replaceAll("/+$", "");
        return endpoint.endsWith("/chat/completions") ? endpoint : endpoint + "/chat/completions";
    }

    private String extractOpenAiText(String rawResponseBody) throws IOException {
        Map<?, ?> responseMap = objectMapper.readValue(rawResponseBody, Map.class);
        List<?> choices = (List<?>) responseMap.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IOException("OpenAI API returned no choices. Raw: " + rawResponseBody);
        }
        Object firstChoice = choices.get(0);
        if (!(firstChoice instanceof Map)) {
            throw new IOException("OpenAI API response did not include a valid choice. Raw: " + rawResponseBody);
        }
        Map<?, ?> choiceMap = (Map<?, ?>) firstChoice;
        Map<?, ?> messageMap = (Map<?, ?>) choiceMap.get("message");
        if (messageMap == null) {
            throw new IOException("OpenAI API response did not include a message. Raw: " + rawResponseBody);
        }
        Object content = messageMap.get("content");
        if (content instanceof String) return (String) content;
        if (content instanceof List<?>) {
            StringBuilder combined = new StringBuilder();
            for (Object item : (List<?>) content) {
                if (item instanceof Map) {
                    Object text = ((Map<?, ?>) item).get("text");
                    if (text != null) combined.append(text);
                }
            }
            if (combined.length() > 0) return combined.toString();
        }
        throw new IOException("OpenAI API response did not include generated text. Raw: " + rawResponseBody);
    }

    private String extractGeminiText(String rawResponseBody) throws IOException {
        Map<?, ?> responseMap = objectMapper.readValue(rawResponseBody, Map.class);
        List<?> candidates = (List<?>) responseMap.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new IOException("Gemini API returned no candidates. Raw: " + rawResponseBody);
        }
        for (Object candidateObj : candidates) {
            if (!(candidateObj instanceof Map)) continue;
            Map<?, ?> contentMap = (Map<?, ?>) ((Map<?, ?>) candidateObj).get("content");
            if (contentMap == null) continue;
            List<?> parts = (List<?>) contentMap.get("parts");
            if (parts == null || parts.isEmpty()) continue;
            StringBuilder combinedText = new StringBuilder();
            for (Object partObj : parts) {
                if (partObj instanceof Map) {
                    Object text = ((Map<?, ?>) partObj).get("text");
                    if (text != null) combinedText.append(text);
                }
            }
            if (combinedText.length() > 0) return combinedText.toString();
        }
        throw new IOException("Gemini API response did not include generated text. Raw: " + rawResponseBody);
    }

    private long getRetryDelayMs(int attempt, Optional<String> retryAfterHeader) {
        long baseDelayMs = Math.max(250L, geminiRetryInitialDelayMs);
        long multiplier = 1L << Math.min(10, Math.max(0, attempt - 1));
        long exponentialDelay = baseDelayMs * multiplier;
        long capped = Math.min(exponentialDelay, Math.max(1000L, geminiRetryMaxDelayMs));
        long jitter = ThreadLocalRandom.current().nextLong(200L, 800L);
        long computedDelay = capped + jitter;

        if (retryAfterHeader.isPresent()) {
            try {
                long retryAfterSeconds = Long.parseLong(retryAfterHeader.get().trim());
                if (retryAfterSeconds > 0) {
                    long retryAfterMs = retryAfterSeconds * 1000L;
                    return Math.min(Math.max(computedDelay, retryAfterMs), Math.max(1000L, geminiRetryMaxDelayMs));
                }
            } catch (NumberFormatException ignored) { /* use computed */ }
        }
        return Math.min(computedDelay, Math.max(1000L, geminiRetryMaxDelayMs));
    }

    private String buildGeminiGenerateContentUrl() throws IOException {
        String endpoint = geminiEndpoint == null ? "" : geminiEndpoint.trim();
        String model = geminiModel == null ? "" : geminiModel.trim();

        if (endpoint.isEmpty()) throw new IOException("Gemini endpoint is not configured.");
        if (model.isEmpty()) throw new IOException("Gemini model is not configured.");

        endpoint = endpoint.replaceAll("/+$", "");
        model = model.replaceAll(":generateContent$", "");

        if (model.contains("/models/")) {
            model = model.substring(model.indexOf("/models/") + "/models/".length());
        } else if (model.startsWith("models/")) {
            model = model.substring("models/".length());
        }
        if (model.contains("/")) {
            model = model.substring(model.lastIndexOf('/') + 1);
        }
        if (model.isBlank()) throw new IOException("Gemini model name is invalid after normalization.");

        String normalizedEndpoint = endpoint.endsWith("/models") ? endpoint : endpoint + "/models";
        return normalizedEndpoint + "/" + model + ":generateContent";
    }

    // ================================================================
    // UNIVERSAL PROMPT
    // ================================================================

    private String buildPrompt(String focus) {
        return String.format(
                "You are a universal visual recognition assistant. " +
                        "Identify EVERYTHING visible in the image: people, animals, plants, food, vehicles, " +
                        "buildings, electronics, furniture, clothing, tools, natural scenes, text, medical signs, and more. " +
                        "If a plant or crop is present, also detect any diseases, pests, or nutrient deficiencies. " +
                        "If a human or animal is present, describe what they are doing and any visible health signs. " +
                        "User focus hint: '%s' (may be 'General').\n\n" +

                        "Respond with ONLY valid JSON, no markdown, no code fences, no explanation. " +
                        "Use this exact schema:\n" +
                        "{\n" +
                        "  \"scene\": \"short description of the overall scene\",\n" +
                        "  \"items\": [\n" +
                        "    {\n" +
                        "      \"className\": \"short specific label, e.g. 'Domestic Dog', 'Tomato Early Blight', 'Toyota Corolla', 'Wooden Chair'\",\n" +
                        "      \"domain\": \"one of: LIVING_THING, PLANT, FOOD, VEHICLE, BUILDING, ELECTRONICS, FURNITURE, CLOTHING, TOOL, NATURE, SCENE, DOCUMENT, MEDICAL, UNKNOWN\",\n" +
                        "      \"probability\": 0.0,\n" +
                        "      \"x\": 0.0, \"y\": 0.0, \"width\": 0.0, \"height\": 0.0,\n" +
                        "      \"attributes\": { \"key\": \"value\" },\n" +
                        "      \"health\": { \"status\": \"healthy|diseased|injured|unknown\", \"severity\": \"CRITICAL|HIGH|MODERATE|LOW|NONE\", \"name\": \"optional disease/condition name\" },\n" +
                        "      \"treatment\": \"optional treatment/action, or null\",\n" +
                        "      \"prevention\": [\"optional tips\"],\n" +
                        "      \"whenToAct\": \"optional timing advice, or null\"\n" +
                        "    }\n" +
                        "  ],\n" +
                        "  \"safety\": { \"containsPeople\": true, \"containsChildren\": false, \"containsWeapons\": false, \"containsMedical\": false },\n" +
                        "  \"summary\": \"one-sentence plain-language summary\"\n" +
                        "}\n\n" +

                        "Rules:\n" +
                        "- Probability must be a number between 0 and 1.\n" +
                        "- Bounding box values (x, y, width, height) must be normalized 0..1 relative to the image.\n" +
                        "- Return at most %d items, sorted from highest to lowest probability.\n" +
                        "- Use specific labels, not just 'object' or 'thing'.\n" +
                        "- If nothing is recognizable, return an empty items array.\n",
                focus == null || focus.isBlank() ? DEFAULT_FOCUS : focus,
                MAX_DETECTIONS);
    }

    // ================================================================
    // PARSING
    // ================================================================

    private List<DetectionResult> parseDetections(String responseText, String focus) throws IOException {
        try {
            String jsonText = stripToJson(responseText);
            Object parsed = objectMapper.readValue(jsonText, Object.class);

            List<DetectionResult> detections = new ArrayList<>();

            if (parsed instanceof Map<?, ?> responseMap) {
                Object items = responseMap.get("items");
                if (items instanceof List<?> list) {
                    for (Object item : list) {
                        DetectionResult d = parseDetectionItem(item, focus);
                        if (d != null && d.probability >= MIN_CONFIDENCE) detections.add(d);
                    }
                } else {
                    DetectionResult single = parseDetectionItem(responseMap, focus);
                    if (single != null && single.probability >= MIN_CONFIDENCE) detections.add(single);
                }
            } else if (parsed instanceof List<?> list) {
                for (Object item : list) {
                    DetectionResult d = parseDetectionItem(item, focus);
                    if (d != null && d.probability >= MIN_CONFIDENCE) detections.add(d);
                }
            }

            if (detections.isEmpty()) {
                throw new IOException("AI returned no usable detections. Raw: " + responseText);
            }

            detections.sort((a, b) -> Double.compare(getFocusScore(b, focus), getFocusScore(a, focus)));
            return detections.stream().limit(MAX_DETECTIONS).collect(Collectors.toList());

        } catch (IOException e) {
            logger.error("AI response parsing failed. Raw: {}", responseText, e);
            throw new IOException("AI response parsing failed. Raw: " + responseText, e);
        }
    }

    private DetectionResult parseDetectionItem(Object item, String focus) {
        if (!(item instanceof Map<?, ?> map)) return null;

        Object classNameValue = map.get("className");
        if (classNameValue == null) classNameValue = map.get("label");
        if (classNameValue == null) return null;

        String rawClass = classNameValue.toString().trim();
        if (rawClass.isEmpty()) return null;

        String className = normalizeClassName(rawClass);
        Domain domain = parseDomain(map.get("domain"), className);

        double probability = toDouble(map.get("probability"), 0.0);
        double x = toDouble(map.get("x"), 0.0);
        double y = toDouble(map.get("y"), 0.0);
        double width = toDouble(map.get("width"), 0.0);
        double height = toDouble(map.get("height"), 0.0);

        Map<String, Object> attributes = toMap(map.get("attributes"));

        String healthStatus = "unknown";
        Severity severity = Severity.NONE;
        String conditionName = null;

        Object healthObj = map.get("health");
        if (healthObj instanceof Map<?, ?> hm) {
            Object hs = hm.get("status");
            if (hs != null) healthStatus = hs.toString();
            Object sev = hm.get("severity");
            if (sev != null) severity = parseSeverity(sev.toString());
            Object hn = hm.get("name");
            if (hn != null) conditionName = hn.toString();
        }

        if ("unknown".equalsIgnoreCase(healthStatus) && looksLikeHealthIssue(className)) {
            healthStatus = "diseased";
            severity = inferSeverity(className, probability);
            conditionName = className;
        }

        String aiTreatment = toNonBlankString(map.get("treatment"));
        List<String> aiPrevention = toStringList(map.get("prevention"));
        String aiWhenToAct = toNonBlankString(map.get("whenToAct"));

        String treatment = resolveTreatment(className, domain, healthStatus, severity, aiTreatment);
        List<String> prevention = resolvePrevention(className, domain, healthStatus, aiPrevention);
        String whenToAct = resolveActionWindow(severity, healthStatus, aiWhenToAct);

        DetectionResult result = new DetectionResult(
                className, probability, x, y, width, height,
                treatment, prevention, whenToAct);
        result.domain = domain;
        result.attributes = attributes;
        result.healthStatus = healthStatus;
        result.severity = severity;
        result.conditionName = conditionName;
        return result;
    }

    private Domain parseDomain(Object raw, String className) {
        if (raw != null) {
            try {
                return Domain.valueOf(raw.toString().trim().toUpperCase());
            } catch (IllegalArgumentException ignored) { /* fall through */ }
        }
        return inferDomain(className);
    }

    private Severity parseSeverity(String raw) {
        try {
            return Severity.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            return Severity.LOW;
        }
    }

    private Map<String, Object> toMap(Object value) {
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        return Collections.emptyMap();
    }

    private double toDouble(Object value, double defaultValue) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value != null) {
            try { return Double.parseDouble(value.toString()); }
            catch (NumberFormatException ignored) { return defaultValue; }
        }
        return defaultValue;
    }

    private String toNonBlankString(Object value) {
        if (value == null) return null;
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof List<?>)) return Collections.emptyList();
        List<String> items = new ArrayList<>();
        for (Object obj : (List<?>) value) {
            String text = toNonBlankString(obj);
            if (text != null) items.add(text);
        }
        return items;
    }

    private String stripToJson(String text) throws IOException {
        if (text == null || text.isBlank()) throw new IOException("AI response text was empty");

        String cleaned = text.trim()
                .replace("```json", "").replace("```JSON", "").replace("```", "").trim();

        for (int i = 0; i < cleaned.length(); i++) {
            char ch = cleaned.charAt(i);
            if (ch == '{' || ch == '[') {
                String candidate = extractBalancedJson(cleaned, i);
                if (candidate != null && !candidate.isBlank()) return candidate;
            }
        }
        throw new IOException("AI response did not contain valid JSON. Raw: " + text);
    }

    private String extractBalancedJson(String input, int startIndex) {
        int depth = 0;
        boolean inString = false, escaped = false;

        for (int i = startIndex; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (ch == '\\') escaped = true;
                else if (ch == '"') inString = false;
                continue;
            }
            if (ch == '"') { inString = true; continue; }
            if (ch == '{' || ch == '[') depth++;
            else if (ch == '}' || ch == ']') {
                depth--;
                if (depth == 0) return input.substring(startIndex, i + 1);
            }
        }
        return null;
    }

    private String detectImageMimeType(byte[] imageData) {
        if (imageData.length >= 4 &&
                (imageData[0] & 0xFF) == 0x89 &&
                imageData[1] == 0x50 &&
                imageData[2] == 0x4E &&
                imageData[3] == 0x47) {
            return "image/png";
        }
        if (imageData.length >= 3 &&
                (imageData[0] & 0xFF) == 0xFF &&
                (imageData[1] & 0xFF) == 0xD8 &&
                (imageData[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        return "image/jpeg";
    }

    // ================================================================
    // LABEL NORMALIZATION & DOMAIN INFERENCE
    // ================================================================

    private String normalizeClassName(String raw) {
        if (raw == null) return "Unknown";
        String s = raw.trim().replaceAll("\\s+", " ");
        if (s.isEmpty()) return "Unknown";
        StringBuilder sb = new StringBuilder(s.length());
        boolean upperNext = true;
        for (char c : s.toCharArray()) {
            if (Character.isWhitespace(c)) { upperNext = true; sb.append(c); }
            else if (upperNext) { sb.append(Character.toUpperCase(c)); upperNext = false; }
            else sb.append(c);
        }
        return sb.toString();
    }

    private Domain inferDomain(String className) {
        String l = className.toLowerCase();

        if (containsAny(l, "leaf", "plant", "crop", "tree", "flower", "grass", "weed",
                "tomato", "cassava", "potato", "pepper", "maize", "corn", "rice",
                "mosaic", "blight", "rust", "mildew", "wilt", "rot", "fungus", "pest")) {
            return Domain.PLANT;
        }
        if (containsAny(l, "fruit", "vegetable", "bread", "soup", "stew", "meat",
                "fish", "drink", "bottle", "cup", "plate", "bowl", "apple", "banana",
                "orange", "mango", "yam", "beans")) {
            return Domain.FOOD;
        }
        if (containsAny(l, "person", "man", "woman", "child", "boy", "girl", "human",
                "dog", "cat", "cow", "goat", "sheep", "chicken", "bird", "insect",
                "bee", "butterfly", "ant", "snake", "lizard", "horse")) {
            return Domain.LIVING_THING;
        }
        if (containsAny(l, "car", "vehicle", "truck", "bus", "bike", "motorcycle",
                "tractor", "boat", "ship", "plane", "aircraft", "train")) {
            return Domain.VEHICLE;
        }
        if (containsAny(l, "building", "house", "hut", "shed", "tower", "bridge",
                "wall", "fence", "gate", "roof", "door", "window")) {
            return Domain.BUILDING;
        }
        if (containsAny(l, "phone", "laptop", "computer", "tv", "television", "radio",
                "camera", "speaker", "headphone", "charger", "tablet", "screen")) {
            return Domain.ELECTRONICS;
        }
        if (containsAny(l, "chair", "table", "bed", "sofa", "couch", "desk", "shelf", "cabinet")) {
            return Domain.FURNITURE;
        }
        if (containsAny(l, "shirt", "trouser", "dress", "shoe", "hat", "cap", "bag",
                "cloth", "jacket", "sandal", "slipper")) {
            return Domain.CLOTHING;
        }
        if (containsAny(l, "hammer", "hoe", "machete", "cutlass", "knife", "saw",
                "drill", "spanner", "wrench", "machine", "engine", "pump", "tool")) {
            return Domain.TOOL;
        }
        if (containsAny(l, "sky", "cloud", "sun", "moon", "star", "water", "river",
                "lake", "sea", "ocean", "mountain", "hill", "soil", "sand",
                "road", "street", "market", "farm", "field", "garden", "forest")) {
            return Domain.NATURE;
        }
        if (containsAny(l, "text", "sign", "label", "qr", "barcode", "document", "paper", "book")) {
            return Domain.DOCUMENT;
        }
        if (containsAny(l, "wound", "rash", "skin", "x-ray", "xray", "scan", "injury", "bruise")) {
            return Domain.MEDICAL;
        }
        return Domain.UNKNOWN;
    }

    private boolean containsAny(String haystack, String... needles) {
        for (String n : needles) if (haystack.contains(n)) return true;
        return false;
    }

    private boolean looksLikeHealthIssue(String className) {
        String l = className.toLowerCase();
        return containsAny(l, "disease", "blight", "rust", "mildew", "wilt", "rot",
                "mosaic", "virus", "fungus", "infection", "spot", "lesion",
                "wound", "injury", "rash", "swelling", "pest", "infestation");
    }

    private Severity inferSeverity(String className, double probability) {
        if (!looksLikeHealthIssue(className)) return Severity.NONE;
        String l = className.toLowerCase();

        if (containsAny(l, "late blight", "mosaic", "brown streak", "hemorrhagic", "poison")) {
            return probability > 0.5 ? Severity.CRITICAL : Severity.HIGH;
        }
        if (containsAny(l, "blight", "bacterial", "virus", "mold", "fungus", "infection")) {
            return probability > 0.6 ? Severity.HIGH : Severity.MODERATE;
        }
        if (containsAny(l, "spot", "mite", "curl", "pest", "wound", "rash")) {
            return probability > 0.6 ? Severity.MODERATE : Severity.LOW;
        }
        return probability > 0.7 ? Severity.MODERATE : Severity.LOW;
    }

    private boolean isHealthIssue(String className) {
        return looksLikeHealthIssue(className);
    }

    private boolean isHealthy(String className) {
        return className.toLowerCase().contains("healthy");
    }

    private boolean isEarlyLifeStage(String className) {
        String l = className.toLowerCase();
        return containsAny(l, "young", "seedling", "sprout", "early", "newborn",
                "baby", "infant", "chick", "puppy", "kitten", "germination",
                "emergence", "juvenile", "immature");
    }

    private String normalizeFocus(String focus) {
        if (focus == null || focus.isBlank() || "unknown".equalsIgnoreCase(focus)) {
            return DEFAULT_FOCUS;
        }
        return focus.trim();
    }

    private double getFocusScore(DetectionResult d, String focus) {
        double score = d.probability;
        if (focus == null || focus.isBlank() || DEFAULT_FOCUS.equalsIgnoreCase(focus)) return score;
        String f = focus.toLowerCase();
        String c = d.className.toLowerCase();
        if (c.contains(f)) score += 0.3;
        return score;
    }

    // ================================================================
    // TREATMENT / PREVENTION / ACTION
    // ================================================================

    private String resolveTreatment(String className, Domain domain, String healthStatus,
                                    Severity severity, String aiTreatment) {
        if (aiTreatment != null) return aiTreatment;

        if (!"diseased".equalsIgnoreCase(healthStatus)
                && !"injured".equalsIgnoreCase(healthStatus)) {
            if (domain == Domain.PLANT) return "Plant appears healthy. Keep monitoring.";
            if (domain == Domain.LIVING_THING) return "Subject appears healthy.";
            return "No action required.";
        }

        if (domain == Domain.PLANT) {
            return "Remove affected parts, apply appropriate treatment, and isolate if needed.";
        }
        if (domain == Domain.LIVING_THING) {
            return "Isolate if contagious. Consult a veterinarian or doctor as appropriate.";
        }
        if (domain == Domain.MEDICAL) {
            return "Consult a qualified medical professional. This is not a diagnosis.";
        }
        return "Consult a relevant expert for treatment.";
    }

    private List<String> resolvePrevention(String className, Domain domain,
                                           String healthStatus, List<String> aiPrevention) {
        if (aiPrevention != null && !aiPrevention.isEmpty()) return aiPrevention;

        List<String> tips = new ArrayList<>();
        if (domain == Domain.PLANT) {
            tips.add("Use disease-free seeds and clean tools");
            tips.add("Ensure good spacing and drainage");
            tips.add("Rotate crops and monitor regularly");
        } else if (domain == Domain.LIVING_THING) {
            tips.add("Maintain hygiene and regular check-ups");
            tips.add("Provide balanced nutrition and clean water");
        } else if (domain == Domain.MEDICAL) {
            tips.add("Seek professional evaluation");
            tips.add("Avoid self-diagnosis");
        } else {
            tips.add("Continue regular monitoring");
            tips.add("Document changes over time");
        }
        return tips;
    }

    private String resolveActionWindow(Severity severity, String healthStatus, String aiWhenToAct) {
        if (aiWhenToAct != null) return aiWhenToAct;
        if (!"diseased".equalsIgnoreCase(healthStatus)
                && !"injured".equalsIgnoreCase(healthStatus)) {
            return "No immediate action required.";
        }
        return switch (severity) {
            case CRITICAL -> "Take action within 24 hours.";
            case HIGH     -> "Take action within 1-2 days.";
            case MODERATE -> "Take action within 3-5 days.";
            case LOW      -> "Monitor and act if it worsens.";
            case NONE     -> "No action required.";
        };
    }

    // ================================================================
    // JSON BUILDING
    // ================================================================

    private String buildComprehensiveDetectionJson(
            List<DetectionResult> allResults,
            String deviceId, String location, String focus) {

        try {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("type", "universal_detection");
            response.put("deviceId", deviceId != null ? deviceId : "unknown");
            response.put("location", location != null ? location : "unknown");
            response.put("focus", focus != null ? focus : DEFAULT_FOCUS);
            response.put("cropType", focus != null ? focus : DEFAULT_FOCUS); // legacy alias
            response.put("count", allResults.size());
            response.put("timestamp", System.currentTimeMillis());
            response.put("detectionTime", lastDetectionTime);
            response.put("modelLoaded", modelLoaded);
            response.put("aiMode", lastAiModeUsed);

            Map<String, List<Map<String, Object>>> byDomain = new LinkedHashMap<>();
            for (DetectionResult r : allResults) {
                String key = r.domain != null ? r.domain.name() : Domain.UNKNOWN.name();
                byDomain.computeIfAbsent(key, k -> new ArrayList<>()).add(buildResultItem(r));
            }
            response.put("byDomain", byDomain);

            if (!allResults.isEmpty()) {
                DetectionResult top = allResults.get(0);
                response.put("topDetection", top.className);
                response.put("topConfidence", Math.round(top.probability * 100));
                response.put("topDomain", top.domain != null ? top.domain.name() : "UNKNOWN");
                response.put("isEarlyLife", isEarlyLifeStage(top.className));
                response.put("severity", top.severity);
                response.put("severityDescription", top.severity.description);
                response.put("requiresAction",
                        top.severity == Severity.CRITICAL || top.severity == Severity.HIGH);
                response.put("healthStatus", top.healthStatus);
            } else {
                response.put("healthStatus", "NO_DETECTION");
            }

            response.put("results", allResults.stream()
                    .map(this::buildResultItem)
                    .collect(Collectors.toList()));

            response.put("summary", buildSummary(allResults));

            return objectMapper.writeValueAsString(response);

        } catch (Exception e) {
            logger.error("Error building detection JSON: {}", e.getMessage());
            return String.format(
                    "{\"type\":\"detection\",\"deviceId\":\"%s\",\"count\":0,\"results\":[],\"error\":\"%s\",\"timestamp\":%d}",
                    deviceId != null ? deviceId : "unknown",
                    escapeJson(e.getMessage()),
                    System.currentTimeMillis());
        }
    }

    private String buildSummary(List<DetectionResult> results) {
        if (results.isEmpty()) return "Nothing recognizable detected.";
        DetectionResult top = results.get(0);
        String base = String.format("Detected %d item(s). Most likely: %s (%.0f%%).",
                results.size(), top.className, top.probability * 100);
        if (isEarlyLifeStage(top.className)) base += " Early life stage detected.";
        return base;
    }

    private Map<String, Object> buildResultItem(DetectionResult r) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("className", r.className);
        item.put("domain", r.domain != null ? r.domain.name() : "UNKNOWN");
        item.put("probability", r.probability);
        item.put("confidence", Math.round(r.probability * 100));
        item.put("isHealthIssue", isHealthIssue(r.className));
        item.put("isCropDisease", isHealthIssue(r.className)); // legacy alias
        item.put("isEarlyLife", isEarlyLifeStage(r.className));
        item.put("isHealthy", isHealthy(r.className));
        item.put("healthStatus", r.healthStatus);
        item.put("severity", r.severity);
        item.put("severityDescription", r.severity.description);
        item.put("conditionName", r.conditionName);
        item.put("treatment", r.treatment);
        item.put("prevention", r.prevention);
        item.put("whenToAct", r.whenToAct);
        item.put("attributes", r.attributes);

        if (r.width > 0 && r.height > 0) {
            Map<String, Double> bbox = new LinkedHashMap<>();
            bbox.put("x", r.x);
            bbox.put("y", r.y);
            bbox.put("width", r.width);
            bbox.put("height", r.height);
            item.put("bbox", bbox);
        }
        return item;
    }

    // ================================================================
    // UTILITIES
    // ================================================================

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private void startCacheCleanup() {
        detectionExecutor.submit(() -> {
            while (true) {
                try {
                    Thread.sleep(3_600_000L);
                    detectionCache.clear();
                    logger.debug("🧹 Detection cache cleared");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    // ================================================================
    // STATISTICS / ACCESSORS
    // ================================================================

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        long total = totalDetections.get();
        stats.put("modelLoaded", modelLoaded);
        stats.put("aiMode", lastAiModeUsed);
        stats.put("aiStack", getAiStackInfo());
        stats.put("totalDetections", total);
        stats.put("successfulDetections", successfulDetections.get());
        stats.put("failedDetections", failedDetections.get());
        stats.put("averageDetectionTime", total > 0 ? detectionTimeSum.get() / total : 0);
        stats.put("lastDetectionTime", lastDetectionTime);
        stats.put("cachedDevices", detectionCache.size());
        stats.put("activeDevices", lastDetection.size());
        stats.put("detectionCapabilities", Arrays.asList(
                "People & Animals",
                "Plants & Crop Diseases",
                "Food & Drinks",
                "Vehicles",
                "Buildings",
                "Electronics",
                "Furniture",
                "Clothing",
                "Tools & Machines",
                "Nature & Scenes",
                "Documents & Text",
                "Medical Signs",
                "Early Life Stage Detection"));
        return stats;
    }

    public String getDefaultFocus() {
        return DEFAULT_FOCUS;
    }

    /** Legacy alias — kept so old callers keep compiling. */
    public String getDefaultCropFocus() {
        return DEFAULT_FOCUS;
    }

    public Map<String, List<DetectionResult>> getDetectionCache() {
        return detectionCache;
    }

    public Map<String, DetectionResult> getLastDetections() {
        return lastDetection;
    }

    public boolean isModelLoaded() {
        return modelLoaded;
    }

    // ================================================================
    // CLEANUP
    // ================================================================

    @PreDestroy
    public void destroy() {
        logger.info("🔄 Shutting down ObjectDetectionService...");

        detectionExecutor.shutdown();
        try {
            if (!detectionExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                detectionExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            detectionExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("✅ Detection resources released.");
        logger.info("   Total detections processed: {}", totalDetections.get());
        logger.info("   Successful: {}, Failed: {}",
                successfulDetections.get(), failedDetections.get());
    }

    // ================================================================
    // INNER CLASS
    // ================================================================

    public static class DetectionResult {
        public String className;
        public Domain domain = Domain.UNKNOWN;
        public double probability;
        public double x, y, width, height;
        public Map<String, Object> attributes = new LinkedHashMap<>();
        public String healthStatus = "unknown";
        public Severity severity = Severity.NONE;
        public String conditionName;
        public String treatment;
        public List<String> prevention;
        public String whenToAct;
        public long timestamp;

        public DetectionResult(String className, double probability,
                               double x, double y, double width, double height) {
            this(className, probability, x, y, width, height, null, null, null);
        }

        public DetectionResult(
                String className, double probability,
                double x, double y, double width, double height,
                String treatment, List<String> prevention, String whenToAct) {
            this.className = className;
            this.probability = probability;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.treatment = treatment;
            this.prevention = prevention != null ? new ArrayList<>(prevention) : new ArrayList<>();
            this.whenToAct = whenToAct;
            this.timestamp = System.currentTimeMillis();
        }

        @Override
        public String toString() {
            return String.format("%s [%s]: %.1f%%", className, domain, probability * 100);
        }
    }
}