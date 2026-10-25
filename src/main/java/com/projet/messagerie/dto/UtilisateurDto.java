package com.projet.messagerie.dto;

/** Élément de GET /api/utilisateurs. enLigne sera branché sur le WebSocket à l'étape 4. */
public record UtilisateurDto(Long id, String username, boolean enLigne) {
}