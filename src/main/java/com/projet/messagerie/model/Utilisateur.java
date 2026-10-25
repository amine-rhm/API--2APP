package com.projet.messagerie.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "utilisateur")
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String username;

    // jamais le mot de passe en clair : on stockera ici le hachage BCrypt (60 caractères)
    @Column(name = "mot_de_passe", nullable = false, length = 100)
    private String motDePasse;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    protected Utilisateur() {
        // constructeur vide exigé par JPA
    }

    public Utilisateur(String username, String motDePasse) {
        this.username = username;
        this.motDePasse = motDePasse;
    }

    @PrePersist
    void avantEnregistrement() {
        this.dateCreation = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getMotDePasse() { return motDePasse; }
    public LocalDateTime getDateCreation() { return dateCreation; }
}