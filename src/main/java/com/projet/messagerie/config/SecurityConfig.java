package com.projet.messagerie.config;

import com.projet.messagerie.security.JsonAuthenticationEntryPoint;
import com.projet.messagerie.security.JwtAuthFilter;
import com.projet.messagerie.security.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
                                                   JsonAuthenticationEntryPoint entryPoint) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)            // API à jeton : pas de cookie de session, donc pas de CSRF
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // REST sans état
            .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))                    // 401 en JSON
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.POST, "/api/comptes", "/api/auth/login").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/ws/**").permitAll()        // le WebSocket vérifie le jeton lui-même (JwtHandshakeInterceptor)
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll() // la doc
                .anyRequest().authenticated())                 // tout le reste exige un jeton valide
            .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}