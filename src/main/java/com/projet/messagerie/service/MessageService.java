package com.projet.messagerie.service;

import com.projet.messagerie.dto.MessageDto;
import com.projet.messagerie.exception.UtilisateurIntrouvableException;
import com.projet.messagerie.model.Message;
import com.projet.messagerie.repository.MessageRepository;
import com.projet.messagerie.repository.UtilisateurRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class MessageService {

    private final MessageRepository messages;
    private final UtilisateurRepository utilisateurs;

    public MessageService(MessageRepository messages, UtilisateurRepository utilisateurs) {
        this.messages = messages;
        this.utilisateurs = utilisateurs;
    }

    /** Conversation entre moiId et autreId, du plus ancien au plus récent (les "limite" derniers messages avant "avant"). */
    public List<MessageDto> historique(Long moiId, Long autreId, int limite, Long avant) {
        if (!utilisateurs.existsById(autreId)) {
            throw new UtilisateurIntrouvableException(autreId);
        }
        List<Message> recents = new ArrayList<>(
            messages.findConversation(moiId, autreId, avant, PageRequest.of(0, limite)));
        Collections.reverse(recents); // la requête renvoie du plus récent au plus ancien
        return recents.stream().map(MessageDto::de).toList();
    }
}