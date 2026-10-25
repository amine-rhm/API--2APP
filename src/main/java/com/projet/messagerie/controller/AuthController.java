package com.projet.messagerie.controller;

import com.projet.messagerie.dto.CompteRequest;
import com.projet.messagerie.dto.CompteResponse;
import com.projet.messagerie.model.Utilisateur;
import com.projet.messagerie.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Crée un compte : 201, 400 si invalide, 409 si le nom est pris. */
    @PostMapping("/comptes")
    @ResponseStatus(HttpStatus.CREATED)
    public CompteResponse creerCompte(@Valid @RequestBody CompteRequest requete) {
        Utilisateur u = authService.creerCompte(requete.username(), requete.motDePasse());
        return new CompteResponse(u.getId(), u.getUsername());
    }
}