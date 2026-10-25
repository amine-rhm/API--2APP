package com.projet.messagerie.exception;

public class UtilisateurIntrouvableException extends RuntimeException {

    public UtilisateurIntrouvableException(Long id) {
        super("Utilisateur introuvable : " + id);
    }
}