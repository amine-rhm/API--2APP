package com.projet.messagerie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.projet.messagerie.notification.NotificationService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tests du canal temps réel /ws/messages, avec un VRAI serveur (port aléatoire)
 * et de vrais clients WebSocket (java.net.http, inclus dans Java : aucune dépendance en plus).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketTest {

    private static final Duration DELAI = Duration.ofSeconds(5);

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper mapper;

    @MockitoSpyBean // le vrai service, mais Mockito note chaque appel pour qu'on puisse le vérifier
    private NotificationService notifications;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void connexionSansJetonRefuseeAvec401() {
        assertThat(statutDuRefus(null)).isEqualTo(401);
    }

    @Test
    void connexionAvecUnFauxJetonRefuseeAvec401() {
        assertThat(statutDuRefus("faux.jeton.invalide")).isEqualTo(401);
    }

    @Test
    void unMessageArriveChezLeDestinataireEtEstEnregistre() throws Exception {
        Compte alice = creerCompte();
        Compte bob = creerCompte();
        Client wsAlice = ouvrir(alice.jeton());
        Client wsBob = ouvrir(bob.jeton());

        // alice essaie de se faire passer pour quelqu'un d'autre avec "expediteurId": 999
        wsAlice.envoyer("{\"type\":\"message\",\"destinataireId\":" + bob.id() + ",\"expediteurId\":999,\"contenu\":\"Salut Bob\"}");

        // 1. bob le reçoit en direct
        JsonNode recu = wsBob.prochain();
        assertThat(recu.get("type").asString()).isEqualTo("message");
        assertThat(recu.get("contenu").asString()).isEqualTo("Salut Bob");
        assertThat(recu.get("expediteurId").asLong()).isEqualTo(alice.id()); // pris dans le jeton, pas dans le JSON
        assertThat(recu.get("id").isNumber()).isTrue();
        assertThat(recu.get("dateEnvoi").isString()).isTrue();               // date posée par le serveur

        // 2. alice reçoit la confirmation (le même message, avec son id)
        assertThat(wsAlice.prochain().get("id").asLong()).isEqualTo(recu.get("id").asLong());

        // 3. il est en base : bob le retrouve dans son historique REST
        JsonNode historique = getJson("/api/messages/" + alice.id(), bob.jeton());
        assertThat(historique.size()).isEqualTo(1);
        assertThat(historique.get(0).get("contenu").asString()).isEqualTo("Salut Bob");

        // 4. bob était en ligne : pas de webhook
        verify(notifications, never()).messageHorsLigne(any());

        wsAlice.fermer();
        wsBob.fermer();
    }

    @Test
    void unMessageVersUnDestinataireHorsLigneEstQuandMemeEnregistre() throws Exception {
        Compte alice = creerCompte();
        Compte bob = creerCompte(); // bob n'ouvre pas de WebSocket
        Client wsAlice = ouvrir(alice.jeton());

        wsAlice.envoyer("{\"type\":\"message\",\"destinataireId\":" + bob.id() + ",\"contenu\":\"Tu liras plus tard\"}");
        wsAlice.prochain(); // la confirmation

        JsonNode historique = getJson("/api/messages/" + alice.id(), bob.jeton());
        assertThat(historique.get(0).get("contenu").asString()).isEqualTo("Tu liras plus tard");

        // et le webhook de notification a été déclenché pour bob
        verify(notifications, timeout(2000)).messageHorsLigne(argThat(m -> m.destinataireId() == bob.id()));
        wsAlice.fermer();
    }

    @Test
    void unMessageInvalideRenvoieUneErreurALExpediteur() throws Exception {
        Compte alice = creerCompte();
        Compte bob = creerCompte();
        Client wsAlice = ouvrir(alice.jeton());

        wsAlice.envoyer("{\"type\":\"message\",\"destinataireId\":" + bob.id() + ",\"contenu\":\"   \"}");
        assertThat(wsAlice.prochain().get("code").asString()).isEqualTo("CONTENU_VIDE");

        wsAlice.envoyer("{\"type\":\"message\",\"destinataireId\":999999,\"contenu\":\"x\"}");
        assertThat(wsAlice.prochain().get("code").asString()).isEqualTo("DESTINATAIRE_INCONNU");

        wsAlice.envoyer("ceci n'est pas du JSON");
        assertThat(wsAlice.prochain().get("code").asString()).isEqualTo("JSON_INVALIDE");

        // rien n'a été enregistré
        assertThat(getJson("/api/messages/" + bob.id(), alice.jeton()).size()).isZero();
        wsAlice.fermer();
    }

    @Test
    void laPresenceSuitLaConnexionWebSocket() throws Exception {
        Compte alice = creerCompte();
        Compte bob = creerCompte();
        assertThat(enLigne(bob, alice.jeton())).isFalse();

        Client wsBob = ouvrir(bob.jeton());
        attendrePresence(bob, alice.jeton(), true);

        wsBob.fermer();
        attendrePresence(bob, alice.jeton(), false);
    }

    @Test
    void lIndicateurDeSaisieEstRelayeAuDestinataire() throws Exception {
        Compte alice = creerCompte();
        Compte bob = creerCompte();
        Client wsAlice = ouvrir(alice.jeton());
        Client wsBob = ouvrir(bob.jeton());

        wsAlice.envoyer("{\"type\":\"saisie\",\"destinataireId\":" + bob.id() + "}");
        JsonNode evenement = wsBob.prochain();
        assertThat(evenement.get("type").asString()).isEqualTo("saisie");
        assertThat(evenement.get("expediteurId").asLong()).isEqualTo(alice.id());

        wsAlice.fermer();
        wsBob.fermer();
    }

    // ---------- Outils ----------

    private record Compte(long id, String jeton) {
    }

    /** Inscrit un utilisateur au nom unique et le connecte. */
    private Compte creerCompte() throws Exception {
        String nom = "u_" + UUID.randomUUID().toString().substring(0, 8);
        String identifiants = "{\"username\":\"" + nom + "\",\"motDePasse\":\"motdepasse123\"}";
        JsonNode compte = postJson("/api/comptes", identifiants);
        JsonNode login = postJson("/api/auth/login", identifiants);
        return new Compte(compte.get("id").asLong(), login.get("token").asString());
    }

    private boolean enLigne(Compte qui, String jeton) throws Exception {
        for (JsonNode u : getJson("/api/utilisateurs", jeton)) {
            if (u.get("id").asLong() == qui.id()) {
                return u.get("enLigne").asBoolean();
            }
        }
        throw new AssertionError("utilisateur absent de la liste");
    }

    /** Ouverture et fermeture sont traitées par le serveur sur un autre thread : on lui laisse jusqu'à 5 s. */
    private void attendrePresence(Compte qui, String jeton, boolean attendu) throws Exception {
        long limite = System.currentTimeMillis() + DELAI.toMillis();
        while (enLigne(qui, jeton) != attendu) {
            if (System.currentTimeMillis() > limite) {
                throw new AssertionError("enLigne aurait dû passer à " + attendu);
            }
            Thread.sleep(50);
        }
    }

    private JsonNode postJson(String chemin, String corps) throws Exception {
        HttpRequest requete = HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(corps))
            .build();
        return mapper.readTree(http.send(requete, HttpResponse.BodyHandlers.ofString()).body());
    }

    private JsonNode getJson(String chemin, String jeton) throws Exception {
        HttpRequest requete = HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin))
            .header("Authorization", "Bearer " + jeton)
            .build();
        return mapper.readTree(http.send(requete, HttpResponse.BodyHandlers.ofString()).body());
    }

    /** Tente une connexion qui doit être refusée, et renvoie le code HTTP de la réponse au handshake. */
    private int statutDuRefus(String jeton) {
        try {
            ouvrir(jeton);
        } catch (Exception e) {
            for (Throwable t = e; t != null; t = t.getCause()) {
                if (t instanceof WebSocketHandshakeException refus) {
                    return refus.getResponse().statusCode();
                }
            }
        }
        throw new AssertionError("la connexion aurait dû être refusée");
    }

    private Client ouvrir(String jeton) throws Exception {
        String url = "ws://localhost:" + port + "/ws/messages" + (jeton == null ? "" : "?token=" + jeton);
        Client client = new Client();
        client.ws = http.newWebSocketBuilder().buildAsync(URI.create(url), client).get(5, TimeUnit.SECONDS);
        return client;
    }

    /** Un client WebSocket de test : il range chaque message reçu dans une file d'attente. */
    private class Client implements WebSocket.Listener {

        private final BlockingQueue<String> recus = new LinkedBlockingQueue<>();
        private final StringBuilder morceaux = new StringBuilder();
        private WebSocket ws;

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence texte, boolean dernier) {
            morceaux.append(texte); // un long message peut arriver en plusieurs morceaux
            if (dernier) {
                recus.add(morceaux.toString());
                morceaux.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        void envoyer(String texte) {
            ws.sendText(texte, true).join();
        }

        /** Attend le prochain message (5 s maximum). */
        JsonNode prochain() throws Exception {
            String texte = recus.poll(DELAI.toMillis(), TimeUnit.MILLISECONDS);
            assertThat(texte).as("aucun message reçu en " + DELAI.toSeconds() + " s").isNotNull();
            return mapper.readTree(texte);
        }

        void fermer() {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "fin du test").join();
        }
    }
}