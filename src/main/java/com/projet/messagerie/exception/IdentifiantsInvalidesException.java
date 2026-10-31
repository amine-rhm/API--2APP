package com.projet.messagerie.exception;

public class IdentifiantsInvalidesException extends RuntimeException {

    public IdentifiantsInvalidesException() {
        // même message pour « nom inconnu » et « mauvais mot de passe » : on ne révèle pas quels comptes existent
        super("Identifiants incorrects");
    }
}