package com.projet.messagerie.websocket;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Registre des connexions WebSocket ouvertes : userId → (idSession → session).
 * Un utilisateur est « en ligne » s'il a au moins une connexion ouverte.
 * Il peut en avoir plusieurs (deux onglets, un téléphone et un ordinateur) : il reçoit les messages sur toutes.
 */
@Component
public class SessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(SessionRegistry.class);

    // ConcurrentHashMap : plusieurs utilisateurs se connectent en même temps, sur des threads différents
    private final Map<Long, Map<String, WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void ajouter(Long userId, WebSocketSession session) {
        sessions.computeIfAbsent(userId, id -> new ConcurrentHashMap<>()).put(session.getId(), session);
    }

    public void retirer(Long userId, String sessionId) {
        sessions.computeIfPresent(userId, (id, parSession) -> {
            parSession.remove(sessionId);
            return parSession.isEmpty() ? null : parSession; // null supprime l'entrée : l'utilisateur passe hors ligne
        });
    }

    public boolean estEnLigne(Long userId) {
        Map<String, WebSocketSession> parSession = sessions.get(userId);
        return parSession != null && !parSession.isEmpty();
    }

    /** Envoie le JSON à toutes les connexions de l'utilisateur. Sans effet s'il est hors ligne. */
    public void envoyerA(Long userId, String json) {
        Map<String, WebSocketSession> parSession = sessions.get(userId);
        if (parSession == null) {
            return;
        }
        for (WebSocketSession session : List.copyOf(parSession.values())) {
            envoyer(session, json);
        }
    }

    /** Envoie le JSON à une seule connexion (pour répondre une erreur à l'onglet fautif). */
    public void envoyerSession(Long userId, String sessionId, String json) {
        Map<String, WebSocketSession> parSession = sessions.get(userId);
        WebSocketSession session = parSession == null ? null : parSession.get(sessionId);
        if (session != null) {
            envoyer(session, json);
        }
    }

    private void envoyer(WebSocketSession session, String json) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(json));
        } catch (IOException | RuntimeException e) {
            log.warn("Envoi WebSocket impossible vers la session {} : {}", session.getId(), e.getMessage());
        }
    }
}