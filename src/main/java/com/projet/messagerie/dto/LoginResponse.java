package com.projet.messagerie.dto;

/** Réponse de POST /api/auth/login : le jeton, sa durée de vie et l'utilisateur connecté. */
public record LoginResponse(String token, String type, long expiresIn, CompteResponse utilisateur) {

    public static LoginResponse bearer(String token, long expiresInSecondes, CompteResponse utilisateur) {
        return new LoginResponse(token, "Bearer", expiresInSecondes, utilisateur);
    }
}