package sms.com.sms.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import sms.com.sms.ObjectDetectionService;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class CameraWebSocketHandler
        extends AbstractWebSocketHandler {

    private static final Logger logger =
            LoggerFactory.getLogger(
                    CameraWebSocketHandler.class
            );

    // ============================================================
    // CONFIGURATION
    // ============================================================

    private static final int MAX_BUFFER_SIZE =
            512 * 1024;

    private static final int MAX_SESSIONS =
            100;

    private static final long HEARTBEAT_TIMEOUT =
            60_000L;

    private static final String DEFAULT_FOCUS =
            "General";

    // ============================================================
    // SERVICES
    // ============================================================

    private final ObjectDetectionService detectionService;

    private final ObjectMapper objectMapper;

    // ============================================================
    // WEBSOCKET SESSIONS
    // ============================================================up

    private final Map<String, WebSocketSession> sessions =
            new ConcurrentHashMap<>();

    private final Map<String, String> deviceToSessionMap =
            new ConcurrentHashMap<>();

    private final Map<String, ReentrantLock> sessionLocks =
            new ConcurrentHashMap<>();

    // ============================================================
    // HEARTBEAT
    // ============================================================

    private final Map<String, Long> lastHeartbeat =
            new ConcurrentHashMap<>();

    // ============================================================
    // DEVICE DATA
    // ============================================================

    private final Map<String, DeviceInfo> deviceRegistry =
            new ConcurrentHashMap<>();

    private final Map<String, PersistentSessionData> persistentSessions =
            new ConcurrentHashMap<>();

    // ============================================================
    // STATISTICS
    // ============================================================

    private final AtomicLong frameCount =
            new AtomicLong(0);

    private final AtomicLong totalBytesReceived =
            new AtomicLong(0);

    private final AtomicLong totalConnections =
            new AtomicLong(0);

    private final AtomicLong totalReconnections =
            new AtomicLong(0);

    private final AtomicLong receivedFpsCounter =
            new AtomicLong(0);

    private volatile long receivedFpsStart =
            System.currentTimeMillis();

    // ============================================================
    // AI DETECTION
    // ============================================================

    /*
     * Only one frame is allowed to wait for AI processing.
     * Old waiting frames are discarded.
     */
    private final BlockingQueue<DetectionFrame> detectionQueue =
            new ArrayBlockingQueue<>(1);

    private final ExecutorService detectionExecutor =
            Executors.newSingleThreadExecutor(
                    r -> {
                        Thread thread =
                                new Thread(
                                        r,
                                        "smart-eye-detection"
                                );

                        thread.setDaemon(true);

                        return thread;
                    }
            );

    // ============================================================
    // SCHEDULER
    // ============================================================

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(
                    2
            );

    // ============================================================
    // START TIME
    // ============================================================

    private final long startTime =
            System.currentTimeMillis();

    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public CameraWebSocketHandler(
            @Lazy ObjectDetectionService detectionService,
            ObjectMapper objectMapper
    ) {

        this.detectionService =
                detectionService;

        this.objectMapper =
                objectMapper;

        scheduler.scheduleAtFixedRate(
                this::checkHeartbeats,
                10,
                10,
                TimeUnit.SECONDS
        );

        scheduler.scheduleAtFixedRate(
                this::cleanupInactiveSessions,
                60,
                60,
                TimeUnit.SECONDS
        );

        detectionExecutor.execute(
                this::detectionLoop
        );

        logger.info(
                "CameraWebSocketHandler initialized"
        );
    }

    // ============================================================
    // CONNECTION
    // ============================================================

    @Override
    public void afterConnectionEstablished(
            WebSocketSession session
    ) throws Exception {

        if (sessions.size() >= MAX_SESSIONS) {

            logger.warn(
                    "Maximum WebSocket sessions reached"
            );

            session.close(
                    CloseStatus.SERVICE_OVERLOAD
            );

            return;
        }

        sessions.put(
                session.getId(),
                session
        );

        sessionLocks.put(
                session.getId(),
                new ReentrantLock()
        );

        lastHeartbeat.put(
                session.getId(),
                System.currentTimeMillis()
        );

        session.setTextMessageSizeLimit(
                64 * 1024
        );

        session.setBinaryMessageSizeLimit(
                MAX_BUFFER_SIZE
        );

        totalConnections.incrementAndGet();

        logger.info(
                "WebSocket connected: session={}, total={}",
                session.getId(),
                sessions.size()
        );

        sendSystemMessage(
                session,
                "connected"
        );
    }

    // ============================================================
    // TEXT MESSAGE
    // ============================================================
    public void sendDetectionToClients(String detectionJson) {
        if (detectionJson == null || detectionJson.isBlank()) {
            return;
        }

        TextMessage message = new TextMessage(detectionJson);

        sessions.forEach((sessionId, session) -> {
            try {
                if (session != null && session.isOpen()) {
                    session.sendMessage(message);
                }
            } catch (IOException e) {
                logger.warn(
                        "Failed to send detection to WebSocket session {}: {}",
                        sessionId,
                        e.getMessage()
                );
            }
        });
    }
    @Override
    protected void handleTextMessage(
            WebSocketSession session,
            TextMessage message
    ) {

        if (!session.isOpen()) {
            return;
        }

        updateHeartbeat(
                session
        );

        String payload =
                message.getPayload();

        if (payload == null ||
                payload.isBlank()) {

            return;
        }

        try {

            JsonNode json =
                    objectMapper.readTree(
                            payload
                    );

            logger.debug(
                    "WebSocket text from {}: {}",
                    session.getId(),
                    payload
            );

            if (json.has("command")) {

                handleCommand(
                        session,
                        json
                );

                return;
            }

            String type =
                    json.path("type")
                            .asText(
                                    ""
                            );

            switch (type) {

                case "register":

                    handleRegistration(
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

                case "ping":

                    sendPong(
                            session
                    );

                    break;

                case "status":

                    handleStatusRequest(
                            session
                    );

                    break;

                case "stay_alive":

                    sendPong(
                            session
                    );

                    break;

                case "reconnect":

                    handleReconnect(
                            session,
                            json
                    );

                    break;

                case "set_focus":

                    handleSetFocus(
                            session,
                            json
                    );

                    break;

                default:

                    logger.debug(
                            "Unknown WebSocket message type: {}",
                            type
                    );

                    break;
            }

        } catch (Exception e) {

            logger.error(
                    "Error processing WebSocket text message",
                    e
            );
        }
    }

    // ============================================================
    // BINARY FRAME
    // ============================================================

    @Override
    protected void handleBinaryMessage(
            WebSocketSession session,
            BinaryMessage message
    ) {

        if (!session.isOpen()) {
            return;
        }

        ByteBuffer buffer =
                message.getPayload();

        if (buffer == null ||
                !buffer.hasRemaining()) {

            return;
        }

        byte[] imageData =
                new byte[
                        buffer.remaining()
                        ];

        buffer.get(
                imageData
        );

        if (imageData.length == 0) {
            return;
        }

        if (
                imageData.length >
                        MAX_BUFFER_SIZE
        ) {

            logger.warn(
                    "Frame too large: {} bytes",
                    imageData.length
            );

            return;
        }

        // --------------------------------------------------------
        // STATISTICS
        // --------------------------------------------------------

        long currentFrame =
                frameCount.incrementAndGet();

        totalBytesReceived.addAndGet(
                imageData.length
        );

        receivedFpsCounter.incrementAndGet();

        updateFrameFps();

        // --------------------------------------------------------
        // DEVICE
        // --------------------------------------------------------

        String deviceId =
                getDeviceId(
                        session
                );

        updateDeviceActivity(
                deviceId
        );

        if (
                currentFrame % 30 == 0
        ) {

            logger.info(
                    "Frame received: frame={}, bytes={}, device={}",
                    currentFrame,
                    imageData.length,
                    deviceId
            );
        }

        // --------------------------------------------------------
        // LIVE VIDEO
        // --------------------------------------------------------

        /*
         * Send the frame immediately to browser clients.
         *
         * This is independent of AI detection.
         */
        broadcastImage(
                imageData,
                session.getId()
        );

        // --------------------------------------------------------
        // AI
        // --------------------------------------------------------

        if (detectionService != null) {

            String location =
                    Objects.toString(
                            session.getAttributes()
                                    .get("location"),
                            "unknown"
                    );

            String focus =
                    Objects.toString(
                            session.getAttributes()
                                    .get("focus"),
                            Objects.toString(
                                    session.getAttributes()
                                            .get("cropType"),
                                    getCurrentDefaultFocus()
                            )
                    );

            submitLatestDetection(
                    imageData.clone(),
                    deviceId,
                    location,
                    focus
            );
        }
    }

    // ============================================================
    // FPS
    // ============================================================

    private void updateFrameFps() {

        long now =
                System.currentTimeMillis();

        long elapsed =
                now - receivedFpsStart;

        if (elapsed >= 5000) {

            long count =
                    receivedFpsCounter.getAndSet(
                            0
                    );

            double fps =
                    count *
                            1000.0 /
                            elapsed;

            logger.info(
                    "CAMERA INPUT FPS: {}",
                    String.format(
                            "%.2f",
                            fps
                    )
            );

            receivedFpsStart =
                    now;
        }
    }

    // ============================================================
    // LATEST AI FRAME
    // ============================================================

    private void submitLatestDetection(
            byte[] imageData,
            String deviceId,
            String location,
            String focus
    ) {

        if (detectionService == null) {
            return;
        }

        DetectionFrame frame =
                new DetectionFrame(
                        imageData,
                        deviceId,
                        location,
                        focus
                );

        /*
         * Try to put the new frame into the one-slot queue.
         */
        if (
                detectionQueue.offer(
                        frame
                )
        ) {

            return;
        }

        /*
         * Queue is full.
         *
         * Remove the OLD frame.
         */
        detectionQueue.poll();

        /*
         * Put the NEWEST frame.
         */
        detectionQueue.offer(
                frame
        );
    }

    // ============================================================
    // AI WORKER
    // ============================================================

    private void detectionLoop() {

        while (
                !Thread.currentThread()
                        .isInterrupted()
        ) {

            try {

                DetectionFrame frame =
                        detectionQueue.take();

                if (frame == null) {
                    continue;
                }

                try {

                    detectionService
                            .processAndSendDetection(
                                    frame.imageData,
                                    frame.deviceId,
                                    frame.location,
                                    frame.focus
                            );

                } catch (Exception e) {

                    logger.error(
                            "AI detection failed",
                            e
                    );
                }

            } catch (
                    InterruptedException e
            ) {

                Thread.currentThread()
                        .interrupt();

                break;

            } catch (Exception e) {

                logger.error(
                        "Detection worker error",
                        e
                );
            }
        }

        logger.info(
                "AI detection worker stopped"
        );
    }

    // ============================================================
    // SENSOR DATA
    // ============================================================

    private void handleSensorData(
            WebSocketSession session,
            JsonNode json
    ) {

        String deviceId =
                json.path(
                        "deviceId"
                ).asText(
                        getDeviceId(session)
                );

        double temperature =
                json.path(
                        "temperature"
                ).asDouble(
                        0
                );

        double humidity =
                json.path(
                        "humidity"
                ).asDouble(
                        0
                );

        String focus =
                json.path(
                        "focus"
                ).asText(
                        json.path(
                                "cropType"
                        ).asText(
                                DEFAULT_FOCUS
                        )
                );

        PersistentSessionData data =
                persistentSessions.computeIfAbsent(
                        deviceId,
                        key ->
                                new PersistentSessionData()
                );

        data.deviceId =
                deviceId;

        data.temperature =
                temperature;

        data.humidity =
                humidity;

        data.focus =
                focus;

        data.lastActivity =
                System.currentTimeMillis();

        DeviceInfo device =
                deviceRegistry.get(
                        deviceId
                );

        if (device != null) {

            device.temperature =
                    temperature;

            device.humidity =
                    humidity;

            device.focus =
                    focus;

            device.lastActivity =
                    System.currentTimeMillis();
        }

        ObjectNode update =
                objectMapper.createObjectNode();

        update.put(
                "type",
                "sensor_update"
        );

        update.put(
                "deviceId",
                deviceId
        );

        update.put(
                "temperature",
                temperature
        );

        update.put(
                "humidity",
                humidity
        );

        update.put(
                "focus",
                focus
        );

        broadcastText(
                update.toString(),
                session.getId()
        );
    }

    // ============================================================
    // REGISTRATION
    // ============================================================

    private void handleRegistration(
            WebSocketSession session,
            JsonNode json
    ) {

        String deviceId =
                json.path(
                        "deviceId"
                ).asText(
                        "camera_01"
                );

        String location =
                json.path(
                        "location"
                ).asText(
                        "unknown"
                );

        String deviceType =
                json.path(
                        "deviceType"
                ).asText(
                        "ESP32-CAM"
                );

        String focus =
                json.path(
                        "focus"
                ).asText(
                        json.path(
                                "cropType"
                        ).asText(
                                DEFAULT_FOCUS
                        )
                );

        String oldSessionId =
                deviceToSessionMap.put(
                        deviceId,
                        session.getId()
                );

        /*
         * If the same device reconnects,
         * remove its previous session mapping.
         */
        if (
                oldSessionId != null &&
                        !oldSessionId.equals(
                                session.getId()
                        )
        ) {

            WebSocketSession oldSession =
                    sessions.get(
                            oldSessionId
                    );

            if (
                    oldSession != null &&
                            oldSession.isOpen()
            ) {

                try {

                    oldSession.close(
                            CloseStatus.NORMAL
                    );

                } catch (IOException e) {

                    logger.debug(
                            "Unable to close old session",
                            e
                    );
                }
            }

            sessions.remove(
                    oldSessionId
            );

            sessionLocks.remove(
                    oldSessionId
            );

            lastHeartbeat.remove(
                    oldSessionId
            );

            totalReconnections.incrementAndGet();
        }

        session.getAttributes().put(
                "deviceId",
                deviceId
        );

        session.getAttributes().put(
                "location",
                location
        );

        session.getAttributes().put(
                "deviceType",
                deviceType
        );

        session.getAttributes().put(
                "focus",
                focus
        );

        session.getAttributes().put(
                "cropType",
                focus
        );

        PersistentSessionData data =
                persistentSessions.computeIfAbsent(
                        deviceId,
                        key ->
                                new PersistentSessionData()
                );

        data.deviceId =
                deviceId;

        data.location =
                location;

        data.deviceType =
                deviceType;

        data.focus =
                focus;

        data.lastActivity =
                System.currentTimeMillis();

        DeviceInfo device =
                deviceRegistry.computeIfAbsent(
                        deviceId,
                        key ->
                                new DeviceInfo()
                );

        device.deviceId =
                deviceId;

        device.deviceType =
                deviceType;

        device.location =
                location;

        device.focus =
                focus;

        device.lastActivity =
                System.currentTimeMillis();

        device.connected =
                true;

        JsonNode signalStrength =
                json.get(
                        "signalStrength"
                );

        if (
                signalStrength != null &&
                        signalStrength.isNumber()
        ) {

            device.signalStrength =
                    signalStrength.asInt();
        }

        JsonNode freeHeap =
                json.get(
                        "freeHeap"
                );

        if (
                freeHeap != null &&
                        freeHeap.isNumber()
        ) {

            device.freeHeap =
                    freeHeap.asLong();
        }

        JsonNode freePsram =
                json.get(
                        "freePsram"
                );

        if (
                freePsram != null &&
                        freePsram.isNumber()
        ) {

            device.freePsram =
                    freePsram.asLong();
        }

        JsonNode firmwareVersion =
                json.get(
                        "firmwareVersion"
                );

        if (
                firmwareVersion != null
        ) {

            device.firmwareVersion =
                    firmwareVersion.asText();
        }

        logger.info(
                "Device registered: device={}, location={}, focus={}, session={}",
                deviceId,
                location,
                focus,
                session.getId()
        );

        ObjectNode response =
                objectMapper.createObjectNode();

        response.put(
                "type",
                "registered"
        );

        response.put(
                "deviceId",
                deviceId
        );

        response.put(
                "status",
                "success"
        );

        response.put(
                "focus",
                focus
        );

        sendText(
                session,
                response.toString()
        );
    }

    // ============================================================
    // RECONNECT
    // ============================================================

    private void handleReconnect(
            WebSocketSession session,
            JsonNode json
    ) {

        String deviceId =
                json.path(
                        "deviceId"
                ).asText(
                        ""
                );

        if (
                deviceId.isBlank()
        ) {

            sendError(
                    session,
                    "deviceId is required"
            );

            return;
        }

        PersistentSessionData data =
                persistentSessions.get(
                        deviceId
                );

        if (data != null) {

            deviceToSessionMap.put(
                    deviceId,
                    session.getId()
            );

            session.getAttributes().put(
                    "deviceId",
                    deviceId
            );

            session.getAttributes().put(
                    "location",
                    data.location
            );

            session.getAttributes().put(
                    "focus",
                    data.focus
            );

            session.getAttributes().put(
                    "cropType",
                    data.focus
            );

            data.lastActivity =
                    System.currentTimeMillis();

            ObjectNode response =
                    objectMapper.createObjectNode();

            response.put(
                    "type",
                    "reconnected"
            );

            response.put(
                    "deviceId",
                    deviceId
            );

            response.put(
                    "status",
                    "success"
            );

            response.put(
                    "focus",
                    data.focus
            );

            sendText(
                    session,
                    response.toString()
            );

        } else {

            sendError(
                    session,
                    "Device session not found"
            );
        }
    }

    // ============================================================
    // SET FOCUS
    // ============================================================

    private void handleSetFocus(
            WebSocketSession session,
            JsonNode json
    ) {

        String focus =
                json.path(
                        "focus"
                ).asText(
                        json.path(
                                "cropType"
                        ).asText(
                                json.path(
                                        "value"
                                ).asText(
                                        DEFAULT_FOCUS
                                )
                        )
                );

        session.getAttributes().put(
                "focus",
                focus
        );

        session.getAttributes().put(
                "cropType",
                focus
        );

        String deviceId =
                getDeviceId(
                        session
                );

        if (deviceId != null) {

            PersistentSessionData data =
                    persistentSessions.get(
                            deviceId
                    );

            if (data != null) {

                data.focus =
                        focus;
            }

            DeviceInfo device =
                    deviceRegistry.get(
                            deviceId
                    );

            if (device != null) {

                device.focus =
                        focus;
            }
        }

        ObjectNode response =
                objectMapper.createObjectNode();

        response.put(
                "type",
                "focus_set"
        );

        response.put(
                "deviceId",
                deviceId == null
                        ? ""
                        : deviceId
        );

        response.put(
                "focus",
                focus
        );

        sendText(
                session,
                response.toString()
        );

        logger.info(
                "Focus changed: device={}, focus={}",
                deviceId,
                focus
        );
    }

    // ============================================================
    // COMMAND
    // ============================================================

    private void handleCommand(
            WebSocketSession session,
            JsonNode json
    ) {

        String command =
                json.path(
                        "command"
                ).asText(
                        ""
                );

        if (command.isBlank()) {
            return;
        }

        String targetDevice =
                json.path(
                        "deviceId"
                ).asText(
                        ""
                );

        if (targetDevice.isBlank()) {

            targetDevice =
                    getDeviceId(
                            session
                    );
        }

        if (targetDevice.isBlank()) {

            targetDevice =
                    "camera_01";
        }

        String targetSessionId =
                deviceToSessionMap.get(
                        targetDevice
                );

        if (targetSessionId == null) {

            sendError(
                    session,
                    "Device is not connected: " +
                            targetDevice
            );

            return;
        }

        WebSocketSession targetSession =
                sessions.get(
                        targetSessionId
                );

        if (
                targetSession == null ||
                        !targetSession.isOpen()
        ) {

            sendError(
                    session,
                    "Target device session is closed"
            );

            return;
        }

        ObjectNode commandMessage =
                objectMapper.createObjectNode();

        commandMessage.put(
                "command",
                command
        );

        commandMessage.put(
                "deviceId",
                targetDevice
        );

        /*
         * Preserve value.
         */
        if (json.has("value")) {

            commandMessage.set(
                    "value",
                    json.get("value")
            );
        }

        /*
         * IMPORTANT:
         * Forward focus because ESP32 expects it.
         */
        if (json.has("focus")) {

            commandMessage.set(
                    "focus",
                    json.get("focus")
            );
        }

        if (json.has("cropType")) {

            commandMessage.set(
                    "cropType",
                    json.get("cropType")
            );
        }

        commandMessage.put(
                "timestamp",
                System.currentTimeMillis()
        );

        sendText(
                targetSession,
                commandMessage.toString()
        );

        ObjectNode response =
                objectMapper.createObjectNode();

        response.put(
                "type",
                "command_forwarded"
        );

        response.put(
                "command",
                command
        );

        response.put(
                "deviceId",
                targetDevice
        );

        response.put(
                "status",
                "success"
        );

        sendText(
                session,
                response.toString()
        );
    }

    // ============================================================
    // STATUS
    // ============================================================

    private void handleStatusRequest(
            WebSocketSession session
    ) {

        ObjectNode response =
                objectMapper.createObjectNode();

        response.put(
                "type",
                "status"
        );

        response.put(
                "connections",
                sessions.size()
        );

        response.put(
                "devices",
                deviceRegistry.size()
        );

        response.put(
                "framesReceived",
                frameCount.get()
        );

        response.put(
                "bytesReceived",
                totalBytesReceived.get()
        );

        response.put(
                "uptime",
                System.currentTimeMillis()
                        - startTime
        );

        response.put(
                "aiQueueSize",
                detectionQueue.size()
        );

        sendText(
                session,
                response.toString()
        );
    }

    // ============================================================
    // LIVE IMAGE BROADCAST
    // ============================================================

    private void broadcastImage(
            byte[] imageData,
            String sourceSessionId
    ) {

        for (
                WebSocketSession target :
                sessions.values()
        ) {

            if (
                    !target.isOpen()
            ) {

                continue;
            }

            if (
                    target.getId()
                            .equals(
                                    sourceSessionId
                            )
            ) {

                continue;
            }

            sendBinaryFrame(
                    target,
                    imageData
            );
        }
    }

    // ============================================================
    // BINARY SEND
    // ============================================================

    private void sendBinaryFrame(
            WebSocketSession session,
            byte[] imageData
    ) {

        ReentrantLock lock =
                sessionLocks.computeIfAbsent(
                        session.getId(),
                        key ->
                                new ReentrantLock()
                );

        /*
         * Do not wait for an old frame to finish.
         *
         * If browser/network is behind,
         * drop this frame instead of creating
         * latency.
         */
        if (
                !lock.tryLock()
        ) {

            return;
        }

        try {

            if (
                    !session.isOpen()
            ) {

                return;
            }

            BinaryMessage message =
                    new BinaryMessage(
                            ByteBuffer.wrap(
                                    imageData
                            )
                    );

            session.sendMessage(
                    message
            );

        } catch (Exception e) {

            logger.debug(
                    "Unable to send binary frame to {}",
                    session.getId(),
                    e
            );

        } finally {

            lock.unlock();
        }
    }

    // ============================================================
    // TEXT BROADCAST
    // ============================================================

    private void broadcastText(
            String message,
            String sourceSessionId
    ) {

        for (
                WebSocketSession target :
                sessions.values()
        ) {

            if (
                    !target.isOpen()
            ) {

                continue;
            }

            if (
                    target.getId()
                            .equals(
                                    sourceSessionId
                            )
            ) {

                continue;
            }

            sendText(
                    target,
                    message
            );
        }
    }

    // ============================================================
    // SEND TEXT
    // ============================================================

    private void sendText(
            WebSocketSession session,
            String message
    ) {

        if (
                session == null ||
                        !session.isOpen()
        ) {

            return;
        }

        ReentrantLock lock =
                sessionLocks.computeIfAbsent(
                        session.getId(),
                        key ->
                                new ReentrantLock()
                );

        if (
                !lock.tryLock()
        ) {

            return;
        }

        try {

            if (
                    session.isOpen()
            ) {

                session.sendMessage(
                        new TextMessage(
                                message
                        )
                );
            }

        } catch (Exception e) {

            logger.debug(
                    "Unable to send text message",
                    e
            );

        } finally {

            lock.unlock();
        }
    }

    // ============================================================
    // SYSTEM MESSAGE
    // ============================================================

    private void sendSystemMessage(
            WebSocketSession session,
            String status
    ) {

        ObjectNode message =
                objectMapper.createObjectNode();

        message.put(
                "type",
                "system"
        );

        message.put(
                "status",
                status
        );

        message.put(
                "sessionId",
                session.getId()
        );

        sendText(
                session,
                message.toString()
        );
    }

    // ============================================================
    // PONG
    // ============================================================

    private void sendPong(
            WebSocketSession session
    ) {

        ObjectNode response =
                objectMapper.createObjectNode();

        response.put(
                "type",
                "pong"
        );

        response.put(
                "timestamp",
                System.currentTimeMillis()
        );

        sendText(
                session,
                response.toString()
        );
    }

    // ============================================================
    // ERROR
    // ============================================================

    private void sendError(
            WebSocketSession session,
            String error
    ) {

        ObjectNode response =
                objectMapper.createObjectNode();

        response.put(
                "type",
                "error"
        );

        response.put(
                "message",
                error
        );

        sendText(
                session,
                response.toString()
        );
    }

    // ============================================================
    // HEARTBEAT
    // ============================================================

    private void updateHeartbeat(
            WebSocketSession session
    ) {

        lastHeartbeat.put(
                session.getId(),
                System.currentTimeMillis()
        );
    }

    // ============================================================
    // DEVICE ACTIVITY
    // ============================================================

    private void updateDeviceActivity(
            String deviceId
    ) {

        if (
                deviceId == null ||
                        deviceId.isBlank()
        ) {

            return;
        }

        long now =
                System.currentTimeMillis();

        PersistentSessionData data =
                persistentSessions.get(
                        deviceId
                );

        if (data != null) {

            data.framesReceived++;

            data.lastActivity =
                    now;

            data.lastSeen =
                    now;
        }

        DeviceInfo device =
                deviceRegistry.get(
                        deviceId
                );

        if (device != null) {

            device.framesReceived++;

            device.lastActivity =
                    now;

            device.lastSeen =
                    now;

            device.connected =
                    true;
        }
    }

    // ============================================================
    // DEVICE ID
    // ============================================================

    private String getDeviceId(
            WebSocketSession session
    ) {

        Object deviceId =
                session.getAttributes()
                        .get(
                                "deviceId"
                        );

        return deviceId == null
                ? null
                : deviceId.toString();
    }

    // ============================================================
    // DEFAULT FOCUS
    // ============================================================

    private String getCurrentDefaultFocus() {

        return DEFAULT_FOCUS;
    }

    // ============================================================
    // HEARTBEAT CHECK
    // ============================================================

    private void checkHeartbeats() {

        long now =
                System.currentTimeMillis();

        List<WebSocketSession> expired =
                new ArrayList<>();

        for (
                WebSocketSession session :
                sessions.values()
        ) {

            Long last =
                    lastHeartbeat.get(
                            session.getId()
                    );

            if (
                    last == null
            ) {

                continue;
            }

            if (
                    now - last >
                            HEARTBEAT_TIMEOUT
            ) {

                expired.add(
                        session
                );
            }
        }

        for (
                WebSocketSession session :
                expired
        ) {

            logger.info(
                    "Closing inactive WebSocket: {}",
                    session.getId()
            );

            try {

                if (
                        session.isOpen()
                ) {

                    session.close(
                            CloseStatus.GOING_AWAY
                    );
                }

            } catch (Exception e) {

                logger.debug(
                        "Error closing inactive session",
                        e
                );
            }
        }
    }

    // ============================================================
    // CLEANUP
    // ============================================================

    private void cleanupInactiveSessions() {

        List<String> inactiveSessions =
                new ArrayList<>();

        for (
                Map.Entry<String,
                        WebSocketSession> entry :
                sessions.entrySet()
        ) {

            WebSocketSession session =
                    entry.getValue();

            if (
                    session == null ||
                            !session.isOpen()
            ) {

                inactiveSessions.add(
                        entry.getKey()
                );
            }
        }

        for (
                String sessionId :
                inactiveSessions
        ) {

            removeSession(
                    sessionId
            );
        }
    }

    // ============================================================
    // CONNECTION CLOSED
    // ============================================================

    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            CloseStatus status
    ) {

        String sessionId =
                session.getId();

        String deviceId =
                getDeviceId(
                        session
                );

        removeSession(
                sessionId
        );

        if (
                deviceId != null
        ) {

            String mapped =
                    deviceToSessionMap.get(
                            deviceId
                    );

            if (
                    sessionId.equals(
                            mapped
                    )
            ) {

                deviceToSessionMap.remove(
                        deviceId
                );
            }

            DeviceInfo device =
                    deviceRegistry.get(
                            deviceId
                    );

            if (device != null) {

                device.connected =
                        false;

                device.lastSeen =
                        System.currentTimeMillis();
            }

            PersistentSessionData data =
                    persistentSessions.get(
                            deviceId
                    );

            if (data != null) {

                data.lastSeen =
                        System.currentTimeMillis();
            }
        }

        logger.info(
                "WebSocket closed: session={}, device={}, status={}",
                sessionId,
                deviceId,
                status
        );
    }

    // ============================================================
    // REMOVE SESSION
    // ============================================================

    private void removeSession(
            String sessionId
    ) {

        sessions.remove(
                sessionId
        );

        lastHeartbeat.remove(
                sessionId
        );

        sessionLocks.remove(
                sessionId
        );
    }

    // ============================================================
    // PRE DESTROY
    // ============================================================

    @PreDestroy
    public void shutdown() {

        logger.info(
                "Shutting down CameraWebSocketHandler"
        );

        scheduler.shutdownNow();

        detectionExecutor.shutdownNow();

        for (
                WebSocketSession session :
                sessions.values()
        ) {

            try {

                if (
                        session.isOpen()
                ) {

                    session.close(
                            CloseStatus.GOING_AWAY
                    );
                }

            } catch (Exception e) {

                logger.debug(
                        "Error closing session",
                        e
                );
            }
        }

        sessions.clear();

        deviceToSessionMap.clear();

        lastHeartbeat.clear();

        sessionLocks.clear();

        detectionQueue.clear();
    }

    // ============================================================
    // DETECTION FRAME
    // ============================================================

    private static class DetectionFrame {

        private final byte[] imageData;

        private final String deviceId;

        private final String location;

        private final String focus;

        private DetectionFrame(
                byte[] imageData,
                String deviceId,
                String location,
                String focus
        ) {

            this.imageData =
                    imageData;

            this.deviceId =
                    deviceId;

            this.location =
                    location;

            this.focus =
                    focus;
        }
    }

    // ============================================================
    // PERSISTENT SESSION
    // ============================================================

    private static class PersistentSessionData {

        String deviceId;

        String location;

        String deviceType;

        String focus =
                DEFAULT_FOCUS;

        double temperature;

        double humidity;

        long framesReceived;

        long lastActivity;

        long lastSeen;
    }

    // ============================================================
    // DEVICE INFO
    // ============================================================

    private static class DeviceInfo {

        String deviceId;

        String deviceType;

        String location;

        String focus =
                DEFAULT_FOCUS;

        boolean connected;

        int signalStrength;

        long freeHeap;

        long freePsram;

        String firmwareVersion;

        double temperature;

        double humidity;

        long framesReceived;

        long lastActivity;

        long lastSeen;
    }
}