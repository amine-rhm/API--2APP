package com.projet.messagerie.controller;

import com.projet.messagerie.dto.MessageDto;
import com.projet.messagerie.security.AuthenticatedUser;
import com.projet.messagerie.service.MessageService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private static final int LIMITE_MAX = 200;

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * Historique entre l'utilisateur connecté et {utilisateurId}.
     * « Qui je suis » vient du JWT (posé par JwtAuthFilter) : on ne peut lire que ses propres conversations.
     */
    @GetMapping("/{utilisateurId}")
    public List<MessageDto> historique(
        @PathVariable Long utilisateurId,
        @AuthenticationPrincipal AuthenticatedUser moi,
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam(required = false) Long avant
    ) {
        int limite = Math.max(1, Math.min(limit, LIMITE_MAX));
        return messageService.historique(moi.id(), utilisateurId, limite, avant != null ? avant : Long.MAX_VALUE);
    }
}