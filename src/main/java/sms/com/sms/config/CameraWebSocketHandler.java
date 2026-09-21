package sms.com.sms.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import sms.com.sms.ObjectDetectionService;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class CameraWebSocketHandler extends AbstractWebSocketHandler {

    private static final Logger log =
            LoggerFactory.getLogger(CameraWebSocketHandler.class);

    /*
     * ============================================================
     * CONFIGURATION
     * ============================================================
     */

    private static final int MAX_SESSIONS = 100;

    private static final int SEND_TIME_LIMIT_MS = 10_000;

    private static final int SEND_BUFFER_SIZE_LIMIT =
            512 * 1024;

    /*
     * AI runs once every 5 seconds.
     */
    private static final int DETECTION_INTERVAL_SECONDS = 5;

    /*
     * Camera/dashboard must send heartbeat/ping
     * before this timeout.
     */
    private static final long HEARTBEAT_TIMEOUT_MS =
            30_000L;

    /*
     * ============================================================
     * DEPENDENCIES
     * ============================================================
     */

    private final ObjectMapper objectMapper;

    private final ObjectDetectionService objectDetectionService;

    /*
     * ============================================================
     * CAMERA SESSIONS
     *
     * deviceId -> WebSocketSession
     * ============================================================
     */

    private final ConcurrentHashMap<
            String,
            WebSocketSession
            > cameraSessions =
            new ConcurrentHashMap<>();

    /*
     * ============================================================
     * DASHBOARD SESSIONS
     *
     * sessionId -> WebSocketSession
     * ============================================================
     */

    private final ConcurrentHashMap<
            String,
            WebSocketSession
            > dashboardSessions =
            new ConcurrentHashMap<>();

    /*
     * ============================================================
     * SESSION -> DEVICE
     *
     * sessionId -> deviceId
     * ============================================================
     */

    private final ConcurrentHashMap<
            String,
            String
            > sessionDeviceIds =
            new ConcurrentHashMap<>();

    /*
     * ============================================================
     * HEARTBEATS
     *
     * sessionId -> last heartbeat timestamp
     * ============================================================
     */

    private final ConcurrentHashMap<
            String,
            Long
            > lastHeartbeat =
            new ConcurrentHashMap<>();

    /*
     * ============================================================
     * LATEST FRAME
     *
     * deviceId -> newest JPEG frame
     *
     * IMPORTANT:
     * We intentionally keep ONLY ONE frame per camera.
     * ============================================================
     */

    private final ConcurrentHashMap<
            String,
            byte[]
            > latestFrames =
            new ConcurrentHashMap<>();

    /*
     * ============================================================
     * CAMERA METADATA
     * ============================================================
     */

    private final ConcurrentHashMap<
            String,
            String
            > deviceLocations =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<
            String,
            String
            > deviceFocus =
            new ConcurrentHashMap<>();

    /*
     * ============================================================
     * AI RUNNING FLAGS
     *
     * Prevents multiple AI jobs for the same camera.
     * ============================================================
     */

    private final ConcurrentHashMap<
            String,
            AtomicBoolean
            > detectionRunning =
            new ConcurrentHashMap<>();

    /*
     * ============================================================
     * FRAME COUNTERS
     * ============================================================
     */

    private final ConcurrentHashMap<
            String,
            AtomicLong
            > frameCounters =
            new ConcurrentHashMap<>();

    /*
     * ============================================================
     * SCHEDULER
     * ============================================================
     */

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(
                    2,
                    r -> {

                        Thread thread =
                                new Thread(r);

                        thread.setName(
                                "smarteye-scheduler"
                        );

                        thread.setDaemon(true);

                        return thread;
                    }
            );

    /*
     * ============================================================
     * AI EXECUTOR
     * ============================================================
     */

    private final ExecutorService detectionExecutor =
            Executors.newFixedThreadPool(
                    2,
                    r -> {

                        Thread thread =
                                new Thread(r);

                        thread.setName(
                                "smarteye-ai"
                        );

                        thread.setDaemon(true);

                        return thread;
                    }
            );

    /*
     * ============================================================
     * CONSTRUCTOR
     * ============================================================
     */

    public CameraWebSocketHandler(
            ObjectMapper objectMapper,
            @Lazy ObjectDetectionService objectDetectionService
    ) {

        this.objectMapper =
                objectMapper;

        this.objectDetectionService =
                objectDetectionService;
    }

    /*
     * ============================================================
     * START SCHEDULERS
     * ============================================================
     */

    @PostConstruct
    public void start() {

        scheduler.scheduleAtFixedRate(
                this::runDetectionCycle,
                DETECTION_INTERVAL_SECONDS,
                DETECTION_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );

        scheduler.scheduleAtFixedRate(
                this::cleanupDeadSessions,
                10,
                10,
                TimeUnit.SECONDS
        );

        log.info(
                "=========================================="
        );

        log.info(
                "SmartEye WebSocket Handler Started"
        );

        log.info(
                "AI detection interval: {} seconds",
                DETECTION_INTERVAL_SECONDS
        );

        log.info(
                "Heartbeat timeout: {} ms",
                HEARTBEAT_TIMEOUT_MS
        );

        log.info(
                "Maximum sessions: {}",
                MAX_SESSIONS
        );

        log.info(
                "=========================================="
        );
    }

    /*
     * ============================================================
     * CONNECTION ESTABLISHED
     * ============================================================
     */

    @Override
    public void afterConnectionEstablished(
            WebSocketSession session
    ) {

        if (totalSessions() >= MAX_SESSIONS) {

            log.warn(
                    "Maximum WebSocket sessions reached. Rejecting {}",
                    session.getId()
            );

            try {

                session.close(
                        new CloseStatus(
                                CloseStatus.SERVICE_OVERLOAD.getCode(),
                                "Maximum sessions reached"
                        )
                );

            } catch (IOException ignored) {
            }

            return;
        }

        WebSocketSession safeSession =
                new ConcurrentWebSocketSessionDecorator(
                        session,
                        SEND_TIME_LIMIT_MS,
                        SEND_BUFFER_SIZE_LIMIT
                );

        String sessionId =
                session.getId();

        sessionDeviceIds.put(
                sessionId,
                sessionId
        );

        lastHeartbeat.put(
                sessionId,
                System.currentTimeMillis()
        );

        log.info(
                "WebSocket CONNECTED: {}",
                sessionId
        );

        sendJson(
                safeSession,
                createMessage(
                        "connected",
                        "SmartEye WebSocket connected"
                )
        );
    }

    /*
     * ============================================================
     * TEXT MESSAGE
     * ============================================================
     */

    @Override
    protected void handleTextMessage(
            WebSocketSession session,
            TextMessage message
    ) {

        String payload =
                message.getPayload();

        if (payload == null ||
                payload.isBlank()) {

            return;
        }

        lastHeartbeat.put(
                session.getId(),
                System.currentTimeMillis()
        );

        try {

            JsonNode json =
                    objectMapper.readTree(payload);

            if (json == null ||
                    !json.has("type")) {

                return;
            }

            String type =
                    json.path("type")
                            .asText("")
                            .trim()
                            .toLowerCase();

            switch (type) {

                case "register":

                    handleRegister(
                            session,
                            json
                    );

                    break;

                case "heartbeat":

                case "ping":

                    handleHeartbeat(
                            session
                    );

                    break;

                case "start_stream":

                    handleStartStream(
                            session,
                            json
                    );

                    break;

                case "stop_stream":

                    handleStopStream(
                            session,
                            json
                    );

                    break;

                case "capture":

                    handleCapture(
                            session,
                            json
                    );

                    break;

                case "status":

                    sendStatus(
                            session
                    );

                    break;

                case "get_sensor":

                    handleGetSensor(
                            session,
                            json
                    );

                    break;

                case "sensor_data":

                    handleSensorData(
                            session,
                            json
                    );

                    break;

                case "flash_on":

                case "flash_off":

                case "set_resolution":

                case "reboot":

                    forwardCameraCommand(
                            session,
                            json
                    );

                    break;

                default:

                    log.debug(
                            "Unknown WebSocket message type: {}",
                            type
                    );

                    sendError(
                            session,
                            "Unknown message type: " + type
                    );
            }

        } catch (Exception e) {

            log.error(
                    "Failed to process WebSocket message",
                    e
            );

            sendError(
                    session,
                    "Invalid WebSocket message"
            );
        }
    }

    /*
     * ============================================================
     * BINARY MESSAGE
     *
     * Camera sends JPEG frames here.
     * ============================================================
     */

    @Override
    protected void handleBinaryMessage(
            WebSocketSession session,
            BinaryMessage message
    ) {

        if (!session.isOpen()) {
            return;
        }

        byte[] frame =
                extractBytes(message);

        if (frame.length == 0) {
            return;
        }

        String deviceId =
                getDeviceId(session);

        /*
         * Only registered cameras may send frames.
         */

        if (!cameraSessions.containsKey(deviceId)) {

            log.warn(
                    "Binary frame received from unknown camera. session={}",
                    session.getId()
            );

            return;
        }

        /*
         * Count frame.
         */

        frameCounters
                .computeIfAbsent(
                        deviceId,
                        key -> new AtomicLong()
                )
                .incrementAndGet();

        /*
         * ========================================================
         * IMPORTANT
         *
         * Replace the old frame.
         *
         * DO NOT queue frames.
         * ========================================================
         */

        latestFrames.put(
                deviceId,
                frame
        );

        /*
         * Immediately send the frame to dashboards.
         */

        broadcastFrame(
                frame
        );
    }

    /*
     * ============================================================
     * CAMERA REGISTRATION
     * ============================================================
     */

    private void handleRegister(
            WebSocketSession session,
            JsonNode json
    ) {

        String deviceId =
                firstNonBlank(
                        json.path("deviceId")
                                .asText(null),

                        json.path("device_id")
                                .asText(null),

                        session.getId()
                );

        String deviceType =
                json.path("deviceType")
                        .asText("")
                        .trim();

        String location =
                firstNonBlank(
                        json.path("location")
                                .asText(null),

                        "Unknown"
                );

        String focus =
                firstNonBlank(
                        json.path("focus")
                                .asText(null),

                        json.path("cropType")
                                .asText(null),

                        "General"
                );

        boolean isCamera =
                deviceType.equalsIgnoreCase(
                        "ESP32-CAM"
                )
                        ||
                        deviceType.equalsIgnoreCase(
                                "ESP32_CAM"
                        )
                        ||
                        deviceType
                                .toLowerCase()
                                .contains("camera");

        /*
         * Save session/device relationship.
         */

        sessionDeviceIds.put(
                session.getId(),
                deviceId
        );

        lastHeartbeat.put(
                session.getId(),
                System.currentTimeMillis()
        );

        /*
         * ========================================================
         * CAMERA
         * ========================================================
         */

        if (isCamera) {

            WebSocketSession oldSession =
                    cameraSessions.put(
                            deviceId,
                            new ConcurrentWebSocketSessionDecorator(
                                    session,
                                    SEND_TIME_LIMIT_MS,
                                    SEND_BUFFER_SIZE_LIMIT
                            )
                    );

            /*
             * If the same device reconnects,
             * close the previous connection.
             */

            if (oldSession != null &&
                    oldSession.isOpen() &&
                    !oldSession.getId()
                            .equals(session.getId())) {

                try {

                    oldSession.close(
                            new CloseStatus(
                                    CloseStatus.NORMAL.getCode(),
                                    "Replaced by new connection"
                            )
                    );

                } catch (IOException ignored) {
                }
            }

            deviceLocations.put(
                    deviceId,
                    location
            );

            deviceFocus.put(
                    deviceId,
                    focus
            );

            detectionRunning.computeIfAbsent(
                    deviceId,
                    key -> new AtomicBoolean(false)
            );

            frameCounters.computeIfAbsent(
                    deviceId,
                    key -> new AtomicLong()
            );

            log.info(
                    "=========================================="
            );

            log.info(
                    "CAMERA REGISTERED"
            );

            log.info(
                    "Device: {}",
                    deviceId
            );

            log.info(
                    "Type: {}",
                    deviceType
            );

            log.info(
                    "Location: {}",
                    location
            );

            log.info(
                    "Focus: {}",
                    focus
            );

            log.info(
                    "=========================================="
            );

            ObjectNode response =
                    objectMapper.createObjectNode();

            response.put(
                    "type",
                    "registration_success"
            );

            response.put(
                    "deviceId",
                    deviceId
            );

            response.put(
                    "role",
                    "camera"
            );

            response.put(
                    "location",
                    location
            );

            response.put(
                    "focus",
                    focus
            );

            response.put(
                    "message",
                    "Camera registered successfully"
            );

            response.put(
                    "timestamp",
                    Instant.now().toString()
            );

            sendJson(
                    session,
                    response
            );

            return;
        }

        /*
         * ========================================================
         * DASHBOARD
         * ========================================================
         */

        WebSocketSession safeDashboard =
                new ConcurrentWebSocketSessionDecorator(
                        session,
                        SEND_TIME_LIMIT_MS,
                        SEND_BUFFER_SIZE_LIMIT
                );

        dashboardSessions.put(
                session.getId(),
                safeDashboard
        );

        log.info(
                "Dashboard registered: {}",
                session.getId()
        );

        ObjectNode response =
                objectMapper.createObjectNode();

        response.put(
                "type",
                "registration_success"
        );

        response.put(
                "role",
                "dashboard"
        );

        response.put(
                "message",
                "Dashboard connected"
        );

        response.put(
                "timestamp",
                Instant.now().toString()
        );

        sendJson(
                safeDashboard,
                response
        );

        sendCurrentStatus(
                safeDashboard
        );
    }

    /*
     * ============================================================
     * HEARTBEAT
     * ============================================================
     */

    private void handleHeartbeat(
            WebSocketSession session
    ) {

        lastHeartbeat.put(
                session.getId(),
                System.currentTimeMillis()
        );

        ObjectNode response =
                objectMapper.createObjectNode();

        response.put(
                "type",
                "pong"
        );

        response.put(
                "timestamp",
                Instant.now().toString()
        );

        sendJson(
                session,
                response
        );
    }

    /*
     * ============================================================
     * START STREAM
     * ============================================================
     */

    private void handleStartStream(
            WebSocketSession session,
            JsonNode json
    ) {

        String requestedDevice =
                getRequestedDevice(
                        session,
                        json
                );

        if (requestedDevice == null ||
                requestedDevice.isBlank()) {

            sendError(
                    session,
                    "deviceId is required"
            );

            return;
        }

        WebSocketSession camera =
                cameraSessions.get(
                        requestedDevice
                );

        if (camera == null ||
                !camera.isOpen()) {

            sendError(
                    session,
                    "Camera is not connected: "
                            + requestedDevice
            );

            return;
        }

        ObjectNode command =
                objectMapper.createObjectNode();

        command.put(
                "type",
                "start_stream"
        );

        command.put(
                "device_id",
                requestedDevice
        );

        command.put(
                "deviceId",
                requestedDevice
        );

        sendJson(
                camera,
                command
        );

        sendJson(
                session,
                createMessage(
                        "stream_started",
                        "Stream start command sent"
                )
        );
    }

    /*
     * ============================================================
     * STOP STREAM
     * ============================================================
     */

    private void handleStopStream(
            WebSocketSession session,
            JsonNode json
    ) {

        String requestedDevice =
                getRequestedDevice(
                        session,
                        json
                );

        if (requestedDevice == null ||
                requestedDevice.isBlank()) {

            sendError(
                    session,
                    "deviceId is required"
            );

            return;
        }

        WebSocketSession camera =
                cameraSessions.get(
                        requestedDevice
                );

        if (camera == null ||
                !camera.isOpen()) {

            sendError(
                    session,
                    "Camera is not connected: "
                            + requestedDevice
            );

            return;
        }

        ObjectNode command =
                objectMapper.createObjectNode();

        command.put(
                "type",
                "stop_stream"
        );

        command.put(
                "device_id",
                requestedDevice
        );

        command.put(
                "deviceId",
                requestedDevice
        );

        sendJson(
                camera,
                command
        );

        sendJson(
                session,
                createMessage(
                        "stream_stopped",
                        "Stream stop command sent"
                )
        );
    }

    /*
     * ============================================================
     * CAPTURE
     * ============================================================
     */

    private void handleCapture(
            WebSocketSession session,
            JsonNode json
    ) {

        String requestedDevice =
                getRequestedDevice(
                        session,
                        json
                );

        if (requestedDevice == null ||
                requestedDevice.isBlank()) {

            sendError(
                    session,
                    "deviceId is required"
            );

            return;
        }

        WebSocketSession camera =
                cameraSessions.get(
                        requestedDevice
                );

        if (camera == null ||
                !camera.isOpen()) {

            sendError(
                    session,
                    "Camera is not connected: "
                            + requestedDevice
            );

            return;
        }

        ObjectNode command =
                objectMapper.createObjectNode();

        command.put(
                "type",
                "capture"
        );

        command.put(
                "device_id",
                requestedDevice
        );

        command.put(
                "deviceId",
                requestedDevice
        );

        sendJson(
                camera,
                command
        );

        sendJson(
                session,
                createMessage(
                        "capture_requested",
                        "Capture command sent"
                )
        );
    }

    /*
     * ============================================================
     * SENSOR DATA
     * ============================================================
     */

    private void handleSensorData(
            WebSocketSession session,
            JsonNode json
    ) {

        String deviceId =
                getDeviceId(session);

        ObjectNode sensor =
                objectMapper.createObjectNode();

        sensor.put(
                "type",
                "sensor_data"
        );

        sensor.put(
                "deviceId",
                deviceId
        );

        if (json.has("temperature")) {

            sensor.put(
                    "temperature",
                    json.path("temperature")
                            .asDouble()
            );
        }

        if (json.has("humidity")) {

            sensor.put(
                    "humidity",
                    json.path("humidity")
                            .asDouble()
            );
        }

        if (json.has("soil")) {

            sensor.put(
                    "soil",
                    json.path("soil")
                            .asDouble()
            );
        }

        if (json.has("soilMoisture")) {

            sensor.put(
                    "soilMoisture",
                    json.path("soilMoisture")
                            .asDouble()
            );
        }

        if (json.has("gas")) {

            sensor.put(
                    "gas",
                    json.path("gas")
                            .asDouble()
            );
        }

        if (json.has("co2")) {

            sensor.put(
                    "co2",
                    json.path("co2")
                            .asDouble()
            );
        }

        String focus =
                firstNonBlank(
                        json.path("focus")
                                .asText(null),

                        deviceFocus.getOrDefault(
                                deviceId,
                                "General"
                        )
                );

        sensor.put(
                "focus",
                focus
        );

        sensor.put(
                "location",
                deviceLocations.getOrDefault(
                        deviceId,
                        "Unknown"
                )
        );

        sensor.put(
                "timestamp",
                Instant.now().toString()
        );

        broadcastJson(
                sensor
        );
    }

    /*
     * ============================================================
     * GET SENSOR
     * ============================================================
     */

    private void handleGetSensor(
            WebSocketSession session,
            JsonNode json
    ) {

        String requestedDevice =
                getRequestedDevice(
                        session,
                        json
                );

        WebSocketSession camera =
                cameraSessions.get(
                        requestedDevice
                );

        if (camera == null ||
                !camera.isOpen()) {

            sendError(
                    session,
                    "Camera is not connected: "
                            + requestedDevice
            );

            return;
        }

        ObjectNode command =
                objectMapper.createObjectNode();

        command.put(
                "type",
                "get_sensor"
        );

        command.put(
                "device_id",
                requestedDevice
        );

        command.put(
                "deviceId",
                requestedDevice
        );

        sendJson(
                camera,
                command
        );
    }

    /*
     * ============================================================
     * FORWARD CAMERA COMMAND
     * ============================================================
     */

    private void forwardCameraCommand(
            WebSocketSession session,
            JsonNode json
    ) {

        String requestedDevice =
                getRequestedDevice(
                        session,
                        json
                );

        if (requestedDevice == null ||
                requestedDevice.isBlank()) {

            sendError(
                    session,
                    "deviceId is required"
            );

            return;
        }

        WebSocketSession camera =
                cameraSessions.get(
                        requestedDevice
                );

        if (camera == null ||
                !camera.isOpen()) {

            sendError(
                    session,
                    "Camera is not connected: "
                            + requestedDevice
            );

            return;
        }

        ObjectNode command =
                json.deepCopy();

        command.put(
                "device_id",
                requestedDevice
        );

        command.put(
                "deviceId",
                requestedDevice
        );

        sendJson(
                camera,
                command
        );
    }

    /*
     * ============================================================
     * BROADCAST FRAME
     *
     * Frame is immediately sent to all dashboards.
     * ============================================================
     */

    private void broadcastFrame(
            byte[] frame
    ) {

        if (dashboardSessions.isEmpty()) {
            return;
        }

        List<String> deadSessions =
                new ArrayList<>();

        for (Map.Entry<
                String,
                WebSocketSession
                > entry :
                dashboardSessions.entrySet()) {

            String sessionId =
                    entry.getKey();

            WebSocketSession dashboard =
                    entry.getValue();

            if (dashboard == null ||
                    !dashboard.isOpen()) {

                deadSessions.add(
                        sessionId
                );

                continue;
            }

            try {

                BinaryMessage message =
                        new BinaryMessage(
                                ByteBuffer.wrap(frame)
                        );

                dashboard.sendMessage(
                        message
                );

            } catch (Exception e) {

                log.debug(
                        "Failed to send frame to dashboard {}",
                        dashboard.getId()
                );

                deadSessions.add(
                        sessionId
                );
            }
        }

        for (String sessionId :
                deadSessions) {

            removeSession(
                    sessionId
            );
        }
    }

    /*
     * ============================================================
     * SEND DETECTION TO DASHBOARDS
     * ============================================================
     */

    public void sendDetectionToClients(
            String detectionJson
    ) {

        if (detectionJson == null ||
                detectionJson.isBlank()) {

            return;
        }

        TextMessage message =
                new TextMessage(
                        detectionJson
                );

        List<String> deadSessions =
                new ArrayList<>();

        for (Map.Entry<
                String,
                WebSocketSession
                > entry :
                dashboardSessions.entrySet()) {

            String sessionId =
                    entry.getKey();

            WebSocketSession dashboard =
                    entry.getValue();

            if (dashboard == null ||
                    !dashboard.isOpen()) {

                deadSessions.add(
                        sessionId
                );

                continue;
            }

            try {

                dashboard.sendMessage(
                        message
                );

            } catch (Exception e) {

                log.debug(
                        "Failed to send detection to dashboard {}",
                        dashboard.getId()
                );

                deadSessions.add(
                        sessionId
                );
            }
        }

        for (String sessionId :
                deadSessions) {

            removeSession(
                    sessionId
            );
        }
    }

    /*
     * ============================================================
     * BROADCAST JSON
     * ============================================================
     */

    private void broadcastJson(
            JsonNode json
    ) {

        String payload;

        try {

            payload =
                    objectMapper.writeValueAsString(
                            json
                    );

        } catch (Exception e) {

            log.error(
                    "Failed to serialize WebSocket JSON",
                    e
            );

            return;
        }

        TextMessage message =
                new TextMessage(
                        payload
                );

        List<String> deadSessions =
                new ArrayList<>();

        for (Map.Entry<
                String,
                WebSocketSession
                > entry :
                dashboardSessions.entrySet()) {

            String sessionId =
                    entry.getKey();

            WebSocketSession dashboard =
                    entry.getValue();

            if (dashboard == null ||
                    !dashboard.isOpen()) {

                deadSessions.add(
                        sessionId
                );

                continue;
            }

            try {

                dashboard.sendMessage(
                        message
                );

            } catch (Exception e) {

                deadSessions.add(
                        sessionId
                );
            }
        }

        for (String sessionId :
                deadSessions) {

            removeSession(
                    sessionId
            );
        }
    }

    /*
     * ============================================================
     * AI DETECTION CYCLE
     * ============================================================
     */

    private void runDetectionCycle() {

        if (latestFrames.isEmpty()) {
            return;
        }

        for (String deviceId :
                new ArrayList<>(
                        latestFrames.keySet()
                )) {

            byte[] frame =
                    latestFrames.get(
                            deviceId
                    );

            if (frame == null ||
                    frame.length == 0) {

                continue;
            }

            AtomicBoolean running =
                    detectionRunning.computeIfAbsent(
                            deviceId,
                            key ->
                                    new AtomicBoolean(false)
                    );

            /*
             * Do not start another AI job
             * while the previous job is running.
             */

            if (!running.compareAndSet(
                    false,
                    true
            )) {

                continue;
            }

            String location =
                    deviceLocations.getOrDefault(
                            deviceId,
                            "Unknown"
                    );

            String focus =
                    deviceFocus.getOrDefault(
                            deviceId,
                            "General"
                    );

            /*
             * Copy the current frame.
             */

            byte[] frameForAI =
                    frame.clone();

            detectionExecutor.submit(
                    () -> {

                        try {

                            log.debug(
                                    "AI detection started: device={}, bytes={}",
                                    deviceId,
                                    frameForAI.length
                            );

                            objectDetectionService
                                    .processAndSendDetection(
                                            frameForAI,
                                            deviceId,
                                            location,
                                            focus
                                    );

                        } catch (Exception e) {

                            log.error(
                                    "AI detection failed for device {}",
                                    deviceId,
                                    e
                            );

                        } finally {

                            running.set(false);
                        }
                    }
            );
        }
    }

    /*
     * ============================================================
     * CURRENT CAMERA STATUS
     * ============================================================
     */

    private void sendCurrentStatus(
            WebSocketSession session
    ) {

        ObjectNode status =
                objectMapper.createObjectNode();

        status.put(
                "type",
                "camera_status"
        );

        status.put(
                "timestamp",
                Instant.now().toString()
        );

        ArrayNode cameras =
                objectMapper.createArrayNode();

        for (String deviceId :
                cameraSessions.keySet()) {

            ObjectNode camera =
                    objectMapper.createObjectNode();

            camera.put(
                    "deviceId",
                    deviceId
            );

            camera.put(
                    "location",
                    deviceLocations.getOrDefault(
                            deviceId,
                            "Unknown"
                    )
            );

            camera.put(
                    "focus",
                    deviceFocus.getOrDefault(
                            deviceId,
                            "General"
                    )
            );

            camera.put(
                    "connected",
                    isCameraConnected(
                            deviceId
                    )
            );

            camera.put(
                    "frames",
                    frameCounters
                            .getOrDefault(
                                    deviceId,
                                    new AtomicLong(0)
                            )
                            .get()
            );

            camera.put(
                    "hasLatestFrame",
                    latestFrames.containsKey(
                            deviceId
                    )
            );

            cameras.add(
                    camera
            );
        }

        status.set(
                "cameras",
                cameras
        );

        sendJson(
                session,
                status
        );
    }

    /*
     * ============================================================
     * STATUS
     * ============================================================
     */

    private void sendStatus(
            WebSocketSession session
    ) {

        ObjectNode status =
                objectMapper.createObjectNode();

        status.put(
                "type",
                "status"
        );

        status.put(
                "cameraSessions",
                cameraSessions.size()
        );

        status.put(
                "dashboardSessions",
                dashboardSessions.size()
        );

        status.put(
                "latestFrames",
                latestFrames.size()
        );

        status.put(
                "detectionJobs",
                detectionExecutor
                        instanceof java.util.concurrent.ThreadPoolExecutor
                        ?
                        ((java.util.concurrent.ThreadPoolExecutor)
                         detectionExecutor)
                        .getActiveCount()
                        :
                        0
        );

        status.put(
                "timestamp",
                Instant.now().toString()
        );

        sendJson(
                session,
                status
        );
    }

    /*
     * ============================================================
     * CLEAN DEAD SESSIONS
     * ============================================================
     */

    private void cleanupDeadSessions() {

        long now =
                System.currentTimeMillis();

        for (Map.Entry<
                String,
                Long
                > entry :
                lastHeartbeat.entrySet()) {

            String sessionId =
                    entry.getKey();

            long last =
                    entry.getValue();

            if (now - last >
                    HEARTBEAT_TIMEOUT_MS) {

                WebSocketSession session =
                        findSession(
                                sessionId
                        );

                if (session != null &&
                        session.isOpen()) {

                    try {

                        session.close(
                                new CloseStatus(
                                        CloseStatus.GOING_AWAY.getCode(),
                                        "Heartbeat timeout"
                                )
                        );

                    } catch (IOException ignored) {
                    }
                }

                removeSession(
                        sessionId
                );
            }
        }
    }

    /*
     * ============================================================
     * FIND SESSION
     * ============================================================
     */

    private WebSocketSession findSession(
            String sessionId
    ) {

        WebSocketSession dashboard =
                dashboardSessions.get(
                        sessionId
                );

        if (dashboard != null) {

            return dashboard;
        }

        for (WebSocketSession camera :
                cameraSessions.values()) {

            if (camera != null &&
                    camera.getId()
                            .equals(sessionId)) {

                return camera;
            }
        }

        return null;
    }

    /*
     * ============================================================
     * CONNECTION CLOSED
     * ============================================================
     */

    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            CloseStatus status
    ) {

        String deviceId =
                sessionDeviceIds.get(
                        session.getId()
                );

        log.info(
                "WebSocket disconnected: session={}, device={}, status={}",
                session.getId(),
                deviceId,
                status
        );

        removeSession(
                session.getId()
        );
    }

    /*
     * ============================================================
     * TRANSPORT ERROR
     * ============================================================
     */

    @Override
    public void handleTransportError(
            WebSocketSession session,
            Throwable exception
    ) {

        log.debug(
                "WebSocket transport error: {}",
                session.getId(),
                exception
        );

        removeSession(
                session.getId()
        );
    }

    /*
     * ============================================================
     * REMOVE SESSION
     * ============================================================
     */

    private void removeSession(
            String sessionId
    ) {

        dashboardSessions.remove(
                sessionId
        );

        String deviceId =
                sessionDeviceIds.remove(
                        sessionId
                );

        lastHeartbeat.remove(
                sessionId
        );

        if (deviceId == null) {
            return;
        }

        WebSocketSession camera =
                cameraSessions.get(
                        deviceId
                );

        /*
         * Only remove the camera if the
         * disconnected session is actually
         * the current camera session.
         */

        if (camera != null &&
                camera.getId()
                        .equals(sessionId)) {

            cameraSessions.remove(
                    deviceId
            );

            latestFrames.remove(
                    deviceId
            );

            detectionRunning.remove(
                    deviceId
            );

            frameCounters.remove(
                    deviceId
            );

            deviceLocations.remove(
                    deviceId
            );

            deviceFocus.remove(
                    deviceId
            );

            log.info(
                    "Camera removed: {}",
                    deviceId
            );
        }
    }

    /*
     * ============================================================
     * CAMERA CONNECTED?
     * ============================================================
     */

    private boolean isCameraConnected(
            String deviceId
    ) {

        WebSocketSession session =
                cameraSessions.get(
                        deviceId
                );

        return session != null &&
                session.isOpen();
    }

    /*
     * ============================================================
     * TOTAL SESSIONS
     * ============================================================
     */

    private int totalSessions() {

        return cameraSessions.size()
                +
                dashboardSessions.size();
    }

    /*
     * ============================================================
     * GET DEVICE ID
     * ============================================================
     */

    private String getDeviceId(
            WebSocketSession session
    ) {

        return sessionDeviceIds.getOrDefault(
                session.getId(),
                session.getId()
        );
    }

    /*
     * ============================================================
     * REQUESTED DEVICE
     * ============================================================
     */

    private String getRequestedDevice(
            WebSocketSession session,
            JsonNode json
    ) {

        String deviceId =
                firstNonBlank(
                        json.path("deviceId")
                                .asText(null),

                        json.path("device_id")
                                .asText(null),

                        null
                );

        /*
         * If this is a camera itself,
         * its own device ID can be used.
         */

        if (deviceId == null ||
                deviceId.isBlank()) {

            String currentDevice =
                    getDeviceId(session);

            if (cameraSessions.containsKey(
                    currentDevice
            )) {

                return currentDevice;
            }
        }

        return deviceId;
    }

    /*
     * ============================================================
     * EXTRACT BINARY BYTES
     * ============================================================
     */

    private byte[] extractBytes(
            BinaryMessage message
    ) {

        ByteBuffer buffer =
                message.getPayload();

        byte[] bytes =
                new byte[
                        buffer.remaining()
                        ];

        buffer.get(
                bytes
        );

        return bytes;
    }

    /*
     * ============================================================
     * SEND JSON
     * ============================================================
     */

    private void sendJson(
            WebSocketSession session,
            JsonNode json
    ) {

        if (session == null ||
                !session.isOpen()) {

            return;
        }

        try {

            String payload =
                    objectMapper.writeValueAsString(
                            json
                    );

            session.sendMessage(
                    new TextMessage(
                            payload
                    )
            );

        } catch (Exception e) {

            log.debug(
                    "Unable to send WebSocket message to {}",
                    session.getId(),
                    e
            );
        }
    }

    /*
     * ============================================================
     * SEND ERROR
     * ============================================================
     */

    private void sendError(
            WebSocketSession session,
            String message
    ) {

        ObjectNode error =
                objectMapper.createObjectNode();

        error.put(
                "type",
                "error"
        );

        error.put(
                "message",
                message
        );

        error.put(
                "timestamp",
                Instant.now().toString()
        );

        sendJson(
                session,
                error
        );
    }

    /*
     * ============================================================
     * CREATE MESSAGE
     * ============================================================
     */

    private ObjectNode createMessage(
            String type,
            String message
    ) {

        ObjectNode json =
                objectMapper.createObjectNode();

        json.put(
                "type",
                type
        );

        json.put(
                "message",
                message
        );

        json.put(
                "timestamp",
                Instant.now().toString()
        );

        return json;
    }

    /*
     * ============================================================
     * FIRST NON-BLANK
     * ============================================================
     */

    private String firstNonBlank(
            String first,
            String second,
            String third
    ) {

        if (first != null &&
                !first.isBlank()) {

            return first;
        }

        if (second != null &&
                !second.isBlank()) {

            return second;
        }

        if (third != null &&
                !third.isBlank()) {

            return third;
        }

        return null;
    }

    private String firstNonBlank(
            String first,
            String second
    ) {

        return firstNonBlank(
                first,
                second,
                null
        );
    }

    /*
     * ============================================================
     * PARTIAL MESSAGES
     * ============================================================
     */

    @Override
    public boolean supportsPartialMessages() {

        return false;
    }

    /*
     * ============================================================
     * SHUTDOWN
     * ============================================================
     */

    @PreDestroy
    public void shutdown() {

        log.info(
                "Shutting down SmartEye WebSocket Handler"
        );

        scheduler.shutdownNow();

        detectionExecutor.shutdownNow();

        /*
         * Close camera connections.
         */

        for (WebSocketSession session :
                cameraSessions.values()) {

            try {

                if (session != null &&
                        session.isOpen()) {

                    session.close(
                            CloseStatus.NORMAL
                    );
                }

            } catch (Exception ignored) {
            }
        }

        /*
         * Close dashboard connections.
         */

        for (WebSocketSession session :
                dashboardSessions.values()) {

            try {

                if (session != null &&
                        session.isOpen()) {

                    session.close(
                            CloseStatus.NORMAL
                    );
                }

            } catch (Exception ignored) {
            }
        }

        cameraSessions.clear();

        dashboardSessions.clear();

        latestFrames.clear();

        sessionDeviceIds.clear();

        lastHeartbeat.clear();

        deviceLocations.clear();

        deviceFocus.clear();

        detectionRunning.clear();

        frameCounters.clear();

        log.info(
                "SmartEye WebSocket Handler stopped"
        );
    }
}