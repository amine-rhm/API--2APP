package com.projet.messagerie.exception;

/** Message refusé (vide, trop long, destinataire inconnu...). Le code est renvoyé au client sur le WebSocket. */
public class MessageInvalideException extends RuntimeException {

    private final String code;

    public MessageInvalideException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}