package com.projet.messagerie.exception;

public class NomDejaPrisException extends RuntimeException {

    public NomDejaPrisException(String username) {
        super("Le nom d'utilisateur « " + username + " » est déjà pris");
    }
}