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
        registry.addHandler(cameraWebSocketHandler, "/camera-stream")
                .setAllowedOrigins("*")  // ✅ Allow all origins
                .setAllowedOriginPatterns("*");  // ✅ For newer browsers
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(1048576);
        container.setMaxBinaryMessageBufferSize(1048576);
        container.setMaxSessionIdleTimeout(600000L);
        container.setAsyncSendTimeout(60000L);
        return container;
    }
}