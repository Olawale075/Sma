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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class CameraWebSocketHandler extends AbstractWebSocketHandler {

    private static final Logger logger =
            LoggerFactory.getLogger(CameraWebSocketHandler.class);

    private static final int MAX_BUFFER_SIZE = 10 * 1024 * 1024;
    private static final int MAX_QUEUE_SIZE = 100;
    private static final int MAX_SESSIONS = 100;

    private static final long HEARTBEAT_TIMEOUT_MS = 30_000;
    private static final long CLEANUP_INTERVAL_MS = 60_000;

    private final ObjectDetectionService detectionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ConcurrentHashMap<String, WebSocketSession> sessions =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, String> deviceToSessionMap =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Long> lastHeartbeat =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, DeviceInfo> deviceRegistry =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, ReentrantLock> sessionLocks =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, BlockingQueue<Object>> messageQueues =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, PersistentSessionData> persistentSessions =
            new ConcurrentHashMap<>();

    private final AtomicInteger frameCount =
            new AtomicInteger();

    private final AtomicLong totalBytesReceived =
            new AtomicLong();

    private final AtomicInteger totalConnections =
            new AtomicInteger();

    private final AtomicInteger totalReconnections =
            new AtomicInteger();

    private final AtomicLong startTime =
            new AtomicLong(System.currentTimeMillis());

    private final ExecutorService detectionExecutor =
            Executors.newFixedThreadPool(2);

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2);

    public CameraWebSocketHandler(
            @Lazy ObjectDetectionService detectionService) {

        this.detectionService = detectionService;

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

        logger.info("CameraWebSocketHandler initialized");
    }

    @Override
    public void afterConnectionEstablished(
            WebSocketSession session) throws Exception {

        if (sessions.size() >= MAX_SESSIONS) {
            session.close(new CloseStatus(
                    1013,
                    "Server is full"
            ));
            return;
        }

        String sessionId = session.getId();

        sessions.put(sessionId, session);
        lastHeartbeat.put(
                sessionId,
                System.currentTimeMillis()
        );

        sessionLocks.put(
                sessionId,
                new ReentrantLock()
        );

        messageQueues.put(
                sessionId,
                new LinkedBlockingQueue<>(MAX_QUEUE_SIZE)
        );

        totalConnections.incrementAndGet();

        configureSession(session);

        logger.info(
                "WebSocket connected: session={}",
                sessionId
        );

        sendJson(
                session,
                objectMapper.createObjectNode()
                        .put("type", "system")
                        .put("status", "connected")
                        .put("sessionId", sessionId)
                        .put(
                                "message",
                                "WebSocket connection established"
                        )
                        .put(
                                "timestamp",
                                System.currentTimeMillis()
                        )
                        .toString()
        );
    }

    private void configureSession(WebSocketSession session) {

        try {
            session.setTextMessageSizeLimit(MAX_BUFFER_SIZE);
            session.setBinaryMessageSizeLimit(MAX_BUFFER_SIZE);
        } catch (Exception e) {
            logger.warn(
                    "Could not configure WebSocket limits: {}",
                    e.getMessage()
            );
        }
    }

    @Override
    protected void handleTextMessage(
            WebSocketSession session,
            TextMessage message) {

        if (!session.isOpen()) {
            return;
        }

        lastHeartbeat.put(
                session.getId(),
                System.currentTimeMillis()
        );

        try {

            String payload = message.getPayload();

            logger.debug(
                    "Received text from {}: {}",
                    session.getId(),
                    payload
            );

            JsonNode json = objectMapper.readTree(payload);

            if (json == null || !json.isObject()) {

                sendError(
                        session,
                        "Message must be a JSON object"
                );

                return;
            }

            String type =
                    json.path("type").asText("");

            if (json.has("command")) {
                handleCommand(session, json);
                return;
            }

            switch (type) {
                case "sensor_data":

                    double temp =
                            json.path("temperature")
                                    .asDouble(Double.NaN);

                    double hum =
                            json.path("humidity")
                                    .asDouble(Double.NaN);

                    double soilMoisture =
                            json.path("soilMoisture")
                                    .asDouble(Double.NaN);

                    String soilStatus =
                            json.path("soilStatus")
                                    .asText("");

                    int soilRaw =
                            json.path("soilRaw")
                                    .asInt(-1);

                    String devId =
                            getDeviceId(session);

                    if (devId != null) {

                        PersistentSessionData data =
                                persistentSessions.computeIfAbsent(
                                        devId,
                                        PersistentSessionData::new
                                );

                        if (!Double.isNaN(temp)) {
                            data.lastTemperature = temp;
                        }

                        if (!Double.isNaN(hum)) {
                            data.lastHumidity = hum;
                        }

                        data.lastSeen =
                                System.currentTimeMillis();
                    }

                    ObjectNode sensorUpdate =
                            objectMapper.createObjectNode();

                    sensorUpdate
                            .put("type", "sensor_update")
                            .put(
                                    "deviceId",
                                    devId != null ? devId : "unknown"
                            )
                            .put(
                                    "timestamp",
                                    System.currentTimeMillis()
                            );

                    if (!Double.isNaN(temp)) {
                        sensorUpdate.put(
                                "temperature",
                                temp
                        );
                    }

                    if (!Double.isNaN(hum)) {
                        sensorUpdate.put(
                                "humidity",
                                hum
                        );
                    }

                    if (!Double.isNaN(soilMoisture)) {
                        sensorUpdate.put(
                                "soilMoisture",
                                soilMoisture
                        );
                    }

                    if (!soilStatus.isBlank()) {
                        sensorUpdate.put(
                                "soilStatus",
                                soilStatus
                        );
                    }

                    if (soilRaw >= 0) {
                        sensorUpdate.put(
                                "soilRaw",
                                soilRaw
                        );
                    }

                    broadcastText(
                            sensorUpdate.toString()
                    );

                    break;

                case "register":
                    handleRegistration(
                            session,
                            json
                    );
                    break;

                case "ping":
                    handlePing(
                            session
                    );
                    break;

                case "status":
                    handleStatusRequest(
                            session
                    );
                    break;

                case "stay_alive":
                    handleStayAlive(
                            session
                    );
                    break;

                case "reconnect":
                    handleReconnect(
                            session,
                            json
                    );
                    break;

                default:
                    sendJson(
                            session,
                            objectMapper.createObjectNode()
                                    .put("type", "echo")
                                    .put("message", payload)
                                    .put(
                                            "timestamp",
                                            System.currentTimeMillis()
                                    )
                                    .toString()
                    );
            }

        } catch (Exception e) {

            logger.error(
                    "WebSocket message error",
                    e
            );

            sendError(
                    session,
                    "Invalid WebSocket message"
            );
        }
    }

    private void handleRegistration(
            WebSocketSession session,
            JsonNode json) {

        String deviceId =
                json.path("deviceId").asText("");

        if (deviceId.isBlank()) {

            sendError(
                    session,
                    "deviceId is required"
            );

            return;
        }

        String location =
                json.path("location")
                        .asText("unknown");

        String cropType =
                json.path("cropType")
                        .asText("unknown");

        String deviceType =
                json.path("deviceType")
                        .asText("ESP32-CAM");

        String ipAddress =
                json.path("ipAddress")
                        .asText(
                                session.getRemoteAddress() != null
                                        ? session.getRemoteAddress().toString()
                                        : "unknown"
                        );

        int signalStrength =
                json.path("signalStrength")
                        .asInt(0);

        String oldSessionId =
                deviceToSessionMap.put(
                        deviceId,
                        session.getId()
                );

        if (oldSessionId != null
                && !oldSessionId.equals(session.getId())) {

            WebSocketSession oldSession =
                    sessions.get(oldSessionId);

            if (oldSession != null
                    && oldSession.isOpen()) {

                try {
                    oldSession.close(
                            new CloseStatus(
                                    1000,
                                    "Replaced by new connection"
                            )
                    );
                } catch (IOException e) {

                    logger.warn(
                            "Could not close old session {}",
                            oldSessionId
                    );
                }
            }

            sessions.remove(oldSessionId);
            lastHeartbeat.remove(oldSessionId);
            sessionLocks.remove(oldSessionId);
            messageQueues.remove(oldSessionId);

            totalReconnections.incrementAndGet();
        }

        session.getAttributes()
                .put("deviceId", deviceId);

        session.getAttributes()
                .put("location", location);

        session.getAttributes()
                .put("cropType", cropType);

        PersistentSessionData persistent =
                persistentSessions.computeIfAbsent(
                        deviceId,
                        PersistentSessionData::new
                );

        persistent.location = location;
        persistent.cropType = cropType;
        persistent.deviceType = deviceType;
        persistent.ipAddress = ipAddress;
        persistent.signalStrength = signalStrength;
        persistent.shouldBeConnected = true;
        persistent.lastSeen = System.currentTimeMillis();
        persistent.lastHeartbeat = System.currentTimeMillis();

        DeviceInfo device =
                new DeviceInfo(
                        deviceId,
                        location,
                        cropType,
                        deviceType,
                        ipAddress,
                        signalStrength,
                        session.getId()
                );

        deviceRegistry.put(
                deviceId,
                device
        );

        logger.info(
                "Device registered: deviceId={}, sessionId={}, ip={}, signal={}",
                deviceId,
                session.getId(),
                ipAddress,
                signalStrength
        );

        sendJson(
                session,
                objectMapper.createObjectNode()
                        .put("type", "system")
                        .put("status", "registered")
                        .put(
                                "message",
                                "Device registered successfully"
                        )
                        .put(
                                "deviceId",
                                deviceId
                        )
                        .put(
                                "persistent",
                                true
                        )
                        .put(
                                "timestamp",
                                System.currentTimeMillis()
                        )
                        .toString()
        );
    }

    private void handleReconnect(
            WebSocketSession session,
            JsonNode json) {

        String deviceId =
                json.path("deviceId").asText("");

        if (deviceId.isBlank()) {

            sendError(
                    session,
                    "deviceId is required"
            );

            return;
        }

        PersistentSessionData data =
                persistentSessions.get(deviceId);

        if (data == null) {

            handleRegistration(
                    session,
                    json
            );

            return;
        }

        session.getAttributes()
                .put("deviceId", deviceId);

        session.getAttributes()
                .put("location", data.location);

        session.getAttributes()
                .put("cropType", data.cropType);

        String oldSessionId =
                deviceToSessionMap.put(
                        deviceId,
                        session.getId()
                );

        if (oldSessionId != null
                && !oldSessionId.equals(session.getId())) {

            sessions.remove(oldSessionId);
            lastHeartbeat.remove(oldSessionId);
            sessionLocks.remove(oldSessionId);
            messageQueues.remove(oldSessionId);
        }

        DeviceInfo device =
                deviceRegistry.get(deviceId);

        if (device != null) {
            device.sessionId =
                    session.getId();

            device.lastActivity =
                    System.currentTimeMillis();
        }

        data.shouldBeConnected = true;
        data.lastSeen =
                System.currentTimeMillis();

        data.lastHeartbeat =
                System.currentTimeMillis();

        totalReconnections.incrementAndGet();

        sendJson(
                session,
                objectMapper.createObjectNode()
                        .put("type", "system")
                        .put("status", "reconnected")
                        .put(
                                "deviceId",
                                deviceId
                        )
                        .put(
                                "message",
                                "Device reconnected successfully"
                        )
                        .put(
                                "timestamp",
                                System.currentTimeMillis()
                        )
                        .toString()
        );

        logger.info(
                "Device reconnected: {}",
                deviceId
        );
    }

    private void handlePing(
            WebSocketSession session) {

        updateHeartbeat(session);

        String deviceId =
                getDeviceId(session);

        sendJson(
                session,
                objectMapper.createObjectNode()
                        .put("type", "pong")
                        .put(
                                "deviceId",
                                deviceId != null
                                        ? deviceId
                                        : "unknown"
                        )
                        .put(
                                "timestamp",
                                System.currentTimeMillis()
                        )
                        .toString()
        );
    }

    private void handleStayAlive(
            WebSocketSession session) {

        updateHeartbeat(session);

        String deviceId =
                getDeviceId(session);

        sendJson(
                session,
                objectMapper.createObjectNode()
                        .put(
                                "type",
                                "stay_alive_response"
                        )
                        .put(
                                "deviceId",
                                deviceId != null
                                        ? deviceId
                                        : "unknown"
                        )
                        .put(
                                "timestamp",
                                System.currentTimeMillis()
                        )
                        .toString()
        );
    }

    private void updateHeartbeat(
            WebSocketSession session) {

        long now =
                System.currentTimeMillis();

        lastHeartbeat.put(
                session.getId(),
                now
        );

        String deviceId =
                getDeviceId(session);

        if (deviceId != null) {

            PersistentSessionData data =
                    persistentSessions.get(deviceId);

            if (data != null) {

                data.lastHeartbeat = now;
                data.lastSeen = now;
                data.lastActivity = now;
                data.shouldBeConnected = true;
            }

            DeviceInfo device =
                    deviceRegistry.get(deviceId);

            if (device != null) {
                device.lastActivity = now;
            }
        }
    }

    private void handleStatusRequest(
            WebSocketSession session) {

        String deviceId =
                getDeviceId(session);

        long uptime =
                (System.currentTimeMillis()
                        - startTime.get())
                        / 1000;

        sendJson(
                session,
                objectMapper.createObjectNode()
                        .put("type", "status")
                        .put(
                                "deviceId",
                                deviceId != null
                                        ? deviceId
                                        : "unknown"
                        )
                        .put(
                                "frames",
                                frameCount.get()
                        )
                        .put(
                                "sessions",
                                sessions.size()
                        )
                        .put(
                                "devices",
                                deviceRegistry.size()
                        )
                        .put(
                                "persistentDevices",
                                persistentSessions.size()
                        )
                        .put(
                                "totalBytes",
                                totalBytesReceived.get()
                        )
                        .put(
                                "totalConnections",
                                totalConnections.get()
                        )
                        .put(
                                "totalReconnections",
                                totalReconnections.get()
                        )
                        .put(
                                "uptime",
                                uptime
                        )
                        .toString()
        );
    }
    private void handleCommand(WebSocketSession session, JsonNode json) {
        String command = json.path("command").asText("");
        if (command.isBlank()) { sendError(session, "command is required"); return; }

        // If this session is the browser (not a registered device), find the target device.
        String requesterDeviceId = getDeviceId(session);
        String targetDeviceId = json.path("deviceId").asText(
                json.path("device_id").asText("camera_01")); // fall back to your known id

        String targetSessionId = deviceToSessionMap.get(targetDeviceId);
        WebSocketSession deviceSession = targetSessionId != null
                ? sessions.get(targetSessionId) : null;

        if (deviceSession == null || !deviceSession.isOpen()) {
            sendError(session, "Device " + targetDeviceId + " is not connected");
            return;
        }

        // Forward verbatim to the device, but ensure field name is "command"
        ObjectNode forward = objectMapper.createObjectNode();
        forward.put("command", command);
        if (json.has("value")) forward.set("value", json.get("value"));
        forward.put("timestamp", System.currentTimeMillis());

        sendJson(deviceSession, forward.toString());

        // Ack the requester
        sendCommandResponse(session, command, "forwarded");
    }

    private void sendCommandResponse(
            WebSocketSession session,
            String command,
            String status) {

        sendJson(
                session,
                objectMapper.createObjectNode()
                        .put(
                                "type",
                                "command_response"
                        )
                        .put(
                                "command",
                                command
                        )
                        .put(
                                "status",
                                status
                        )
                        .put(
                                "timestamp",
                                System.currentTimeMillis()
                        )
                        .toString()
        );
    }

    @Override
    protected void handleBinaryMessage(
            WebSocketSession session,
            BinaryMessage message) {

        if (!session.isOpen()) {
            return;
        }

        updateHeartbeat(session);

        ByteBuffer buffer =
                message.getPayload();

        byte[] imageData =
                new byte[buffer.remaining()];

        buffer.get(imageData);

        if (imageData.length == 0) {
            return;
        }

        int currentFrame =
                frameCount.incrementAndGet();

        totalBytesReceived.addAndGet(
                imageData.length
        );

        String deviceId =
                getDeviceId(session);

        if (deviceId != null) {

            PersistentSessionData data =
                    persistentSessions.get(deviceId);

            if (data != null) {

                data.framesReceived++;
                data.lastActivity =
                        System.currentTimeMillis();
                data.lastSeen =
                        System.currentTimeMillis();
            }

            DeviceInfo device =
                    deviceRegistry.get(deviceId);

            if (device != null) {
                device.lastActivity =
                        System.currentTimeMillis();
            }
        }

        if (currentFrame % 30 == 0) {

            logger.info(
                    "Frame received: frame={}, bytes={}, device={}",
                    currentFrame,
                    imageData.length,
                    deviceId
            );
        }

        broadcastImage(
                imageData,
                session.getId()
        );

        if (detectionService != null) {

            byte[] detectionData =
                    imageData.clone();

            String location = Objects.toString(
                    session.getAttributes().get("location"),
                    "unknown"
            );

            String cropType = Objects.toString(
                    session.getAttributes().get("cropType"),
                    detectionService.getDefaultCropFocus()
            );

            detectionExecutor.submit(() -> {

                try {

                    detectionService
                            .processAndSendDetection(
                                    detectionData,
                                    deviceId,
                                    location,
                                    cropType
                            );

                } catch (Exception e) {

                    logger.error(
                            "Detection processing error",
                            e
                    );
                }
            });
        }
    }

    private void broadcastImage(
            byte[] imageData,
            String sourceSessionId) {

        for (WebSocketSession client :
                sessions.values()) {

            if (!client.isOpen()) {
                continue;
            }

            if (client.getId()
                    .equals(sourceSessionId)) {
                continue;
            }

            sendMessageSafely(
                    client,
                    new BinaryMessage(
                            ByteBuffer.wrap(
                                    imageData.clone()
                            )
                    )
            );
        }
    }

    public void sendDetectionToClients(
            String detectionJson) {

        if (detectionJson == null
                || detectionJson.isBlank()) {
            return;
        }

        try {

            JsonNode node =
                    objectMapper.readTree(
                            detectionJson
                    );

            if (!node.isObject()) {
                return;
            }

            if (!node.has("type")) {

                ((com.fasterxml.jackson.databind.node.ObjectNode) node)
                        .put(
                                "type",
                                "detection"
                        );
            }

            String message =
                    objectMapper.writeValueAsString(
                            node
                    );

            broadcastText(message);

        } catch (Exception e) {

            logger.error(
                    "Invalid detection JSON",
                    e
            );
        }
    }

    public void broadcastText(
            String message) {

        if (message == null
                || message.isBlank()) {
            return;
        }

        TextMessage textMessage =
                new TextMessage(message);

        for (WebSocketSession session :
                sessions.values()) {

            if (session.isOpen()) {

                sendMessageSafely(
                        session,
                        textMessage
                );
            }
        }
    }

    private void sendJson(
            WebSocketSession session,
            String json) {

        sendMessageSafely(
                session,
                new TextMessage(json)
        );
    }

    private void sendError(
            WebSocketSession session,
            String message) {

        try {

            sendJson(
                    session,
                    objectMapper.createObjectNode()
                            .put(
                                    "type",
                                    "error"
                            )
                            .put(
                                    "message",
                                    message
                            )
                            .put(
                                    "timestamp",
                                    System.currentTimeMillis()
                            )
                            .toString()
            );

        } catch (Exception e) {

            logger.warn(
                    "Could not send error message",
                    e
            );
        }
    }

    private void sendMessageSafely(
            WebSocketSession session,
            Object message) {

        if (session == null
                || !session.isOpen()) {
            return;
        }

        ReentrantLock lock =
                sessionLocks.computeIfAbsent(
                        session.getId(),
                        key -> new ReentrantLock()
                );

        if (!lock.tryLock()) {

            queueMessage(
                    session,
                    message
            );

            return;
        }

        try {

            if (!session.isOpen()) {
                return;
            }

            if (message instanceof TextMessage) {

                session.sendMessage(
                        (TextMessage) message
                );

            } else if (message instanceof BinaryMessage) {

                session.sendMessage(
                        (BinaryMessage) message
                );

            } else {

                logger.warn(
                        "Unsupported WebSocket message type: {}",
                        message.getClass()
                );
            }

        } catch (IOException e) {

            logger.warn(
                    "Failed to send message to {}: {}",
                    session.getId(),
                    e.getMessage()
            );

        } finally {

            lock.unlock();
        }
    }

    private void queueMessage(
            WebSocketSession session,
            Object message) {

        if (session == null
                || !session.isOpen()) {
            return;
        }

        BlockingQueue<Object> queue =
                messageQueues.computeIfAbsent(
                        session.getId(),
                        key -> new LinkedBlockingQueue<>(
                                MAX_QUEUE_SIZE
                        )
                );

        if (!queue.offer(message)) {

            queue.poll();
            queue.offer(message);
        }
    }

    private void checkHeartbeats() {

        long now =
                System.currentTimeMillis();

        for (Map.Entry<String, Long> entry :
                lastHeartbeat.entrySet()) {

            String sessionId =
                    entry.getKey();

            Long last =
                    entry.getValue();

            if (now - last
                    > HEARTBEAT_TIMEOUT_MS) {

                WebSocketSession session =
                        sessions.get(sessionId);

                if (session == null) {
                    continue;
                }

                String deviceId =
                        getDeviceId(session);

                logger.warn(
                        "Heartbeat timeout: session={}, device={}",
                        sessionId,
                        deviceId
                );

                if (deviceId != null) {

                    PersistentSessionData data =
                            persistentSessions.get(
                                    deviceId
                            );

                    if (data != null) {

                        data.shouldBeConnected = true;
                        data.lastSeen = now;
                    }
                }

                try {

                    if (session.isOpen()) {

                        session.close(
                                new CloseStatus(
                                        1001,
                                        "Heartbeat timeout"
                                )
                        );
                    }

                } catch (IOException e) {

                    logger.warn(
                            "Could not close timed out session {}",
                            sessionId
                    );
                }
            }
        }
    }

    private void cleanupInactiveSessions() {

        for (Map.Entry<String, WebSocketSession> entry :
                sessions.entrySet()) {

            WebSocketSession session =
                    entry.getValue();

            if (!session.isOpen()) {

                cleanupSession(
                        entry.getKey()
                );
            }
        }
    }

    private void cleanupSession(
            String sessionId) {

        WebSocketSession session =
                sessions.remove(sessionId);

        lastHeartbeat.remove(
                sessionId
        );

        sessionLocks.remove(
                sessionId
        );

        messageQueues.remove(
                sessionId
        );

        if (session == null) {
            return;
        }

        String deviceId =
                getDeviceId(session);

        if (deviceId == null) {
            return;
        }

        String mappedSession =
                deviceToSessionMap.get(
                        deviceId
                );

        if (sessionId.equals(mappedSession)) {

            deviceToSessionMap.remove(
                    deviceId,
                    sessionId
            );

            DeviceInfo device =
                    deviceRegistry.get(
                            deviceId
                    );

            if (device != null) {
                device.sessionId = null;
            }

            PersistentSessionData data =
                    persistentSessions.get(
                            deviceId
                    );

            if (data != null) {
                data.shouldBeConnected = true;
                data.lastSeen =
                        System.currentTimeMillis();
            }
        }
    }

    private String getDeviceId(
            WebSocketSession session) {

        Object deviceId =
                session.getAttributes()
                        .get("deviceId");

        return deviceId != null
                ? deviceId.toString()
                : null;
    }

    public void registerPersistentDevice(
            String deviceId,
            String location,
            String cropType,
            String deviceType,
            String ipAddress,
            int signalStrength) {

        if (deviceId == null
                || deviceId.isBlank()) {
            return;
        }

        PersistentSessionData data =
                persistentSessions.computeIfAbsent(
                        deviceId,
                        PersistentSessionData::new
                );

        data.location = location;
        data.cropType = cropType;
        data.deviceType = deviceType;
        data.ipAddress = ipAddress;
        data.signalStrength = signalStrength;
        data.shouldBeConnected = true;
        data.lastSeen =
                System.currentTimeMillis();
        data.lastHeartbeat =
                System.currentTimeMillis();
    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            CloseStatus status) {

        String sessionId =
                session.getId();

        String deviceId =
                getDeviceId(session);

        cleanupSession(
                sessionId
        );

        if (deviceId != null) {

            PersistentSessionData data =
                    persistentSessions.get(
                            deviceId
                    );

            if (data != null) {

                data.shouldBeConnected = true;
                data.lastSeen =
                        System.currentTimeMillis();
            }
        }

        logger.info(
                "WebSocket closed: session={}, device={}, code={}, reason={}",
                sessionId,
                deviceId,
                status.getCode(),
                status.getReason()
        );
    }

    @Override
    public void handleTransportError(
            WebSocketSession session,
            Throwable exception) {

        String deviceId =
                getDeviceId(session);

        logger.error(
                "WebSocket transport error: session={}, device={}",
                session.getId(),
                deviceId,
                exception
        );

        if (deviceId != null) {

            PersistentSessionData data =
                    persistentSessions.get(
                            deviceId
                    );

            if (data != null) {

                data.shouldBeConnected = true;
                data.lastSeen =
                        System.currentTimeMillis();
            }
        }

        cleanupSession(
                session.getId()
        );

        try {

            if (session.isOpen()) {

                session.close(
                        CloseStatus.SERVER_ERROR
                );
            }

        } catch (IOException e) {

            logger.warn(
                    "Could not close session after transport error",
                    e
            );
        }
    }

    @PreDestroy
    public void destroy() {

        logger.info(
                "Shutting down WebSocket handler"
        );

        scheduler.shutdownNow();
        detectionExecutor.shutdownNow();

        for (WebSocketSession session :
                sessions.values()) {

            try {

                if (session.isOpen()) {

                    session.close(
                            CloseStatus.GOING_AWAY
                    );
                }

            } catch (IOException e) {

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
        messageQueues.clear();

        logger.info(
                "WebSocket handler stopped"
        );
    }

    public static class PersistentSessionData {

        public final String deviceId;

        public String location;
        public String cropType;
        public String deviceType;
        public String ipAddress;

        public int signalStrength;

        public boolean shouldBeConnected;

        public long lastSeen;
        public long lastHeartbeat;
        public long lastActivity;
        public long framesReceived;
        public double lastTemperature;
        public double lastHumidity;

        public PersistentSessionData(
                String deviceId) {

            this.deviceId = deviceId;

            long now =
                    System.currentTimeMillis();

            this.lastSeen = now;
            this.lastHeartbeat = now;
            this.lastActivity = now;
            this.shouldBeConnected = true;
            this.lastTemperature = Double.NaN;
            this.lastHumidity = Double.NaN;
        }
    }

    public static class DeviceInfo {

        public final String deviceId;
        public final String location;
        public final String cropType;
        public final String deviceType;
        public final String ipAddress;
        public final int signalStrength;

        public String sessionId;

        public final long connectedAt;

        public long lastActivity;

        public DeviceInfo(
                String deviceId,
                String location,
                String cropType,
                String deviceType,
                String ipAddress,
                int signalStrength,
                String sessionId) {

            this.deviceId = deviceId;
            this.location = location;
            this.cropType = cropType;
            this.deviceType = deviceType;
            this.ipAddress = ipAddress;
            this.signalStrength = signalStrength;
            this.sessionId = sessionId;

            this.connectedAt =
                    System.currentTimeMillis();

            this.lastActivity =
                    this.connectedAt;
        }

        public void updateActivity() {

            this.lastActivity =
                    System.currentTimeMillis();
        }

        @Override
        public String toString() {

            return String.format(
                    "Device{id='%s', location='%s', crop='%s', signal=%d dBm, uptime=%ds}",
                    deviceId,
                    location,
                    cropType,
                    signalStrength,
                    (
                            System.currentTimeMillis()
                                    - connectedAt
                    ) / 1000
            );
        }
    }
}