package com.projet.messagerie.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Corps de POST /api/comptes. */
public record CompteRequest(

    @NotBlank(message = "username obligatoire")
    @Size(min = 3, max = 30, message = "username : 3 à 30 caractères")
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "username : lettres, chiffres et _ uniquement")
    String username,

    @NotBlank(message = "motDePasse obligatoire")
    @Size(min = 8, max = 72, message = "motDePasse : 8 à 72 caractères")
    String motDePasse
) {
}