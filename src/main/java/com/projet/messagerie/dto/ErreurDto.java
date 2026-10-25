package com.projet.messagerie.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;

/** Format d'erreur JSON identique pour toute l'API. */
public record ErreurDto(
    int statut,
    String erreur,
    String message,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime horodatage
) {

    public static ErreurDto de(HttpStatus status, String message) {
        return new ErreurDto(status.value(), status.getReasonPhrase(), message, LocalDateTime.now());
    }
}