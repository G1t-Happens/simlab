package dev.simlab.server.websocket;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/** Maps the WebSocket endpoint. Like every endpoint it only accepts handshakes from the same origin */
@Configuration(proxyBeanMethods = false)
@EnableWebSocket
class WebSocketConfiguration implements WebSocketConfigurer {

    /** inputs are small JSON messages, the container rejects larger ones before they reach the handler */
    private static final int MAX_TEXT_MESSAGE_SIZE = 1024;

    private final RoomWebSocketHandler handler;
    private final RoomHandshakeInterceptor handshakeInterceptor;

    WebSocketConfiguration(RoomWebSocketHandler handler, RoomHandshakeInterceptor handshakeInterceptor) {
        this.handler = handler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/rooms/*").addInterceptors(handshakeInterceptor);
    }

    @Bean
    ServletServerContainerFactoryBean webSocketContainer() {
        var container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(MAX_TEXT_MESSAGE_SIZE);
        return container;
    }
}
