package com.projet.messagerie.exception;

import com.projet.messagerie.dto.ErreurDto;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Transforme chaque exception en réponse JSON avec le bon code HTTP. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** @Valid a échoué (username trop court, mot de passe vide...) → 400 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErreurDto> validation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(erreur -> erreur.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return reponse(HttpStatus.BAD_REQUEST, message);
    }

    /** Corps de requête absent ou JSON mal formé → 400 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErreurDto> jsonIllisible(HttpMessageNotReadableException e) {
        return reponse(HttpStatus.BAD_REQUEST, "Corps JSON absent ou mal formé");
    }

    /** En-tête obligatoire absent (X-Utilisateur-Id, provisoire jusqu'au JWT) → 400 */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErreurDto> enteteManquant(MissingRequestHeaderException e) {
        return reponse(HttpStatus.BAD_REQUEST, "En-tête manquant : " + e.getHeaderName());
    }

    @ExceptionHandler(NomDejaPrisException.class)
    public ResponseEntity<ErreurDto> nomDejaPris(NomDejaPrisException e) {
        return reponse(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(UtilisateurIntrouvableException.class)
    public ResponseEntity<ErreurDto> introuvable(UtilisateurIntrouvableException e) {
        return reponse(HttpStatus.NOT_FOUND, e.getMessage());
    }

    private ResponseEntity<ErreurDto> reponse(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ErreurDto.de(status, message));
    }
}