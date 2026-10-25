package com.projet.messagerie.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.projet.messagerie.model.Message;
import java.time.LocalDateTime;

/**
 * Format JSON d'un message, celui du sujet :
 * {"type":"message","id":1,"expediteurId":1,"destinataireId":2,"contenu":"...","dateEnvoi":"2026-09-21T10:15:30"}
 */
public record MessageDto(
    String type,
    Long id,
    Long expediteurId,
    Long destinataireId,
    String contenu,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime dateEnvoi
) {

    public static MessageDto de(Message m) {
        return new MessageDto("message", m.getId(), m.getExpediteurId(), m.getDestinataireId(),
            m.getContenu(), m.getDateEnvoi());
    }
}