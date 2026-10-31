package com.projet.messagerie.service;

import com.projet.messagerie.dto.MessageDto;
import com.projet.messagerie.exception.MessageInvalideException;
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

    private static final int LONGUEUR_MAX = 2000;

    private final MessageRepository messages;
    private final UtilisateurRepository utilisateurs;

    public MessageService(MessageRepository messages, UtilisateurRepository utilisateurs) {
        this.messages = messages;
        this.utilisateurs = utilisateurs;
    }

    /**
     * Valide puis enregistre un message. L'expéditeur vient toujours du JWT (jamais du client),
     * la date est posée par le serveur. Le message est enregistré même si le destinataire est hors ligne.
     */
    public MessageDto enregistrer(Long expediteurId, Long destinataireId, String contenu) {
        if (contenu == null || contenu.isBlank()) {
            throw new MessageInvalideException("CONTENU_VIDE", "Le message est vide");
        }
        if (contenu.length() > LONGUEUR_MAX) {
            throw new MessageInvalideException("CONTENU_TROP_LONG", "Le message dépasse " + LONGUEUR_MAX + " caractères");
        }
        if (destinataireId == null) {
            throw new MessageInvalideException("DESTINATAIRE_MANQUANT", "destinataireId est obligatoire");
        }
        if (destinataireId.equals(expediteurId)) {
            throw new MessageInvalideException("DESTINATAIRE_INVALIDE", "Impossible de s'envoyer un message");
        }
        if (!utilisateurs.existsById(destinataireId)) {
            throw new MessageInvalideException("DESTINATAIRE_INCONNU", "Destinataire inconnu : " + destinataireId);
        }
        return MessageDto.de(messages.save(new Message(expediteurId, destinataireId, contenu)));
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