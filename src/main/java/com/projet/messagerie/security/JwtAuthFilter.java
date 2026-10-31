package com.projet.messagerie.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lit « Authorization: Bearer <jwt> » sur chaque requête.
 * Jeton valide → la requête est authentifiée. Sinon on ne fait rien : Spring Security
 * refusera ensuite les routes protégées (401).
 * Pas de @Component : il est créé dans SecurityConfig (sinon Spring l'enregistrerait deux fois).
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String PREFIXE = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String entete = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (entete != null && entete.startsWith(PREFIXE)) {
            jwtService.valider(entete.substring(PREFIXE.length()).trim()).ifPresent(utilisateur -> {
                var authentification = new UsernamePasswordAuthenticationToken(
                    utilisateur, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(authentification);
            });
        }
        chain.doFilter(request, response);
    }
}