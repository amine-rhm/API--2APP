package com.projet.messagerie.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * Le « service tiers » SIMULÉ qui reçoit les webhooks. En vrai, ce serait un autre serveur
 * (un service de notifications push) : ici il se contente d'écrire dans les logs ce qu'il aurait envoyé.
 */
@RestController
@RequestMapping("/simulation")
public class ServiceTiersSimuleController {

    private static final Logger log = LoggerFactory.getLogger(ServiceTiersSimuleController.class);

    @PostMapping("/webhook")
    @ResponseStatus(HttpStatus.ACCEPTED) // 202 : « bien reçu, je m'en occupe »
    public void recevoir(@RequestBody JsonNode notification) {
        log.info("🔔 [service tiers simulé] notification envoyée à {} : « {} » (de {})",
            notification.path("destinataire").asString(),
            notification.path("apercu").asString(),
            notification.path("expediteur").asString());
    }
}