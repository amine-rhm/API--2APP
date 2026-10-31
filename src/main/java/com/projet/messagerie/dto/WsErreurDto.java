package com.projet.messagerie.dto;

/** Erreur renvoyée sur le canal WebSocket : {"type":"erreur","code":"...","message":"..."}. */
public record WsErreurDto(String type, String code, String message) {

    public static WsErreurDto de(String code, String message) {
        return new WsErreurDto("erreur", code, message);
    }
}