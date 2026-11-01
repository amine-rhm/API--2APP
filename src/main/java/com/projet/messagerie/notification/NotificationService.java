package com.projet.messagerie.notification;

import com.projet.messagerie.dto.MessageDto;
import com.projet.messagerie.model.Utilisateur;
import com.projet.messagerie.repository.UtilisateurRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Bonus « notification hors ligne » : quand le destinataire n'est pas connecté, on prévient un service tiers
 * par un WEBHOOK, c'est-à-dire une requête HTTP POST avec un JSON décrivant l'événement.
 * Dans une vraie appli, ce service tiers enverrait la notification push sur le téléphone (Firebase, Apple...).
 * L'envoi est asynchrone : le WebSocket n'attend jamais la réponse du service tiers.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int APERCU_MAX = 60;

    private final UtilisateurRepository utilisateurs;
    private final JsonMapper mapper;
    private final String url;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public NotificationService(UtilisateurRepository utilisateurs, JsonMapper mapper,
                               @Value("${app.webhook.url:}") String url) {
        this.utilisateurs = utilisateurs;
        this.mapper = mapper;
        this.url = url;
    }

    public void messageHorsLigne(MessageDto message) {
        String destinataire = nom(message.destinataireId());
        if (url.isBlank()) {
            log.info("Webhook désactivé : pas de notification pour {}", destinataire);
            return;
        }

        ObjectNode evenement = mapper.createObjectNode();
        evenement.put("evenement", "message.hors_ligne");
        evenement.put("destinataire", destinataire);
        evenement.put("expediteur", nom(message.expediteurId()));
        evenement.put("messageId", message.id());
        evenement.put("apercu", apercu(message.contenu()));
        evenement.put("dateEnvoi", message.dateEnvoi().toString());

        HttpRequest requete = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(3))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(evenement.toString()))
            .build();

        http.sendAsync(requete, HttpResponse.BodyHandlers.discarding()).whenComplete((reponse, erreur) -> {
            if (erreur != null) {
                // le service tiers est en panne : le message reste en base, seule la notification est perdue
                log.warn("Webhook injoignable ({}) : notification pour {} non envoyée", url, destinataire);
            } else {
                log.info("Webhook envoyé pour {} → réponse {}", destinataire, reponse.statusCode());
            }
        });
    }

    private String nom(Long id) {
        return utilisateurs.findById(id).map(Utilisateur::getUsername).orElse("#" + id);
    }

    private static String apercu(String contenu) {
        return contenu.length() <= APERCU_MAX ? contenu : contenu.substring(0, APERCU_MAX) + "…";
    }
}