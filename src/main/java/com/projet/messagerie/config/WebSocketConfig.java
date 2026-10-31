package com.projet.messagerie.config;

import com.projet.messagerie.security.JwtHandshakeInterceptor;
import com.projet.messagerie.websocket.MessageWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Déclare le canal temps réel : ws://hôte:8080/ws/messages?token=<jwt>. */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final MessageWebSocketHandler handler;
    private final JwtHandshakeInterceptor interceptor;

    public WebSocketConfig(MessageWebSocketHandler handler, JwtHandshakeInterceptor interceptor) {
        this.handler = handler;
        this.interceptor = interceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/messages")
            .addInterceptors(interceptor)        // vérifie le JWT avant d'accepter la connexion
            .setAllowedOriginPatterns("*");      // pages servies depuis n'importe quelle adresse du réseau local (démo)
    }
}