package com.projet.messagerie.service;

import com.projet.messagerie.exception.NomDejaPrisException;
import com.projet.messagerie.model.Utilisateur;
import com.projet.messagerie.repository.UtilisateurRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UtilisateurRepository utilisateurs;
    private final PasswordEncoder encoder;

    public AuthService(UtilisateurRepository utilisateurs, PasswordEncoder encoder) {
        this.utilisateurs = utilisateurs;
        this.encoder = encoder;
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
}