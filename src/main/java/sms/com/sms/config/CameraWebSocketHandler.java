package sms.com.sms.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class CameraWebSocketHandler extends AbstractWebSocketHandler {

    private static final Logger log =
            LoggerFactory.getLogger(CameraWebSocketHandler.class);

    private static final int MAX_SESSIONS = 100;

    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int SEND_BUFFER_SIZE_LIMIT = 512 * 1024;

    private static final int DETECTION_INTERVAL_SECONDS = 5;

    private static final long HEARTBEAT_TIMEOUT_MS =
            30_000L;

    private final ObjectMapper objectMapper;

    private final ObjectDetectionService objectDetectionService;

    private final ConcurrentHashMap<String, WebSocketSession> cameraSessions =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, WebSocketSession> dashboardSessions =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, String> sessionDeviceIds =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Long> lastHeartbeat =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, byte[]> latestFrames =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, String> deviceLocations =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, String> deviceFocus =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, AtomicBoolean> detectionRunning =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, AtomicLong> frameCounters =
            new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r);
                t.setName("smarteye-scheduler");
                t.setDaemon(true);
                return t;
            });

    private final ExecutorService detectionExecutor =
            Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r);
                t.setName("smarteye-ai");
                t.setDaemon(true);
                return t;
            });

    public CameraWebSocketHandler(
            ObjectMapper objectMapper,
            @Lazy ObjectDetectionService objectDetectionService
    ) {
        this.objectMapper = objectMapper;
        this.objectDetectionService = objectDetectionService;
    }

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

        log.info("SmartEye CameraWebSocketHandler started");
        log.info(
                "AI detection interval: {} seconds",
                DETECTION_INTERVAL_SECONDS
        );
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {

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

        sessionDeviceIds.put(
                safeSession.getId(),
                safeSession.getId()
        );

        lastHeartbeat.put(
                safeSession.getId(),
                System.currentTimeMillis()
        );

        log.info(
                "WebSocket connected: {}",
                safeSession.getId()
        );

        sendJson(
                safeSession,
                createMessage(
                        "connected",
                        "SmartEye WebSocket connected"
                )
        );
    }

    @Override
    protected void handleTextMessage(
            WebSocketSession session,
            TextMessage message
    ) {

        String payload = message.getPayload();

        if (payload == null || payload.isBlank()) {
            return;
        }

        lastHeartbeat.put(
                session.getId(),
                System.currentTimeMillis()
        );

        try {

            JsonNode json =
                    objectMapper.readTree(payload);

            if (json == null || !json.has("type")) {
                return;
            }

            String type =
                    json.path("type")
                            .asText("")
                            .trim()
                            .toLowerCase();

            switch (type) {

                case "register":
                    handleRegister(session, json);
                    break;

                case "heartbeat":
                case "ping":
                    handleHeartbeat(session);
                    break;

                case "start_stream":
                    handleStartStream(session, json);
                    break;

                case "stop_stream":
                    handleStopStream(session, json);
                    break;

                case "capture":
                    handleCapture(session, json);
                    break;

                case "status":
                    sendStatus(session);
                    break;

                case "sensor_data":
                    handleSensorData(session, json);
                    break;

                default:
                    log.debug(
                            "Unknown WebSocket message type: {}",
                            type
                    );
            }

        } catch (Exception e) {

            log.error(
                    "Failed to process WebSocket message: {}",
                    payload,
                    e
            );

            sendJson(
                    session,
                    createMessage(
                            "error",
                            "Invalid WebSocket message"
                    )
            );
        }
    }

    @Override
    protected void handleBinaryMessage(
            WebSocketSession session,
            BinaryMessage message
    ) {

        if (!session.isOpen()) {
            return;
        }

        byte[] frame = extractBytes(message);

        if (frame.length == 0) {
            return;
        }

        String deviceId =
                getDeviceId(session);

        if (!cameraSessions.containsKey(deviceId)) {

            log.warn(
                    "Binary frame received from unknown/non-camera session: {}",
                    session.getId()
            );

            return;
        }

        frameCounters
                .computeIfAbsent(
                        deviceId,
                        k -> new AtomicLong()
                )
                .incrementAndGet();

        /*
         * Keep ONLY the newest frame.
         *
         * We deliberately do not queue every frame.
         * This prevents latency from continuously increasing.
         */
        latestFrames.put(
                deviceId,
                frame
        );

        /*
         * Send the frame immediately to dashboards.
         *
         * AI processing is NOT performed here.
         */
        broadcastFrame(
                frame
        );
    }

    private void handleRegister(
            WebSocketSession session,
            JsonNode json
    ) {

        String deviceId =
                firstNonBlank(

                        json.path("device_id").asText(null),
                        session.getId()
                );

        String deviceType =
                json.path("deviceType")
                        .asText("")
                        .trim();

        String location =
                firstNonBlank(
                        json.path("location").asText(null),
                        "Unknown"
                );

        String focus =
                firstNonBlank(

                        json.path("cropType").asText(null),
                        "General"
                );

        boolean isCamera =
                deviceType.equalsIgnoreCase("ESP32-CAM")
                        ||
                        deviceType.equalsIgnoreCase("ESP32_CAM")
                        ||
                        deviceType.toLowerCase().contains("camera");

        sessionDeviceIds.put(
                session.getId(),
                deviceId
        );

        lastHeartbeat.put(
                session.getId(),
                System.currentTimeMillis()
        );

        if (isCamera) {

            cameraSessions.put(
                    deviceId,
                    session
            );

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
                    k -> new AtomicBoolean(false)
            );

            frameCounters.computeIfAbsent(
                    deviceId,
                    k -> new AtomicLong()
            );

            log.info(
                    "Camera registered: deviceId={}, location={}, focus={}",
                    deviceId,
                    location,
                    focus
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

            sendJson(
                    session,
                    response
            );

        } else {

            dashboardSessions.put(
                    session.getId(),
                    session
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

            sendJson(
                    session,
                    response
            );

            sendCurrentStatus(session);
        }
    }

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

    private void handleStartStream(
            WebSocketSession session,
            JsonNode json
    ) {

        String requestedDevice =
                firstNonBlank(

                        json.path("deviceId").asText(null),
                        getDeviceId(session)
                );

        WebSocketSession camera =
                cameraSessions.get(requestedDevice);

        if (camera == null || !camera.isOpen()) {

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

    private void handleStopStream(
            WebSocketSession session,
            JsonNode json
    ) {

        String requestedDevice =
                firstNonBlank(

                        json.path("deviceId").asText(null),
                        getDeviceId(session)
                );

        WebSocketSession camera =
                cameraSessions.get(requestedDevice);

        if (camera == null || !camera.isOpen()) {

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

    private void handleCapture(
            WebSocketSession session,
            JsonNode json
    ) {

        String requestedDevice =
                firstNonBlank(

                        json.path("deviceId").asText(null),
                        getDeviceId(session)
                );

        WebSocketSession camera =
                cameraSessions.get(requestedDevice);

        if (camera == null || !camera.isOpen()) {

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

        sendJson(
                camera,
                command
        );
    }

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
                    json.path("temperature").asDouble()
            );
        }

        if (json.has("humidity")) {
            sensor.put(
                    "humidity",
                    json.path("humidity").asDouble()
            );
        }

        if (json.has("soil")) {
            sensor.put(
                    "soil",
                    json.path("soil").asDouble()
            );
        }

        if (json.has("focus")) {
            sensor.put(
                    "focus",
                    json.path("focus").asText("General")
            );
        }

        broadcastJson(
                sensor
        );
    }

    private void broadcastFrame(
            byte[] frame
    ) {

        if (dashboardSessions.isEmpty()) {
            return;
        }

        List<String> deadSessions =
                new ArrayList<>();

        BinaryMessage message =
                new BinaryMessage(
                        ByteBuffer.wrap(frame)
                );

        for (Map.Entry<String, WebSocketSession> entry
                : dashboardSessions.entrySet()) {

            WebSocketSession dashboard =
                    entry.getValue();

            if (!dashboard.isOpen()) {
                deadSessions.add(
                        entry.getKey()
                );
                continue;
            }

            try {

                dashboard.sendMessage(
                        message
                );

            } catch (Exception e) {

                log.debug(
                        "Failed to send frame to dashboard {}",
                        dashboard.getId()
                );

                deadSessions.add(
                        entry.getKey()
                );
            }
        }

        for (String id : deadSessions) {
            removeSession(id);
        }
    }

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

        for (Map.Entry<String, WebSocketSession> entry
                : dashboardSessions.entrySet()) {

            WebSocketSession dashboard =
                    entry.getValue();

            if (!dashboard.isOpen()) {
                deadSessions.add(
                        entry.getKey()
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
                        entry.getKey()
                );
            }
        }

        for (String id : deadSessions) {
            removeSession(id);
        }
    }

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
            return;
        }

        TextMessage message =
                new TextMessage(payload);

        List<String> deadSessions =
                new ArrayList<>();

        for (Map.Entry<String, WebSocketSession> entry
                : dashboardSessions.entrySet()) {

            WebSocketSession dashboard =
                    entry.getValue();

            if (!dashboard.isOpen()) {
                deadSessions.add(
                        entry.getKey()
                );
                continue;
            }

            try {
                dashboard.sendMessage(
                        message
                );
            } catch (Exception e) {
                deadSessions.add(
                        entry.getKey()
                );
            }
        }

        for (String id : deadSessions) {
            removeSession(id);
        }
    }

    private void runDetectionCycle() {

        if (latestFrames.isEmpty()) {
            return;
        }

        for (String deviceId :
                new ArrayList<>(latestFrames.keySet())) {

            byte[] frame =
                    latestFrames.get(deviceId);

            if (frame == null ||
                    frame.length == 0) {
                continue;
            }

            AtomicBoolean running =
                    detectionRunning.computeIfAbsent(
                            deviceId,
                            k -> new AtomicBoolean(false)
                    );

            /*
             * If the previous AI job is still running,
             * skip this cycle.
             *
             * This is important because AI must never
             * create an endless queue of old frames.
             */
            if (!running.compareAndSet(false, true)) {
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

            byte[] frameForAI =
                    frame.clone();

            detectionExecutor.submit(() -> {

                try {

                    log.debug(
                            "Running AI detection: device={}, size={} bytes",
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
                            "AI detection failed for {}",
                            deviceId,
                            e
                    );

                } finally {

                    running.set(false);
                }
            });
        }
    }

    private void sendCurrentStatus(
            WebSocketSession session
    ) {

        ObjectNode status =
                objectMapper.createObjectNode();

        status.put(
                "type",
                "camera_status"
        );

        var cameras =
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
                    isCameraConnected(deviceId)
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

            cameras.add(camera);
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
                "timestamp",
                Instant.now().toString()
        );

        sendJson(
                session,
                status
        );
    }

    private void cleanupDeadSessions() {

        long now =
                System.currentTimeMillis();

        for (Map.Entry<String, Long> entry :
                lastHeartbeat.entrySet()) {

            String sessionId =
                    entry.getKey();

            long last =
                    entry.getValue();

            if (now - last >
                    HEARTBEAT_TIMEOUT_MS) {

                WebSocketSession session =
                        findSession(sessionId);

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

                removeSession(sessionId);
            }
        }
    }

    private WebSocketSession findSession(
            String sessionId
    ) {

        WebSocketSession session =
                dashboardSessions.get(
                        sessionId
                );

        if (session != null) {
            return session;
        }

        for (WebSocketSession camera :
                cameraSessions.values()) {

            if (camera.getId()
                    .equals(sessionId)) {

                return camera;
            }
        }

        return null;
    }

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

        if (deviceId != null) {

            WebSocketSession camera =
                    cameraSessions.get(
                            deviceId
                    );

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
            }
        }
    }

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

    private int totalSessions() {

        return cameraSessions.size()
                + dashboardSessions.size();
    }

    private String getDeviceId(
            WebSocketSession session
    ) {

        return sessionDeviceIds.getOrDefault(
                session.getId(),
                session.getId()
        );
    }

    private byte[] extractBytes(
            BinaryMessage message
    ) {

        ByteBuffer buffer =
                message.getPayload();

        byte[] bytes =
                new byte[buffer.remaining()];

        buffer.get(bytes);

        return bytes;
    }

    private void sendJson(
            WebSocketSession session,
            JsonNode json
    ) {

        if (session == null ||
                !session.isOpen()) {
            return;
        }

        try {

            session.sendMessage(
                    new TextMessage(
                            objectMapper.writeValueAsString(
                                    json
                            )
                    )
            );

        } catch (Exception e) {

            log.debug(
                    "Unable to send WebSocket message",
                    e
            );
        }
    }

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

        sendJson(
                session,
                error
        );
    }

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

    private String firstNonBlank(
            String first,
            String second
    ) {

        if (first != null &&
                !first.isBlank()) {

            return first;
        }

        if (second != null &&
                !second.isBlank()) {

            return second;
        }

        return null;
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    @PreDestroy
    public void shutdown() {

        log.info(
                "Shutting down SmartEye WebSocket handler"
        );

        scheduler.shutdownNow();
        detectionExecutor.shutdownNow();

        cameraSessions.clear();
        dashboardSessions.clear();
        latestFrames.clear();
        sessionDeviceIds.clear();
        lastHeartbeat.clear();
        deviceLocations.clear();
        deviceFocus.clear();
        detectionRunning.clear();
        frameCounters.clear();
    }
}