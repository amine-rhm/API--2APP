package com.projet.messagerie.service;

import com.projet.messagerie.dto.UtilisateurDto;
import com.projet.messagerie.repository.UtilisateurRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class UtilisateurService {

    private final UtilisateurRepository utilisateurs;

    public UtilisateurService(UtilisateurRepository utilisateurs) {
        this.utilisateurs = utilisateurs;
    }

    public List<UtilisateurDto> lister() {
        return utilisateurs.findAll(Sort.by("username")).stream()
            .map(u -> new UtilisateurDto(u.getId(), u.getUsername(), false)) // enLigne : étape 4
            .toList();
    }
}