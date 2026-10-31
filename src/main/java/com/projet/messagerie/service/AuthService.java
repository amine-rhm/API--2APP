package com.projet.messagerie.service;

import com.projet.messagerie.dto.CompteResponse;
import com.projet.messagerie.dto.LoginResponse;
import com.projet.messagerie.exception.IdentifiantsInvalidesException;
import com.projet.messagerie.exception.NomDejaPrisException;
import com.projet.messagerie.model.Utilisateur;
import com.projet.messagerie.repository.UtilisateurRepository;
import com.projet.messagerie.security.JwtService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UtilisateurRepository utilisateurs;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;

    public AuthService(UtilisateurRepository utilisateurs, PasswordEncoder encoder, JwtService jwtService) {
        this.utilisateurs = utilisateurs;
        this.encoder = encoder;
        this.jwtService = jwtService;
    }

    public Utilisateur creerCompte(String username, String motDePasse) {
        if (utilisateurs.existsByUsername(username)) {
            throw new NomDejaPrisException(username);
        }
        try {
            // on enregistre le HACHAGE BCrypt, jamais le mot de passe en clair
            return utilisateurs.saveAndFlush(new Utilisateur(username, encoder.encode(motDePasse)));
        } catch (DataIntegrityViolationException e) {
            // deux inscriptions simultanées avec le même nom : la contrainte UNIQUE de la base tranche
            throw new NomDejaPrisException(username);
        }
    }

    public LoginResponse login(String username, String motDePasse) {
        Utilisateur utilisateur = utilisateurs.findByUsername(username)
            .orElseThrow(IdentifiantsInvalidesException::new);
        // on ne « déchiffre » pas le hachage : on vérifie que le mot de passe tapé lui correspond
        if (!encoder.matches(motDePasse, utilisateur.getMotDePasse())) {
            throw new IdentifiantsInvalidesException();
        }
        String token = jwtService.generer(utilisateur.getId(), utilisateur.getUsername());
        return LoginResponse.bearer(token, jwtService.getExpirationSecondes(),
            new CompteResponse(utilisateur.getId(), utilisateur.getUsername()));
    }
}