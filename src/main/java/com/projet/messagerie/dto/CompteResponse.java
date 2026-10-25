package com.projet.messagerie.dto;

/** Réponse de création de compte : jamais de mot de passe dans une réponse. */
public record CompteResponse(Long id, String username) {
}