package com.projet.messagerie.repository;

import com.projet.messagerie.model.Message;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * Messages échangés entre a et b (dans les deux sens), plus anciens que l'id "avant",
     * du plus récent au plus ancien. Pageable limite le nombre de résultats.
     */
    @Query("""
        SELECT m FROM Message m
        WHERE ((m.expediteurId = :a AND m.destinataireId = :b)
            OR (m.expediteurId = :b AND m.destinataireId = :a))
          AND m.id < :avant
        ORDER BY m.id DESC
        """)
    List<Message> findConversation(@Param("a") Long a, @Param("b") Long b,
                                   @Param("avant") Long avant, Pageable page);
}