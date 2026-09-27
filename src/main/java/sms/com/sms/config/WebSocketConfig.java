package sms.com.sms.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
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
                // Matches BOTH:
                //   /camera-stream                          (legacy ESP32 firmware)
                //   /camera-stream/{mac}                    (new firmware with MAC)
                //   /camera-stream/3C:71:BF:12:AB:CD
                .addHandler(cameraWebSocketHandler,
                        "/camera-stream",
                        "/camera-stream/**")
                .setAllowedOrigins("*");
    }

    /**
     * Raise the WebSocket buffers so large JPEG frames from the ESP32-CAM
     * (~120 KB) are not rejected by Tomcat/Jetty before they reach the handler.
     *
     * Default Tomcat binary buffer is only 8 KB.
     */

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container =
                new ServletServerContainerFactoryBean();

        // Must be >= largest binary frame your ESP32 sends
        container.setMaxBinaryMessageBufferSize(512 * 1024);   // 512 KB
        container.setMaxTextMessageBufferSize(64 * 1024);      // 64 KB
        container.setMaxSessionIdleTimeout(600_000L);          // 10 min
        return container;
    }
}