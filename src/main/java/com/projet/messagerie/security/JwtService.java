package com.projet.messagerie.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Génère et valide les jetons JWT signés en HS256. Contenu : sub = id utilisateur, username. */
@Service
public class JwtService {

    private final SecretKey cle;
    private final long expirationMinutes;

    public JwtService(
        @Value("${app.jwt.secret}") String secret,
        @Value("${app.jwt.expiration-minutes}") long expirationMinutes
    ) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET doit faire au moins 32 caractères");
        }
        this.cle = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    public String generer(Long userId, String username) {
        Date maintenant = new Date();
        Date expiration = new Date(maintenant.getTime() + expirationMinutes * 60_000L);
        return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("username", username)
            .issuedAt(maintenant)
            .expiration(expiration)
            .signWith(cle)
            .compact();
    }

    /** L'utilisateur si le jeton est valide (signature correcte, pas expiré), sinon vide. */
    public Optional<AuthenticatedUser> valider(String jeton) {
        if (jeton == null || jeton.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser().verifyWith(cle).build().parseSignedClaims(jeton).getPayload();
            Long id = Long.valueOf(claims.getSubject());
            String username = claims.get("username", String.class);
            return username == null ? Optional.empty() : Optional.of(new AuthenticatedUser(id, username));
        } catch (JwtException | IllegalArgumentException e) {
            // signature fausse, jeton expiré ou mal formé
            return Optional.empty();
        }
    }

    public long getExpirationSecondes() {
        return expirationMinutes * 60;
    }
}