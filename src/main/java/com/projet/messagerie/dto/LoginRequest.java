package com.projet.messagerie.dto;

import jakarta.validation.constraints.NotBlank;

/** Corps de POST /api/auth/login. */
public record LoginRequest(
    @NotBlank(message = "username obligatoire") String username,
    @NotBlank(message = "motDePasse obligatoire") String motDePasse
) {
}