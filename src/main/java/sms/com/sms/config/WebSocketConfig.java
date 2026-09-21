package sms.com.sms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final CameraWebSocketHandler cameraWebSocketHandler;

    public WebSocketConfig(CameraWebSocketHandler cameraWebSocketHandler) {
        this.cameraWebSocketHandler = cameraWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
                .addHandler(cameraWebSocketHandler, "/camera-stream")
                .setAllowedOrigins("*");
    }

    /*
     * THIS IS THE CRITICAL PART FOR ESP32 VGA FRAMES.
     *
     * Default Tomcat buffer for binary messages is only 8 KB.
     * A VGA JPEG @ quality 15 can reach ~60-120 KB.
     * Without this bean, every frame throws MessageTooBigException
     * and the session is torn down immediately.
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {

        ServletServerContainerFactoryBean container =
                new ServletServerContainerFactoryBean();

        container.setMaxTextMessageBufferSize(64 * 1024);      // 64 KB
        container.setMaxBinaryMessageBufferSize(512 * 1024);   // 512 KB
        container.setMaxSessionIdleTimeout(300_000L);          // 5 min

        return container;
    }
}