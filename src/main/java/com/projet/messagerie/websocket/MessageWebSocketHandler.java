package com.projet.messagerie.websocket;

import com.projet.messagerie.dto.MessageDto;
import com.projet.messagerie.dto.WsErreurDto;
import com.projet.messagerie.exception.MessageInvalideException;
import com.projet.messagerie.security.JwtHandshakeInterceptor;
import com.projet.messagerie.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Canal temps réel /ws/messages. Trajet d'un message :
 *   1. l'expéditeur est lu sur la connexion (posé au handshake depuis le JWT), JAMAIS dans le JSON reçu
 *   2. le message est validé et enregistré en base (MessageService)
 *   3. il est retransmis au destinataire s'il a des connexions ouvertes
 *   4. il est renvoyé à l'expéditeur, comme confirmation (avec l'id et la date du serveur)
 * Destinataire hors ligne : l'étape 3 ne fait rien, mais le message est déjà en base (historique).
 */
@Component
public class MessageWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MessageWebSocketHandler.class);

    private static final int DELAI_ENVOI_MS = 10_000;
    private static final int TAMPON_ENVOI_OCTETS = 256 * 1024;

    private final SessionRegistry registre;
    private final MessageService messageService;
    private final JsonMapper mapper;

    public MessageWebSocketHandler(SessionRegistry registre, MessageService messageService, JsonMapper mapper) {
        this.registre = registre;
        this.messageService = messageService;
        this.mapper = mapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = userId(session);
        // une WebSocketSession n'accepte pas deux envois en même temps : le décorateur les met en file
        registre.ajouter(userId, new ConcurrentWebSocketSessionDecorator(session, DELAI_ENVOI_MS, TAMPON_ENVOI_OCTETS));
        log.info("WebSocket ouverte : utilisateur {} (session {})", userId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage texte) {
        Long userId = userId(session);

        JsonNode json;
        try {
            json = mapper.readTree(texte.getPayload());
        } catch (JacksonException e) {
            erreur(userId, session, "JSON_INVALIDE", "Le message n'est pas du JSON valide");
            return;
        }
        if (json == null || !json.isObject()) {
            erreur(userId, session, "JSON_INVALIDE", "Un objet JSON est attendu");
            return;
        }

        try {
            switch (json.path("type").asString("")) {
                case "message" -> traiterMessage(userId, json);
                case "saisie" -> traiterSaisie(userId, json);
                default -> erreur(userId, session, "TYPE_INCONNU", "Type de message inconnu");
            }
        } catch (MessageInvalideException e) {
            erreur(userId, session, e.getCode(), e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus statut) {
        Long userId = userId(session);
        registre.retirer(userId, session.getId());
        log.info("WebSocket fermée : utilisateur {} (session {})", userId, session.getId());
    }

    private void traiterMessage(Long expediteurId, JsonNode json) {
        // un éventuel "expediteurId" ou "dateEnvoi" envoyé par le client est ignoré
        Long destinataireId = lireId(json, "destinataireId");
        String contenu = json.path("contenu").isString() ? json.get("contenu").asString() : null;

        MessageDto message = messageService.enregistrer(expediteurId, destinataireId, contenu);

        String payload = mapper.writeValueAsString(message);
        registre.envoyerA(message.destinataireId(), payload);
        registre.envoyerA(expediteurId, payload);
    }

    /** Bonus « X est en train d'écrire » : rien en base, simplement relayé au destinataire s'il est connecté. */
    private void traiterSaisie(Long expediteurId, JsonNode json) {
        Long destinataireId = lireId(json, "destinataireId");
        if (destinataireId == null || destinataireId.equals(expediteurId)) {
            return;
        }
        ObjectNode evenement = mapper.createObjectNode();
        evenement.put("type", "saisie");
        evenement.put("expediteurId", expediteurId);
        evenement.put("destinataireId", destinataireId);
        registre.envoyerA(destinataireId, evenement.toString());
    }

    private void erreur(Long userId, WebSocketSession session, String code, String message) {
        registre.envoyerSession(userId, session.getId(), mapper.writeValueAsString(WsErreurDto.de(code, message)));
    }

    private Long lireId(JsonNode json, String champ) {
        JsonNode noeud = json.path(champ);
        return noeud.isIntegralNumber() ? noeud.asLong() : null;
    }

    private Long userId(WebSocketSession session) {
        return (Long) session.getAttributes().get(JwtHandshakeInterceptor.ATTR_USER_ID);
    }
}