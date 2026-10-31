package com.projet.messagerie.service;

import com.projet.messagerie.dto.UtilisateurDto;
import com.projet.messagerie.repository.UtilisateurRepository;
import com.projet.messagerie.websocket.SessionRegistry;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class UtilisateurService {

    private final UtilisateurRepository utilisateurs;
    private final SessionRegistry registre;

    public UtilisateurService(UtilisateurRepository utilisateurs, SessionRegistry registre) {
        this.utilisateurs = utilisateurs;
        this.registre = registre;
    }

    public List<UtilisateurDto> lister() {
        return utilisateurs.findAll(Sort.by("username")).stream()
            // en ligne = au moins une connexion WebSocket ouverte
            .map(u -> new UtilisateurDto(u.getId(), u.getUsername(), registre.estEnLigne(u.getId())))
            .toList();
    }
}