package sms.com.sms;

import ai.djl.Application;
import ai.djl.engine.Engine;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sms.com.sms.config.CameraWebSocketHandler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Paths;
import java.time.Duration;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.Base64;

@Service
public class ObjectDetectionService {

    private static final Logger logger = LoggerFactory.getLogger(ObjectDetectionService.class);
    private static final DecimalFormat df = new DecimalFormat("#.#");
    private static final String DEFAULT_CROP_FOCUS = "Cassava Leaf";
    private static final String AI_MODE_GEMINI = "Gemini";
    private static final String AI_MODE_OPENAI = "OpenAI";
    private static final String AI_MODE_UNAVAILABLE = "Unavailable";

    // Thread pool for async detection
    private final ExecutorService detectionExecutor = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() - 1)
    );

    // Cache for detections
    private final Map<String, List<DetectionResult>> detectionCache = new ConcurrentHashMap<>();

    // Keep last detection for each device
    private final Map<String, DetectionResult> lastDetection = new ConcurrentHashMap<>();

    private Predictor<Image, Classifications> predictor;
    private ZooModel<Image, Classifications> model;
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

    // Model status
    private boolean modelLoaded = false;
    private volatile String lastAiModeUsed = AI_MODE_UNAVAILABLE;
    private long totalDetections = 0;
    private long detectionTimeSum = 0;
    private long lastDetectionTime = 0;
    private long successfulDetections = 0;
    private long failedDetections = 0;

    // ================================================================
    // COMPREHENSIVE DETECTION LABELS
    // ================================================================

    // Crop disease labels
    private static final List<String> CROP_DISEASE_LABELS = Arrays.asList(
            // Cassava
            "Cassava_Bacterial_Blight",
            "Cassava_Brown_Streak",
            "Cassava_Green_Mite",
            "Cassava_Mosaic_Disease",
            "Cassava_Healthy",
            // Tomato
            "Tomato_Early_Blight",
            "Tomato_Late_Blight",
            "Tomato_Leaf_Mold",
            "Tomato_Septoria_Leaf_Spot",
            "Tomato_Spider_Mites",
            "Tomato_Target_Spot",
            "Tomato_Yellow_Leaf_Curl",
            "Tomato_Mosaic_Virus",
            "Tomato_Healthy",
            // Potato
            "Potato_Early_Blight",
            "Potato_Late_Blight",
            "Potato_Healthy",
            // Pepper
            "Pepper_Bacterial_Spot",
            "Pepper_Healthy"
    );

    // General object categories
    private static final List<String> GENERAL_OBJECTS = Arrays.asList(
            "Person", "Animal", "Vehicle", "Building", "Tool",
            "Machine", "Equipment", "Container", "Fruit", "Vegetable",
            "Leaf", "Flower", "Root", "Soil", "Water",
            "Pest", "Insect", "Bird", "Rodent", "Weed"
    );

    // Early life indicators
    private static final List<String> EARLY_LIFE_INDICATORS = Arrays.asList(
            "young", "seedling", "sprout", "early", "new", "small",
            "germination", "emergence", "juvenile", "immature"
    );

    // Confidence threshold - balanced for general detection
    private static final double MIN_CONFIDENCE = 0.25;

    // Severity levels
    private enum DiseaseSeverity {
        CRITICAL("Immediate action required"),
        HIGH("Severe - treat immediately"),
        MODERATE("Monitor and treat"),
        LOW("Early signs - monitor closely"),
        NONE("No issues detected");

        public final String description;
        DiseaseSeverity(String description) {
            this.description = description;
        }
    }

    public ObjectDetectionService(CameraWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @PostConstruct
    public void init() {
        logger.info("🌿 Initializing AI crop-disease detection service...");
        logger.info("   Thread Pool Size: {}", detectionExecutor.getClass().getSimpleName());
        logger.info("   Gemini model: {}", geminiModel);
        logger.info("   Gemini endpoint: {}", geminiEndpoint);
        logger.info("   Gemini retries: {}", geminiMaxRetries);
        logger.info("   OpenAI model: {}", openAiModel);
        logger.info("   OpenAI endpoint: {}", openAiEndpoint);

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
        logger.info("   Registered DJL engines: {}", formatDjlEngines());
        logger.info("   Default crop focus: {}", DEFAULT_CROP_FOCUS);
        logger.info("   Detection Capabilities: Crops (Cassava, Tomato, Potato, Pepper) + General Objects");
    }

    // ================================================================
    // MAIN DETECTION METHODS
    // ================================================================

    public void processAndSendDetection(byte[] imageData) {
        processAndSendDetection(imageData, null, null, DEFAULT_CROP_FOCUS);
    }

    public void processAndSendDetection(byte[] imageData, String deviceId, String location, String cropType) {
        if (imageData == null || imageData.length == 0) {
            logger.warn("Empty image data received from device: {}", deviceId);
            return;
        }

        detectionExecutor.submit(() -> {
            try {
                long startTime = System.nanoTime();
                String normalizedCropType = normalizeCropType(cropType);

                // Perform comprehensive detection
                List<DetectionResult> results = detectEverything(imageData, normalizedCropType);

                long endTime = System.nanoTime();
                long detectionTime = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);

                totalDetections++;
                detectionTimeSum += detectionTime;
                lastDetectionTime = detectionTime;

                // Filter and categorize results
                List<DetectionResult> cropResults = results.stream()
                        .filter(r -> r.probability >= MIN_CONFIDENCE)
                        .sorted((a, b) -> Double.compare(
                                getFocusScore(b.className, b.probability, normalizedCropType),
                                getFocusScore(a.className, a.probability, normalizedCropType)
                        ))
                        .collect(Collectors.toList());

                // Separate crop diseases from general objects
                List<DetectionResult> diseaseResults = cropResults.stream()
                        .filter(r -> isCropDisease(r.className))
                        .collect(Collectors.toList());

                List<DetectionResult> generalResults = cropResults.stream()
                        .filter(r -> !isCropDisease(r.className))
                        .collect(Collectors.toList());

                // Log results
                if (!cropResults.isEmpty()) {
                    successfulDetections++;
                    DetectionResult top = cropResults.get(0);

                    String category = isCropDisease(top.className) ? "CROP_DISEASE" : "GENERAL_OBJECT";
                    boolean isEarlyLife = isEarlyLifeStage(top.className);

                    logger.info("🔍 Device {}: Detected {} items in {}ms [Category: {}]",
                            deviceId != null ? deviceId : "unknown",
                            cropResults.size(),
                            detectionTime,
                            category);

                    logger.info("   🏆 Top: {} ({}%) - {}",
                            top.className,
                            df.format(top.probability * 100),
                            isEarlyLife ? "⚠️ EARLY LIFE STAGE" : "");

                    if (isEarlyLife) {
                        logger.warn("   🌱 Early life stage detected! Monitor closely.");
                    }

                    if (deviceId != null) {
                        lastDetection.put(deviceId, top);
                    }

                    // Log disease summary if any
                    if (!diseaseResults.isEmpty()) {
                        logger.info("   🦠 Diseases detected: {}",
                                diseaseResults.stream()
                                        .map(r -> r.className)
                                        .collect(Collectors.joining(", ")));
                    }

                } else {
                    logger.debug("No significant detections from device: {}", deviceId);
                    if (deviceId != null) {
                        lastDetection.remove(deviceId);
                    }
                }

                // Build and send comprehensive JSON
                String detectionJson = buildComprehensiveDetectionJson(
                        cropResults, diseaseResults, generalResults,
                        deviceId, location, normalizedCropType
                );
                webSocketHandler.sendDetectionToClients(detectionJson);

                if (deviceId != null) {
                    detectionCache.put(deviceId, cropResults);
                }

            } catch (Exception e) {
                failedDetections++;
                logger.error("❌ Error processing detection: {}", e.getMessage(), e);
                String emptyJson = String.format(
                        "{\"type\":\"detection\",\"deviceId\":\"%s\",\"count\":0,\"results\":[],\"error\":\"%s\",\"timestamp\":%d}",
                        deviceId != null ? deviceId : "unknown",
                        escapeJson(e.getMessage()),
                        System.currentTimeMillis()
                );
                webSocketHandler.sendDetectionToClients(emptyJson);
            }
        });
    }

    /**
     * Detect everything - crops, diseases, and general objects
     */
    public List<DetectionResult> detectEverything(byte[] imageData) throws IOException {
        return detectEverything(imageData, DEFAULT_CROP_FOCUS);
    }

    public List<DetectionResult> detectEverything(byte[] imageData, String cropType) throws IOException {
        return detectEverything(imageData, cropType, null);
    }

    public List<DetectionResult> detectEverything(byte[] imageData, String cropType, Integer maxAttemptsOverride) throws IOException {
        String normalizedCropType = normalizeCropType(cropType);
        if (!modelLoaded) {
            throw new IllegalStateException("No AI provider is configured. Set gemini.api-key/GEMINI_API_KEY or openai.api-key/OPENAI_API_KEY.");
        }

        logger.info(
                "Detection request received. cropType={}, imageBytes={}, installedAiModes={}, configuredProviders={}",
                normalizedCropType,
                imageData != null ? imageData.length : 0,
                String.join(", ", getInstalledAiModes()),
                String.join(", ", getConfiguredAiProviders())
        );

        if (isGeminiConfigured()) {
            try {
                lastAiModeUsed = AI_MODE_GEMINI;
                logger.info("Running detection with AI mode: {}", lastAiModeUsed);
                String responseText = callGeminiVisionApi(imageData, normalizedCropType, maxAttemptsOverride);
                return parseGeminiDetections(responseText, normalizedCropType);
            } catch (IOException geminiException) {
                if (!isOpenAiConfigured()) {
                    throw geminiException;
                }
                logger.warn("Gemini provider failed for detection request. Falling back to OpenAI: {}", geminiException.getMessage());
            }
        }

        lastAiModeUsed = AI_MODE_OPENAI;
        logger.info("Running detection with AI mode: {}", lastAiModeUsed);
        String responseText = callOpenAIVisionApi(imageData, normalizedCropType, maxAttemptsOverride);
        return parseGeminiDetections(responseText, normalizedCropType);
    }

    public static class GeminiServiceUnavailableException extends IOException {
        public GeminiServiceUnavailableException(String message) {
            super(message);
        }
    }

    private boolean isGeminiConfigured() {
        return geminiApiKey != null && !geminiApiKey.isBlank();
    }

    private boolean isOpenAiConfigured() {
        return openAiApiKey != null && !openAiApiKey.isBlank();
    }

    private void logAiModeInventory(boolean geminiReady, boolean openAiReady) {
        logger.info(
                "AI stack inventory: configuredProviders={}, installedModes={}, djlEngines={}",
                String.join(", ", getConfiguredAiProviders(geminiReady, openAiReady)),
                String.join(", ", getInstalledAiModes()),
                formatDjlEngines()
        );
        logger.info(
                "DJL classpath checks: api={}, model-zoo={}, pytorch={}",
                isClassAvailable("ai.djl.Application"),
                isClassAvailable("ai.djl.repository.zoo.Criteria"),
                isClassAvailable("ai.djl.pytorch.engine.PtEngine")
        );
    }

    private List<String> getConfiguredAiProviders(boolean geminiReady, boolean openAiReady) {
        List<String> configuredProviders = new ArrayList<>();
        if (geminiReady) {
            configuredProviders.add(AI_MODE_GEMINI);
        }
        if (openAiReady) {
            configuredProviders.add(AI_MODE_OPENAI);
        }
        if (configuredProviders.isEmpty()) {
            configuredProviders.add("none");
        }
        return configuredProviders;
    }

    public List<String> getConfiguredAiProviders() {
        return getConfiguredAiProviders(isGeminiConfigured(), isOpenAiConfigured());
    }

    public List<String> getInstalledAiModes() {
        List<String> installedModes = new ArrayList<>();
        if (isClassAvailable("ai.djl.Application")) {
            installedModes.add("ai.djl");
        }
        if (isClassAvailable("ai.djl.repository.zoo.Criteria")) {
            installedModes.add("model-zoo");
        }
        if (isPyTorchAvailable()) {
            installedModes.add("pytorch");
        }
        if (isGeminiConfigured()) {
            installedModes.add(AI_MODE_GEMINI);
        }
        if (isOpenAiConfigured()) {
            installedModes.add(AI_MODE_OPENAI);
        }
        if (installedModes.isEmpty()) {
            installedModes.add("none");
        }
        return installedModes;
    }

    public List<String> getDjlEngines() {
        if (!isClassAvailable("ai.djl.Application")) {
            return Collections.emptyList();
        }

        try {
            List<String> engines = new ArrayList<>(Engine.getAllEngines());
            engines.sort(String.CASE_INSENSITIVE_ORDER);
            return engines;
        } catch (RuntimeException ex) {
            logger.warn("Unable to enumerate DJL engines: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    public String getLastAiModeUsed() {
        return lastAiModeUsed;
    }

    public Map<String, Object> getAiStackInfo() {
        Map<String, Object> aiStack = new LinkedHashMap<>();
        aiStack.put("activeMode", lastAiModeUsed);
        aiStack.put("configuredProviders", getConfiguredAiProviders());
        aiStack.put("installedModes", getInstalledAiModes());
        aiStack.put("djlEngines", getDjlEngines());
        return aiStack;
    }

    private boolean isPyTorchAvailable() {
        if (isClassAvailable("ai.djl.pytorch.engine.PtEngine")) {
            return true;
        }

        return getDjlEngines().stream().anyMatch(engine -> "PyTorch".equalsIgnoreCase(engine));
    }

    private String formatDjlEngines() {
        List<String> engines = getDjlEngines();
        return engines.isEmpty() ? "none" : String.join(", ", engines);
    }

    private boolean isClassAvailable(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    private String callGeminiVisionApi(byte[] imageData, String cropType, Integer maxAttemptsOverride) throws IOException {
        try {
            String mimeType = detectImageMimeType(imageData);
            String base64Image = Base64.getEncoder().encodeToString(imageData);

            Map<String, Object> inlineData = new LinkedHashMap<>();
            inlineData.put("mime_type", mimeType);
            inlineData.put("data", base64Image);

            Map<String, Object> textPart = new LinkedHashMap<>();
            textPart.put("text", buildGeminiPrompt(cropType));

            Map<String, Object> imagePart = new LinkedHashMap<>();
            imagePart.put("inline_data", inlineData);

            Map<String, Object> content = new LinkedHashMap<>();
            content.put("role", "user");
            content.put("parts", Arrays.asList(textPart, imagePart));

            Map<String, Object> generationConfig = new LinkedHashMap<>();
            generationConfig.put("temperature", 0.2);
            generationConfig.put("topP", 0.8);
            generationConfig.put("maxOutputTokens", 512);
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
                    logger.warn("Gemini API returned HTTP {} on attempt {}/{}. Retrying in {} ms.",
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
                                + "Last response: " + lastRetriableBody
                );
            }

            throw new IOException("Gemini API retries exhausted without a successful response.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Gemini API request was interrupted", e);
        }
    }

    private String callOpenAIVisionApi(byte[] imageData, String cropType, Integer maxAttemptsOverride) throws IOException {
        try {
            String mimeType = detectImageMimeType(imageData);
            String base64Image = Base64.getEncoder().encodeToString(imageData);
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", normalizeOpenAiModel(),
                    "temperature", 0.2,
                    "max_tokens", 512,
                    "messages", List.of(Map.of(
                            "role", "user",
                            "content", List.of(
                                    Map.of("type", "text", "text", buildGeminiPrompt(cropType)),
                                    Map.of("type", "image_url", "image_url",
                                            Map.of("url", "data:" + mimeType + ";base64," + base64Image))
                            )
                    ))
            ));

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
                    logger.warn("OpenAI API returned HTTP {} on attempt {}/{}. Retrying in {} ms.",
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
                                + "Last response: " + lastRetriableBody
                );
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
        if (endpoint.isBlank()) {
            endpoint = "https://api.openai.com/v1";
        }
        endpoint = endpoint.replaceAll("/+$", "");
        if (endpoint.endsWith("/chat/completions")) {
            return endpoint;
        }
        return endpoint + "/chat/completions";
    }

    private String extractOpenAiText(String rawResponseBody) throws IOException {
        Map<?, ?> responseMap = objectMapper.readValue(rawResponseBody, Map.class);
        List<?> choices = (List<?>) responseMap.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IOException("OpenAI API returned no choices. Raw response: " + rawResponseBody);
        }

        Object firstChoice = choices.get(0);
        if (!(firstChoice instanceof Map)) {
            throw new IOException("OpenAI API response did not include a valid choice. Raw response: " + rawResponseBody);
        }

        Map<?, ?> choiceMap = (Map<?, ?>) firstChoice;
        Map<?, ?> messageMap = (Map<?, ?>) choiceMap.get("message");
        if (messageMap == null) {
            throw new IOException("OpenAI API response did not include a message. Raw response: " + rawResponseBody);
        }

        Object content = messageMap.get("content");
        if (content instanceof String) {
            return (String) content;
        }

        if (content instanceof List<?>) {
            StringBuilder combined = new StringBuilder();
            for (Object item : (List<?>) content) {
                if (!(item instanceof Map)) {
                    continue;
                }
                Map<?, ?> part = (Map<?, ?>) item;
                Object text = part.get("text");
                if (text != null) {
                    combined.append(text);
                }
            }
            if (combined.length() > 0) {
                return combined.toString();
            }
        }

        throw new IOException("OpenAI API response did not include generated text. Raw response: " + rawResponseBody);
    }

    private String extractGeminiText(String rawResponseBody) throws IOException {
        Map<?, ?> responseMap = objectMapper.readValue(rawResponseBody, Map.class);
        List<?> candidates = (List<?>) responseMap.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new IOException("Gemini API returned no candidates. Raw response: " + rawResponseBody);
        }

        for (Object candidateObj : candidates) {
            if (!(candidateObj instanceof Map)) {
                continue;
            }
            Map<?, ?> candidateMap = (Map<?, ?>) candidateObj;
            Map<?, ?> contentMap = (Map<?, ?>) candidateMap.get("content");
            if (contentMap == null) {
                continue;
            }

            List<?> parts = (List<?>) contentMap.get("parts");
            if (parts == null || parts.isEmpty()) {
                continue;
            }

            StringBuilder combinedText = new StringBuilder();
            for (Object partObj : parts) {
                if (!(partObj instanceof Map)) {
                    continue;
                }
                Map<?, ?> partMap = (Map<?, ?>) partObj;
                Object text = partMap.get("text");
                if (text != null) {
                    combinedText.append(text);
                }
            }

            if (combinedText.length() > 0) {
                return combinedText.toString();
            }
        }

        throw new IOException("Gemini API response did not include generated text. Raw response: " + rawResponseBody);
    }

    private long getRetryDelayMs(int attempt, Optional<String> retryAfterHeader) {
        long baseDelayMs = Math.max(250L, geminiRetryInitialDelayMs);
        long multiplier = 1L << Math.min(10, Math.max(0, attempt - 1));
        long exponentialDelay = baseDelayMs * multiplier;
        long cappedExponentialDelay = Math.min(exponentialDelay, Math.max(1000L, geminiRetryMaxDelayMs));
        long jitter = ThreadLocalRandom.current().nextLong(200L, 800L);
        long computedDelay = cappedExponentialDelay + jitter;

        if (retryAfterHeader.isPresent()) {
            String value = retryAfterHeader.get().trim();
            try {
                long retryAfterSeconds = Long.parseLong(value);
                if (retryAfterSeconds > 0) {
                    long retryAfterMs = retryAfterSeconds * 1000L;
                    return Math.min(Math.max(computedDelay, retryAfterMs), Math.max(1000L, geminiRetryMaxDelayMs));
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed header and use computed backoff.
            }
        }

        return Math.min(computedDelay, Math.max(1000L, geminiRetryMaxDelayMs));
    }

    private String buildGeminiGenerateContentUrl() throws IOException {
        String endpoint = geminiEndpoint == null ? "" : geminiEndpoint.trim();
        String model = geminiModel == null ? "" : geminiModel.trim();

        if (endpoint.isEmpty()) {
            throw new IOException("Gemini endpoint is not configured.");
        }
        if (model.isEmpty()) {
            throw new IOException("Gemini model is not configured.");
        }

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

        if (model.isBlank()) {
            throw new IOException("Gemini model name is invalid after normalization.");
        }

        String normalizedEndpoint = endpoint.endsWith("/models") ? endpoint : endpoint + "/models";
        return normalizedEndpoint + "/" + model + ":generateContent";
    }

    private String buildGeminiPrompt(String cropType) {
        return String.format(
                "You are a crop disease image classifier for %s. " +
                        "Analyze the image and respond with ONLY valid JSON. No markdown, no code fences, no explanation. " +
                        "Use this exact schema: {\"items\":[{\"className\":\"string\",\"probability\":0.0,\"x\":0.1,\"y\":0.1,\"width\":0.7,\"height\":0.7,\"treatment\":\"string\",\"prevention\":[\"string\"],\"whenToAct\":\"string\"}]}. " +
                        "Use only these labels when relevant: %s. " +
                        "If the image is a general object, use only these labels: %s. " +
                        "Return at most 5 items sorted from most likely to least likely. " +
                        "Probability must be a number between 0 and 1.",
                cropType,
                String.join(", ", CROP_DISEASE_LABELS),
                String.join(", ", GENERAL_OBJECTS)
        );
    }

    private List<DetectionResult> parseGeminiDetections(String responseText, String cropType) throws IOException {
        try {
            String jsonText = stripToJson(responseText);
            Object parsed = objectMapper.readValue(jsonText, Object.class);

            List<DetectionResult> detections = new ArrayList<>();
            if (parsed instanceof Map<?, ?>) {
                Map<?, ?> responseMap = (Map<?, ?>) parsed;
                Object items = responseMap.get("items");
                if (items instanceof List) {
                    for (Object item : (List<?>) items) {
                        DetectionResult detection = parseDetectionItem(item, cropType);
                        if (detection != null && detection.probability >= MIN_CONFIDENCE) {
                            detections.add(detection);
                        }
                    }
                } else {
                    DetectionResult single = parseDetectionItem(responseMap, cropType);
                    if (single != null && single.probability >= MIN_CONFIDENCE) {
                        detections.add(single);
                    }
                }
            } else if (parsed instanceof List<?>) {
                for (Object item : (List<?>) parsed) {
                    DetectionResult detection = parseDetectionItem(item, cropType);
                    if (detection != null && detection.probability >= MIN_CONFIDENCE) {
                        detections.add(detection);
                    }
                }
            }

            if (detections.isEmpty()) {
                throw new IOException("Gemini returned no usable detections for raw response: " + responseText);
            }

            return detections.stream()
                    .sorted((a, b) -> Double.compare(
                            getFocusScore(b.className, b.probability, cropType),
                            getFocusScore(a.className, a.probability, cropType)
                    ))
                    .limit(5)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("Gemini response parsing failed. Raw response: {}", responseText, e);
            throw new IOException("Gemini response parsing failed. Raw response: " + responseText, e);
        } catch (Exception e) {
            logger.error("Unexpected Gemini parsing error. Raw response: {}", responseText, e);
            throw new IOException("Unexpected Gemini parsing error. Raw response: " + responseText, e);
        }
    }

    private DetectionResult parseDetectionItem(Object item, String cropType) {
        if (!(item instanceof Map)) {
            return null;
        }

        Map<?, ?> map = (Map<?, ?>) item;
        Object classNameValue = map.get("className");
        if (classNameValue == null) {
            classNameValue = map.get("label");
        }
        if (classNameValue == null) {
            return null;
        }

        String mappedClassName = mapToComprehensiveLabel(classNameValue.toString(), cropType);
        double probability = toDouble(map.get("probability"), 0.0);
        double x = toDouble(map.get("x"), 0.1);
        double y = toDouble(map.get("y"), 0.1);
        double width = toDouble(map.get("width"), 0.7);
        double height = toDouble(map.get("height"), 0.7);
        String aiTreatment = toNonBlankString(map.get("treatment"));
        List<String> aiPrevention = toStringList(map.get("prevention"));
        String aiWhenToAct = toNonBlankString(map.get("whenToAct"));

        return new DetectionResult(
                mappedClassName,
                probability,
                x,
                y,
                width,
                height,
                resolveTreatmentSuggestion(mappedClassName, aiTreatment),
                resolvePreventionTips(mappedClassName, aiPrevention),
                resolveActionWindow(mappedClassName, probability, aiWhenToAct)
        );
    }

    private double toDouble(Object value, double defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private String toNonBlankString(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof List<?>)) {
            return Collections.emptyList();
        }
        List<String> items = new ArrayList<>();
        for (Object obj : (List<?>) value) {
            String text = toNonBlankString(obj);
            if (text != null) {
                items.add(text);
            }
        }
        return items;
    }

    private String stripToJson(String text) throws IOException {
        if (text == null || text.isBlank()) {
            throw new IOException("Gemini response text was empty");
        }

        String cleaned = text.trim();
        cleaned = cleaned.replace("```json", "").replace("```JSON", "").replace("```", "").trim();

        for (int i = 0; i < cleaned.length(); i++) {
            char ch = cleaned.charAt(i);
            if (ch == '{' || ch == '[') {
                String candidate = extractBalancedJson(cleaned, i);
                if (candidate != null && !candidate.isBlank()) {
                    return candidate;
                }
            }
        }

        throw new IOException("Gemini response did not contain valid JSON. Raw response: " + text);
    }

    private String extractBalancedJson(String input, int startIndex) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = startIndex; i < input.length(); i++) {
            char ch = input.charAt(i);

            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }

            if (ch == '"') {
                inString = true;
                continue;
            }

            if (ch == '{' || ch == '[') {
                depth++;
            } else if (ch == '}' || ch == ']') {
                depth--;
                if (depth == 0) {
                    return input.substring(startIndex, i + 1);
                }
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
    // COMPREHENSIVE LABEL MAPPING
    // ================================================================

    private String mapToComprehensiveLabel(String className, String cropType) {
        String lower = className.toLowerCase();

        if (isCassavaFocus(cropType)) {
            if (lower.contains("mosaic") || lower.contains("virus")) {
                return "Cassava_Mosaic_Disease";
            }
            if (lower.contains("brown") && (lower.contains("streak") || lower.contains("stripe"))) {
                return "Cassava_Brown_Streak";
            }
            if (lower.contains("bacterial") || lower.contains("blight")) {
                return "Cassava_Bacterial_Blight";
            }
            if (lower.contains("mite") || lower.contains("pest")) {
                return "Cassava_Green_Mite";
            }
            if (lower.contains("cassava leaf") || lower.contains("leaf") || lower.contains("foliage")
                    || lower.contains("plant") || lower.contains("healthy") || lower.contains("normal")) {
                return "Cassava_Healthy";
            }
        }

        // ========== CASSAVA DISEASES ==========
        if (lower.contains("cassava") || lower.contains("manihot")) {
            if (lower.contains("mosaic") || lower.contains("cmv")) {
                return "Cassava_Mosaic_Disease";
            }
            if (lower.contains("brown streak") || lower.contains("cbsd")) {
                return "Cassava_Brown_Streak";
            }
            if (lower.contains("bacterial blight") || lower.contains("cbb")) {
                return "Cassava_Bacterial_Blight";
            }
            if (lower.contains("green mite") || lower.contains("mite")) {
                return "Cassava_Green_Mite";
            }
            if (lower.contains("healthy") || lower.contains("normal")) {
                return "Cassava_Healthy";
            }
            return "Cassava_Disease";
        }

        // ========== TOMATO DISEASES ==========
        if (lower.contains("tomato") || lower.contains("solanum") || lower.contains("lycopersicon")) {
            if (lower.contains("early") && lower.contains("blight")) {
                return "Tomato_Early_Blight";
            }
            if (lower.contains("late") && lower.contains("blight")) {
                return "Tomato_Late_Blight";
            }
            if (lower.contains("leaf") && lower.contains("mold")) {
                return "Tomato_Leaf_Mold";
            }
            if (lower.contains("septoria") && lower.contains("leaf")) {
                return "Tomato_Septoria_Leaf_Spot";
            }
            if (lower.contains("spider") || lower.contains("mite")) {
                return "Tomato_Spider_Mites";
            }
            if (lower.contains("target") && lower.contains("spot")) {
                return "Tomato_Target_Spot";
            }
            if (lower.contains("yellow") || lower.contains("curl")) {
                return "Tomato_Yellow_Leaf_Curl";
            }
            if (lower.contains("mosaic") || lower.contains("virus")) {
                return "Tomato_Mosaic_Virus";
            }
            if (lower.contains("healthy") || lower.contains("normal")) {
                return "Tomato_Healthy";
            }
            return "Tomato_Disease";
        }

        // ========== POTATO DISEASES ==========
        if (lower.contains("potato") || lower.contains("solanum tuberosum")) {
            if (lower.contains("early") && lower.contains("blight")) {
                return "Potato_Early_Blight";
            }
            if (lower.contains("late") && lower.contains("blight")) {
                return "Potato_Late_Blight";
            }
            if (lower.contains("healthy")) {
                return "Potato_Healthy";
            }
            return "Potato_Disease";
        }

        // ========== PEPPER DISEASES ==========
        if (lower.contains("pepper") || lower.contains("capsicum") || lower.contains("bell")) {
            if (lower.contains("bacterial") && lower.contains("spot")) {
                return "Pepper_Bacterial_Spot";
            }
            if (lower.contains("healthy")) {
                return "Pepper_Healthy";
            }
            return "Pepper_Disease";
        }

        // ========== GENERAL PLANT CONDITIONS ==========
        if (lower.contains("leaf") && (lower.contains("spot") || lower.contains("blight"))) {
            return "Leaf_Disease";
        }
        if (lower.contains("root") || lower.contains("rot")) {
            return "Root_Rot";
        }
        if (lower.contains("wilt")) {
            return "Wilt_Disease";
        }
        if (lower.contains("rust")) {
            return "Rust_Disease";
        }
        if (lower.contains("mildew")) {
            return "Mildew_Disease";
        }

        // ========== GENERAL OBJECTS ==========
        if (lower.contains("person") || lower.contains("human") || lower.contains("man") || lower.contains("woman")) {
            return "Person";
        }
        if (lower.contains("animal") || lower.contains("dog") || lower.contains("cat") || lower.contains("cow") || lower.contains("goat")) {
            return "Animal";
        }
        if (lower.contains("vehicle") || lower.contains("car") || lower.contains("truck") || lower.contains("tractor") || lower.contains("bike")) {
            return "Vehicle";
        }
        if (lower.contains("building") || lower.contains("house") || lower.contains("structure") || lower.contains("shed")) {
            return "Building";
        }
        if (lower.contains("tool") || lower.contains("hoe") || lower.contains("machete") || lower.contains("cutlass")) {
            return "Tool";
        }
        if (lower.contains("machine") || lower.contains("engine") || lower.contains("pump")) {
            return "Machine";
        }
        if (lower.contains("container") || lower.contains("bucket") || lower.contains("barrel") || lower.contains("drum")) {
            return "Container";
        }
        if (lower.contains("fruit") || lower.contains("apple") || lower.contains("orange") || lower.contains("mango")) {
            return "Fruit";
        }
        if (lower.contains("vegetable") || lower.contains("cabbage") || lower.contains("onion") || lower.contains("carrot")) {
            return "Vegetable";
        }
        if (lower.contains("pest") || lower.contains("insect") || lower.contains("bug") || lower.contains("beetle") || lower.contains("caterpillar")) {
            return "Pest_Insect";
        }
        if (lower.contains("bird")) {
            return "Bird";
        }
        if (lower.contains("rodent") || lower.contains("rat") || lower.contains("mouse")) {
            return "Rodent";
        }
        if (lower.contains("weed") || lower.contains("grass") || lower.contains("unwanted")) {
            return "Weed";
        }
        if (lower.contains("soil") || lower.contains("dirt") || lower.contains("ground")) {
            return "Soil";
        }
        if (lower.contains("water") || lower.contains("pond") || lower.contains("irrigation")) {
            return "Water";
        }
        if (lower.contains("flower")) {
            return "Flower";
        }

        return className;
    }

    private String normalizeCropType(String cropType) {
        if (cropType == null || cropType.isBlank() || "unknown".equalsIgnoreCase(cropType)) {
            return DEFAULT_CROP_FOCUS;
        }
        return cropType.trim();
    }

    private boolean isCassavaFocus(String cropType) {
        return normalizeCropType(cropType).toLowerCase().contains("cassava");
    }

    private double getFocusScore(String className, double probability, String cropType) {
        double score = probability;
        if (isCassavaFocus(cropType) && className.startsWith("Cassava_")) {
            score += 0.5;
        }
        if (className.contains("Leaf") || className.contains("Healthy")) {
            score += 0.1;
        }
        return score;
    }

    private boolean isCropDisease(String className) {
        return className.contains("Cassava") ||
                className.contains("Tomato") ||
                className.contains("Potato") ||
                className.contains("Pepper") ||
                className.contains("Leaf_Disease") ||
                className.contains("Root_Rot") ||
                className.contains("Wilt_Disease") ||
                className.contains("Rust_Disease") ||
                className.contains("Mildew_Disease");
    }

    private boolean isEarlyLifeStage(String className) {
        String lower = className.toLowerCase();
        return EARLY_LIFE_INDICATORS.stream().anyMatch(lower::contains);
    }

    private DiseaseSeverity getDiseaseSeverity(String className, double probability) {
        String lower = className.toLowerCase();

        if (lower.contains("healthy")) {
            return DiseaseSeverity.NONE;
        }

        // Critical diseases
        if (lower.contains("mosaic") ||
                lower.contains("brown streak") ||
                (lower.contains("blight") && lower.contains("late"))) {
            if (probability > 0.5) return DiseaseSeverity.CRITICAL;
            if (probability > 0.3) return DiseaseSeverity.HIGH;
        }

        // High severity
        if (lower.contains("blight") ||
                lower.contains("bacterial") ||
                lower.contains("mold") ||
                lower.contains("virus")) {
            if (probability > 0.6) return DiseaseSeverity.HIGH;
            if (probability > 0.4) return DiseaseSeverity.MODERATE;
        }

        // Moderate severity
        if (lower.contains("spot") ||
                lower.contains("mite") ||
                lower.contains("curl")) {
            if (probability > 0.6) return DiseaseSeverity.MODERATE;
            if (probability > 0.4) return DiseaseSeverity.LOW;
        }

        // General severity based on confidence
        if (probability > 0.7) return DiseaseSeverity.MODERATE;
        if (probability > 0.4) return DiseaseSeverity.LOW;
        return DiseaseSeverity.LOW;
    }

    // ================================================================
    // COMPREHENSIVE JSON BUILDING
    // ================================================================

    private String buildComprehensiveDetectionJson(
            List<DetectionResult> allResults,
            List<DetectionResult> diseaseResults,
            List<DetectionResult> generalResults,
            String deviceId, String location, String cropType) {

        try {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("type", "comprehensive_detection");
            response.put("deviceId", deviceId != null ? deviceId : "unknown");
            response.put("location", location != null ? location : "unknown");
            response.put("cropType", cropType != null ? cropType : "unknown");
            response.put("count", allResults.size());
            response.put("totalCount", allResults.size());
            response.put("diseaseCount", diseaseResults.size());
            response.put("generalCount", generalResults.size());
            response.put("timestamp", System.currentTimeMillis());
            response.put("detectionTime", lastDetectionTime);
            response.put("modelLoaded", modelLoaded);

            // Summary
            if (!allResults.isEmpty()) {
                DetectionResult top = allResults.get(0);
                response.put("topDetection", top.className);
                response.put("topConfidence", Math.round(top.probability * 100));
                response.put("isEarlyLife", isEarlyLifeStage(top.className));

                DiseaseSeverity severity = getDiseaseSeverity(top.className, top.probability);
                response.put("severity", severity);
                response.put("severityDescription", severity.description);
                response.put("requiresAction",
                        severity == DiseaseSeverity.CRITICAL || severity == DiseaseSeverity.HIGH);

                // Health status
                if (!diseaseResults.isEmpty()) {
                    boolean hasHealthy = diseaseResults.stream()
                            .anyMatch(r -> r.className.toLowerCase().contains("healthy"));
                    response.put("healthStatus", hasHealthy ? "MIXED" : "DISEASED");
                } else {
                    response.put("healthStatus", "NO_DISEASE");
                }
            } else {
                response.put("healthStatus", "NO_DETECTION");
            }

            // Disease results
            List<Map<String, Object>> diseaseList = new ArrayList<>();
            for (DetectionResult result : diseaseResults) {
                Map<String, Object> item = buildResultItem(result);
                diseaseList.add(item);
            }
            response.put("diseases", diseaseList);

            // General objects
            List<Map<String, Object>> generalList = new ArrayList<>();
            for (DetectionResult result : generalResults) {
                Map<String, Object> item = buildResultItem(result);
                generalList.add(item);
            }
            response.put("objects", generalList);
            response.put("results", allResults.stream()
                    .map(this::buildResultItem)
                    .collect(Collectors.toList()));

            // Recommendations
            if (!diseaseResults.isEmpty()) {
                DetectionResult topDisease = diseaseResults.get(0);
                response.put("recommendations", getRecommendations(topDisease.className));
            } else if (!generalResults.isEmpty()) {
                response.put("recommendations", Arrays.asList(
                        "Continue regular monitoring",
                        "Maintain good agricultural practices",
                        "Watch for any changes in crop health"
                ));
            }

            return objectMapper.writeValueAsString(response);

        } catch (Exception e) {
            logger.error("Error building comprehensive detection JSON: {}", e.getMessage());
            return String.format(
                    "{\"type\":\"detection\",\"deviceId\":\"%s\",\"count\":0,\"results\":[],\"error\":\"%s\",\"timestamp\":%d}",
                    deviceId != null ? deviceId : "unknown",
                    escapeJson(e.getMessage()),
                    System.currentTimeMillis()
            );
        }
    }

    private Map<String, Object> buildResultItem(DetectionResult result) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("className", result.className);
        item.put("probability", result.probability);
        item.put("confidence", Math.round(result.probability * 100));
        item.put("isCropDisease", isCropDisease(result.className));
        item.put("isEarlyLife", isEarlyLifeStage(result.className));

        DiseaseSeverity severity = getDiseaseSeverity(result.className, result.probability);
        item.put("severity", severity);
        item.put("severityDescription", severity.description);
        item.put("isHealthy", result.className.toLowerCase().contains("healthy"));
        item.put("treatment", result.treatment);
        item.put("prevention", result.prevention);
        item.put("whenToAct", result.whenToAct);

        // Bounding box
        if (result.width > 0 && result.height > 0) {
            Map<String, Double> bbox = new LinkedHashMap<>();
            bbox.put("x", result.x);
            bbox.put("y", result.y);
            bbox.put("width", result.width);
            bbox.put("height", result.height);
            item.put("bbox", bbox);
        }

        return item;
    }

    private List<String> getRecommendations(String disease) {
        List<String> recommendations = new ArrayList<>();

        if (disease.contains("Cassava")) {
            recommendations.addAll(getCassavaRecommendations(disease));
        } else if (disease.contains("Tomato")) {
            recommendations.addAll(getTomatoRecommendations(disease));
        } else if (disease.contains("Potato")) {
            recommendations.addAll(getPotatoRecommendations(disease));
        } else if (disease.contains("Pepper")) {
            recommendations.addAll(getPepperRecommendations(disease));
        } else {
            recommendations.add("Monitor the plant closely");
            recommendations.add("Consult with agricultural expert");
            recommendations.add("Apply appropriate treatment if needed");
        }

        recommendations.add("📌 Document findings for future reference");
        return recommendations;
    }

    private String resolveTreatmentSuggestion(String disease, String aiTreatment) {
        if (aiTreatment != null) {
            return aiTreatment;
        }
        List<String> recommendations = getRecommendations(disease);
        for (String recommendation : recommendations) {
            if (!recommendation.toLowerCase().contains("document findings")) {
                return recommendation;
            }
        }
        return "Consult an agronomist for treatment guidance.";
    }

    private List<String> resolvePreventionTips(String disease, List<String> aiPrevention) {
        if (aiPrevention != null && !aiPrevention.isEmpty()) {
            return aiPrevention;
        }
        List<String> fallback = new ArrayList<>(getRecommendations(disease));
        fallback.removeIf(rec -> rec.toLowerCase().contains("document findings"));
        return fallback.stream().limit(3).collect(Collectors.toList());
    }

    private String resolveActionWindow(String disease, double probability, String aiWhenToAct) {
        if (aiWhenToAct != null) {
            return aiWhenToAct;
        }
        DiseaseSeverity severity = getDiseaseSeverity(disease, probability);
        if (severity == DiseaseSeverity.CRITICAL) {
            return "Take action within 24 hours.";
        }
        if (severity == DiseaseSeverity.HIGH) {
            return "Take action within 1-2 days.";
        }
        if (severity == DiseaseSeverity.MODERATE) {
            return "Take action within 3-5 days.";
        }
        if (severity == DiseaseSeverity.NONE) {
            return "No immediate treatment required. Continue monitoring.";
        }
        return "Monitor closely and act if symptoms worsen.";
    }

    private List<String> getCassavaRecommendations(String disease) {
        List<String> recs = new ArrayList<>();
        if (disease.contains("Mosaic")) {
            recs.add("Remove infected plants immediately");
            recs.add("Control whitefly population");
            recs.add("Use disease-free cuttings for planting");
        } else if (disease.contains("Brown_Streak")) {
            recs.add("Use resistant varieties");
            recs.add("Apply copper-based fungicides");
            recs.add("Remove infected plants");
        } else if (disease.contains("Green_Mite")) {
            recs.add("Scout the underside of cassava leaves for mites");
            recs.add("Use mite control approved for cassava farms");
            recs.add("Remove heavily infested leaves where practical");
        } else if (disease.contains("Blight")) {
            recs.add("Apply copper bactericides");
            recs.add("Improve drainage");
            recs.add("Use disease-free planting material");
        } else if (disease.contains("Healthy")) {
            recs.add("Cassava leaf looks healthy");
            recs.add("Keep monitoring for mosaic, blight, and mite damage");
            recs.add("Maintain clean tools and disease-free cuttings");
        } else {
            recs.add("Monitor cassava plants regularly");
            recs.add("Maintain good field hygiene");
        }
        return recs;
    }

    private List<String> getTomatoRecommendations(String disease) {
        List<String> recs = new ArrayList<>();
        if (disease.contains("Early_Blight") || disease.contains("Late_Blight")) {
            recs.add("Apply fungicides immediately");
            recs.add("Remove infected leaves");
            recs.add("Improve air circulation");
        } else if (disease.contains("Leaf_Mold")) {
            recs.add("Reduce humidity");
            recs.add("Space plants properly");
            recs.add("Apply fungicides");
        } else if (disease.contains("Yellow_Leaf_Curl")) {
            recs.add("Control whiteflies");
            recs.add("Remove infected plants");
            recs.add("Use virus-resistant varieties");
        } else {
            recs.add("Monitor tomato plants regularly");
            recs.add("Maintain proper spacing");
        }
        return recs;
    }

    private List<String> getPotatoRecommendations(String disease) {
        List<String> recs = new ArrayList<>();
        if (disease.contains("Blight")) {
            recs.add("Apply fungicides immediately");
            recs.add("Remove infected foliage");
            recs.add("Practice crop rotation");
        } else {
            recs.add("Monitor potato plants regularly");
            recs.add("Use disease-free seed potatoes");
        }
        return recs;
    }

    private List<String> getPepperRecommendations(String disease) {
        List<String> recs = new ArrayList<>();
        if (disease.contains("Bacterial_Spot")) {
            recs.add("Apply copper-based bactericides");
            recs.add("Avoid overhead irrigation");
            recs.add("Remove infected plants");
        } else {
            recs.add("Monitor pepper plants regularly");
            recs.add("Maintain good garden hygiene");
        }
        return recs;
    }

    // ================================================================
    // MOCK RESULTS
    // ================================================================

    private List<DetectionResult> generateComprehensiveMockResults(String cropType) {
        List<DetectionResult> mockResults = new ArrayList<>();
        Random rand = new Random();

        List<String> allLabels;
        if (isCassavaFocus(cropType)) {
            allLabels = Arrays.asList(
                    "Cassava_Healthy",
                    "Cassava_Bacterial_Blight",
                    "Cassava_Brown_Streak",
                    "Cassava_Mosaic_Disease",
                    "Cassava_Green_Mite"
            );
        } else {
            allLabels = new ArrayList<>();
            allLabels.addAll(CROP_DISEASE_LABELS);
            allLabels.addAll(GENERAL_OBJECTS);
        }

        int count = rand.nextInt(4) + 1; // 1-4 detections
        for (int i = 0; i < count; i++) {
            String label = allLabels.get(rand.nextInt(allLabels.size()));
            double confidence = 0.35 + rand.nextDouble() * 0.55;
            double[] bbox = generateSyntheticBbox();

            mockResults.add(new DetectionResult(
                    label,
                    confidence,
                    bbox[0], bbox[1], bbox[2], bbox[3]
            ));
        }

        return mockResults;
    }

    // ================================================================
    // MODEL LOADING METHODS
    // ================================================================

    private void loadFromModelZoo() throws Exception {
        logger.info("🔄 Attempting to load ResNet-18 from DJL model zoo...");
        Criteria<Image, Classifications> criteria = Criteria.builder()
                .setTypes(Image.class, Classifications.class)
                .optApplication(Application.CV.IMAGE_CLASSIFICATION)
                .optArtifactId("resnet")
                .optFilter("layers", "18")
                .optFilter("dataset", "imagenet")
                .optEngine("PyTorch")
                .build();

        model = criteria.loadModel();
        predictor = model.newPredictor();
        modelLoaded = true;
        logger.info("✅ ResNet-18 loaded successfully from DJL model zoo!");
    }

    private void loadFromUrl() throws Exception {
        logger.info("🔄 Attempting to load model from DJL CDN...");
        Criteria<Image, Classifications> criteria = Criteria.builder()
                .setTypes(Image.class, Classifications.class)
                .optApplication(Application.CV.IMAGE_CLASSIFICATION)
                .optModelUrls("https://djl-ai.s3.amazonaws.com/mlrepo/model/cv/image_classification/ai/djl/pytorch/resnet/0.0.1/traced_resnet18.pt.gz")
                .optEngine("PyTorch")
                .build();

        model = criteria.loadModel();
        predictor = model.newPredictor();
        modelLoaded = true;
        logger.info("✅ Model loaded successfully from DJL CDN!");
    }

    private void loadLocalModel() throws Exception {
        logger.info("🔄 Attempting to load local model file...");
        java.nio.file.Path modelPath = Paths.get("models/resnet18");
        if (!java.nio.file.Files.exists(modelPath)) {
            java.nio.file.Files.createDirectories(modelPath);
            logger.warn("⚠️ Models directory created. Please place model files in: {}", modelPath.toAbsolutePath());
            throw new Exception("Local model not found");
        }

        Criteria<Image, Classifications> criteria = Criteria.builder()
                .setTypes(Image.class, Classifications.class)
                .optApplication(Application.CV.IMAGE_CLASSIFICATION)
                .optModelPath(modelPath)
                .optEngine("PyTorch")
                .build();

        model = criteria.loadModel();
        predictor = model.newPredictor();
        modelLoaded = true;
        logger.info("✅ Local model loaded successfully!");
    }

    // ================================================================
    // UTILITY METHODS
    // ================================================================

    private double[] generateSyntheticBbox() {
        Random rand = new Random();
        double x = rand.nextDouble() * 0.5 + 0.1;
        double y = rand.nextDouble() * 0.5 + 0.1;
        double width = 0.15 + rand.nextDouble() * 0.35;
        double height = 0.15 + rand.nextDouble() * 0.35;
        return new double[]{x, y, width, height};
    }

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
                    Thread.sleep(3600000);
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
    // STATISTICS
    // ================================================================

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("modelLoaded", modelLoaded);
        stats.put("aiMode", lastAiModeUsed);
        stats.put("aiStack", getAiStackInfo());
        stats.put("totalDetections", totalDetections);
        stats.put("successfulDetections", successfulDetections);
        stats.put("failedDetections", failedDetections);
        stats.put("averageDetectionTime", totalDetections > 0 ?
                detectionTimeSum / totalDetections : 0);
        stats.put("lastDetectionTime", lastDetectionTime);
        stats.put("cachedDevices", detectionCache.size());
        stats.put("activeDevices", lastDetection.size());
        stats.put("detectionCapabilities", Arrays.asList(
                "Cassava Diseases",
                "Tomato Diseases",
                "Potato Diseases",
                "Pepper Diseases",
                "General Objects",
                "Early Life Stage Detection"
        ));
        return stats;
    }

    public String getDefaultCropFocus() {
        return DEFAULT_CROP_FOCUS;
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

        if (predictor != null) {
            predictor.close();
        }
        if (model != null) {
            model.close();
        }

        logger.info("✅ Model resources released.");
        logger.info("   Total detections processed: {}", totalDetections);
        logger.info("   Successful: {}, Failed: {}", successfulDetections, failedDetections);
    }

    // ================================================================
    // INNER CLASS
    // ================================================================

    public static class DetectionResult {
        public String className;
        public double probability;
        public double x, y, width, height;
        public String treatment;
        public List<String> prevention;
        public String whenToAct;
        public long timestamp;

        public DetectionResult(String className, double probability, double x, double y, double width, double height) {
            this(className, probability, x, y, width, height, null, null, null);
        }

        public DetectionResult(
                String className,
                double probability,
                double x,
                double y,
                double width,
                double height,
                String treatment,
                List<String> prevention,
                String whenToAct
        ) {
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
            return String.format("%s: %.1f%%", className, probability * 100);
        }
    }
}