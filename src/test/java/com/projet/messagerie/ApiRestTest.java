package com.projet.messagerie;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tests de l'API REST : inscription, connexion, protection par JWT.
 * MockMvc simule les requêtes HTTP sans démarrer de vrai serveur ; la base est H2 (profil « test »).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiRestTest {

    private static final String MOT_DE_PASSE = "motdepasse123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JsonMapper mapper;

    // ---------- Inscription ----------

    @Test
    void inscriptionRenvoie201EtLeCompte() throws Exception {
        String nom = nomUnique();
        mvc.perform(post("/api/comptes").contentType(MediaType.APPLICATION_JSON).content(identifiants(nom, MOT_DE_PASSE)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value(nom))
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.motDePasse").doesNotExist()); // le mot de passe ne ressort jamais
    }

    @Test
    void inscriptionAvecUnNomDejaPrisRenvoie409() throws Exception {
        String nom = nomUnique();
        inscrire(nom);
        mvc.perform(post("/api/comptes").contentType(MediaType.APPLICATION_JSON).content(identifiants(nom, MOT_DE_PASSE)))
            .andExpect(status().isConflict());
    }

    @Test
    void inscriptionAvecUnMotDePasseTropCourtRenvoie400() throws Exception {
        mvc.perform(post("/api/comptes").contentType(MediaType.APPLICATION_JSON).content(identifiants(nomUnique(), "court")))
            .andExpect(status().isBadRequest());
    }

    // ---------- Connexion ----------

    @Test
    void connexionRenvoieUnJeton() throws Exception {
        String nom = nomUnique();
        inscrire(nom);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(identifiants(nom, MOT_DE_PASSE)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isString())
            .andExpect(jsonPath("$.type").value("Bearer"))
            .andExpect(jsonPath("$.utilisateur.username").value(nom));
    }

    @Test
    void connexionAvecUnMauvaisMotDePasseRenvoie401() throws Exception {
        String nom = nomUnique();
        inscrire(nom);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(identifiants(nom, "mauvais-mot-de-passe")))
            .andExpect(status().isUnauthorized());
    }

    // ---------- Protection par JWT ----------

    @Test
    void sansJetonLesRoutesProtegeesRenvoient401() throws Exception {
        mvc.perform(get("/api/utilisateurs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/messages/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void avecUnFauxJetonRenvoie401() throws Exception {
        mvc.perform(get("/api/utilisateurs").header("Authorization", "Bearer faux.jeton.invalide"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void avecUnJetonLaListeDesUtilisateursEstAccessible() throws Exception {
        String nom = nomUnique();
        inscrire(nom);
        String jeton = connecter(nom);
        mvc.perform(get("/api/utilisateurs").header("Authorization", "Bearer " + jeton))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.username == '" + nom + "')].enLigne").value(false)); // pas de WebSocket ouvert
    }

    @Test
    void historiqueAvecUnUtilisateurInconnuRenvoie404() throws Exception {
        String nom = nomUnique();
        inscrire(nom);
        mvc.perform(get("/api/messages/999999").header("Authorization", "Bearer " + connecter(nom)))
            .andExpect(status().isNotFound());
    }

    // ---------- webhook ----------

    @Test
    void leServiceTiersSimuleAccepteUnWebhookSansJeton() throws Exception {
        mvc.perform(post("/simulation/webhook").contentType(MediaType.APPLICATION_JSON)
                .content("{\"destinataire\":\"bob\",\"expediteur\":\"alice\",\"apercu\":\"Salut\"}"))
            .andExpect(status().isAccepted());
    }




    // ---------- Outils ----------

    private void inscrire(String nom) throws Exception {
        mvc.perform(post("/api/comptes").contentType(MediaType.APPLICATION_JSON).content(identifiants(nom, MOT_DE_PASSE)))
            .andExpect(status().isCreated());
    }

    private String connecter(String nom) throws Exception {
        String reponse = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(identifiants(nom, MOT_DE_PASSE)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return mapper.readTree(reponse).get("token").asString();
    }

    private String identifiants(String nom, String motDePasse) {
        return "{\"username\":\"" + nom + "\",\"motDePasse\":\"" + motDePasse + "\"}";
    }

    /** Un nom différent à chaque test : les tests partagent la même base H2 et ne doivent pas se gêner. */
    private static String nomUnique() {
        return "u_" + UUID.randomUUID().toString().substring(0, 8);
    }
}