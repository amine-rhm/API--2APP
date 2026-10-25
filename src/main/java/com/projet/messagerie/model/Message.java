package com.projet.messagerie.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "message",
    // accélère la lecture d'une conversation (WHERE expediteur/destinataire, ORDER BY id)
    indexes = @Index(name = "idx_message_conversation", columnList = "expediteur_id, destinataire_id, id")
)
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expediteur_id", nullable = false)
    private Long expediteurId;

    @Column(name = "destinataire_id", nullable = false)
    private Long destinataireId;

    @Column(nullable = false, length = 2000)
    private String contenu;

    // horodatage SERVEUR : jamais fourni par le client
    @Column(name = "date_envoi", nullable = false, updatable = false)
    private LocalDateTime dateEnvoi;

    protected Message() {
    }

    public Message(Long expediteurId, Long destinataireId, String contenu) {
        this.expediteurId = expediteurId;
        this.destinataireId = destinataireId;
        this.contenu = contenu;
    }

    @PrePersist
    void avantEnregistrement() {
        this.dateEnvoi = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getExpediteurId() { return expediteurId; }
    public Long getDestinataireId() { return destinataireId; }
    public String getContenu() { return contenu; }
    public LocalDateTime getDateEnvoi() { return dateEnvoi; }
}