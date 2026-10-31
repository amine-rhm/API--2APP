package com.projet.messagerie.security;

/** Identité extraite d'un JWT valide : la seule source fiable pour savoir « qui fait la requête ». */
public record AuthenticatedUser(Long id, String username) {
}