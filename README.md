# Écho — Messagerie instantanée entre deux appareils

Projet de synthèse du module **API et Web Services** (Master Ingénierie Informatique).

Écho permet à deux utilisateurs, chacun sur **un appareil différent** (un ordinateur et un téléphone par exemple),
de s'envoyer des messages **en temps réel**. Les deux appareils ne se parlent jamais directement :
tout passe par un **serveur Spring Boot**, qui enregistre chaque message dans une base **MySQL**
puis le retransmet au destinataire par **WebSocket**.

| | |
|---|---|
| **Serveur** | Java 17, Spring Boot 4.1.2, Maven |
| **Base de données** | MySQL (H2 en mémoire pour les tests) |
| **Sécurité** | JWT (jjwt 0.12), mots de passe hachés avec BCrypt |
| **Temps réel** | WebSocket natif (`spring-boot-starter-websocket`) |
| **Clients** | 2 pages web : `bureau.html` et `mobile.html` (HTML, CSS, JavaScript sans framework) |
| **Documentation de l'API** | Swagger UI (springdoc 3) |
| **Tests** | JUnit 5, MockMvc, client WebSocket Java : 18 tests |

---

## Sommaire
1. [Prérequis](#1-prérequis)
2. [Préparer la base MySQL](#2-préparer-la-base-mysql-une-seule-fois)
3. [Lancer le serveur](#3-lancer-le-serveur)
4. [Utiliser l'application](#4-utiliser-lapplication)
5. [Faire la démo sur deux appareils](#5-faire-la-démo-sur-deux-appareils)
6. [Lancer les tests](#6-lancer-les-tests)
7. [Architecture](#7-architecture)
8. [API REST](#8-api-rest)
9. [Canal WebSocket](#9-canal-websocket)
10. [Fonctionnalités bonus](#10-fonctionnalités-bonus)
11. [Organisation du code](#11-organisation-du-code)
12. [Problèmes fréquents](#12-problèmes-fréquents)

---

## 1. Prérequis

| Outil | Version utilisée | Vérifier avec |
|---|---|---|
| Java (JDK) | 17 minimum (testé avec 21) | `java -version` |
| Maven | 3.9 | `mvn -v` |
| MySQL | 8 ou plus | `mysql --version` |

> Pas de Maven installé ? Le projet contient le *Maven Wrapper* : remplacez `mvn` par `./mvnw` dans toutes les commandes.

---

## 2. Préparer la base MySQL (une seule fois)

Connectez-vous à MySQL en administrateur :

```bash
mysql -u root -p
```

Puis créez la base et l'utilisateur de l'application :

```sql
CREATE DATABASE messagerie_app CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'messagerie_user'@'localhost' IDENTIFIED BY 'MotDePasse123';
GRANT ALL PRIVILEGES ON messagerie_app.* TO 'messagerie_user'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

Les **tables** (`utilisateur` et `message`) n'ont pas besoin d'être créées à la main :
Hibernate les crée au premier démarrage (`ddl-auto: update`).

---

## 3. Lancer le serveur

Le mot de passe MySQL et la clé secrète des JWT **ne sont pas écrits dans le code**.
On les donne au lancement, par des variables d'environnement :

| Variable | Rôle | Exemple |
|---|---|---|
| `DB_PASSWORD` | mot de passe de `messagerie_user` dans MySQL | `MotDePasse123` |
| `JWT_SECRET` | clé qui signe les jetons JWT (**32 caractères minimum**) | `change-moi-un-secret-de-plus-de-32-caracteres` |

Depuis la racine du projet :

```bash
DB_PASSWORD=MotDePasse123 JWT_SECRET=change-moi-un-secret-de-plus-de-32-caracteres mvn spring-boot:run
```

Le serveur est prêt quand cette ligne apparaît :

```
Started MessagerieApplication in ... seconds
```

Il écoute sur le **port 8080**.

---

## 4. Utiliser l'application

| Adresse | Ce qu'on y trouve |
|---|---|
| http://localhost:8080/ | redirige automatiquement vers la bonne version (téléphone ou ordinateur) |
| http://localhost:8080/bureau.html | client **bureau** : contacts à gauche, conversation à droite |
| http://localhost:8080/mobile.html | client **mobile** : un écran à la fois, avec un bouton retour |
| http://localhost:8080/swagger-ui.html | documentation interactive de l'API REST |

**Premier essai, sur un seul ordinateur :**
1. Ouvrez `bureau.html`, onglet **Inscription**, créez le compte `alice` (mot de passe de 8 caractères minimum).
2. Ouvrez une **fenêtre de navigation privée** (ou un autre navigateur) sur `mobile.html` et créez le compte `bob`.
3. Dans la fenêtre d'alice, cliquez sur `bob` et écrivez : le message apparaît **immédiatement** chez bob.

Ce que vous voyez dans l'interface :
- **point vert** sur l'avatar : la personne est connectée en ce moment ;
- **« écrit… »** et trois points animés : l'autre est en train de taper ;
- **pastille jaune** avec un nombre : messages non lus dans une autre conversation ;
- **bannière en bas de l'écran** : un message vient d'arriver dans une autre conversation (un clic l'ouvre) ;
- bouton **◐** : thème clair ou sombre ; bouton **⏻** : déconnexion.

> Chaque onglet garde sa propre session (`sessionStorage`) : deux onglets peuvent donc être deux utilisateurs différents.

---

## 5. Faire la démo sur deux appareils

Le sujet demande **deux appareils physiquement distincts**. Exemple : le Mac qui fait tourner le serveur, et un téléphone.

1. Mettez le téléphone et l'ordinateur **sur le même réseau** : même Wi-Fi, ou l'ordinateur connecté au partage de connexion du téléphone.
2. Trouvez l'adresse IP de l'ordinateur :
   ```bash
   ipconfig getifaddr en0
   ```
   (sous Windows : `ipconfig`, ligne « Adresse IPv4 »)
3. Lancez le serveur (section 3).
4. **Ordinateur** : ouvrez http://localhost:8080/ et connectez-vous en tant qu'**utilisateur 1**.
5. **Téléphone** : ouvrez `http://<adresse-ip>:8080/`, par exemple `http://172.20.10.2:8080/`, et connectez-vous en tant qu'**utilisateur 2**.
   Tapez bien `http://` (pas `https://`) et `:8080`.

**Scénario du sujet :**

| Étape | Action | Résultat attendu |
|---|---|---|
| 1 | Connexion de A (ordinateur) avec l'utilisateur 1 | la pastille passe à « en direct » |
| 2 | Connexion de B (téléphone) avec l'utilisateur 2 | A voit B passer **en ligne** (point vert) |
| 3 | A envoie un message | il apparaît **tout de suite** sur B, sans rien toucher |
| 4 | B répond | il apparaît **tout de suite** sur A |
| 5 | A se déconnecte (⏻) puis se reconnecte | **tout l'historique** de la conversation est toujours là |

---

## 6. Lancer les tests

```bash
mvn test
```

Les tests utilisent le profil `test` (`src/test/resources/application-test.yml`) : une base **H2 en mémoire**,
créée au début et détruite à la fin. **Ils n'ont besoin ni de MySQL ni des variables d'environnement.**

Résultat attendu :

```
Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

| Classe de test | Ce qu'elle vérifie |
|---|---|
| `MessagerieApplicationTests` | l'application démarre |
| `ApiRestTest` (10 tests) | inscription (201), nom déjà pris (409), mot de passe trop court (400), connexion (jeton renvoyé), mauvais mot de passe (401), routes refusées sans jeton ou avec un faux jeton (401), liste des utilisateurs, historique d'un utilisateur inconnu (404), réception d'un webhook |
| `WebSocketTest` (7 tests) | connexion WebSocket refusée sans jeton ou avec un faux jeton (401), message reçu en direct **et** enregistré en base, expéditeur imposé par le jeton (impossible d'usurper un autre utilisateur), message vers un destinataire hors ligne enregistré quand même **et** webhook déclenché, erreurs (message vide, destinataire inconnu, JSON invalide), présence en ligne / hors ligne, indicateur de saisie |

---

## 7. Architecture

```
   Appareil A (ordinateur)                       Appareil B (téléphone)
      bureau.html                                   mobile.html
          │                                              │
          │   REST (HTTP + JSON)    : compte, connexion, │
          │                           utilisateurs,      │
          │                           historique         │
          │   WebSocket (/ws/messages) : messages en     │
          │                              temps réel      │
          └──────────────────────┬───────────────────────┘
                                 │
                      ┌──────────┴──────────┐
                      │ SERVEUR Spring Boot │──── webhook (POST) ───► service de
                      │  REST + WebSocket   │     si destinataire      notifications
                      │  + sécurité JWT     │     hors ligne           (simulé)
                      └──────────┬──────────┘
                                 │ JPA / Hibernate
                      ┌──────────┴──────────┐
                      │   MySQL             │
                      │   utilisateur       │
                      │   message           │
                      └─────────────────────┘
```

**Répartition REST / WebSocket, comme demandé par le sujet :**

| Opération | Technologie | Pourquoi |
|---|---|---|
| Créer un compte, se connecter | REST `POST` | opération ponctuelle : une requête, une réponse |
| Lire l'historique | REST `GET` | ressource identifiable par une URL, peut être mise en cache |
| Liste des utilisateurs et présence | REST `GET` | consultation ponctuelle d'un état |
| Envoyer et recevoir des messages | WebSocket | le serveur doit **pousser** le message vers le destinataire sans qu'il le demande |

**Trajet d'un message :**
1. A envoie `{"type":"message","destinataireId":2,"contenu":"Salut"}` sur sa connexion WebSocket.
2. Le serveur prend l'**expéditeur dans le jeton JWT** de la connexion, jamais dans le JSON reçu.
3. Il **valide** le message (non vide, 2000 caractères maximum, destinataire existant), pose la **date serveur**, puis l'**enregistre en base**.
4. **Seulement ensuite**, il l'envoie au destinataire s'il est connecté, et renvoie une confirmation à l'expéditeur.
5. Si le destinataire est **hors ligne** : le message est déjà en base (il le verra dans son historique),
   et un **webhook** prévient le service de notifications.

---

## 8. API REST

Toutes les réponses sont en JSON. Les routes marquées 🔒 exigent l'en-tête :

```
Authorization: Bearer <jeton>
```

Sans jeton valide, elles renvoient **401 Unauthorized**.

| Méthode | URL | Rôle | Codes de retour |
|---|---|---|---|
| `POST` | `/api/comptes` | créer un compte | 201, 400 (données invalides), 409 (nom déjà pris) |
| `POST` | `/api/auth/login` | se connecter, recevoir un JWT | 200, 401 (identifiants incorrects) |
| `GET` 🔒 | `/api/utilisateurs` | lister les utilisateurs et leur statut en ligne | 200 |
| `GET` 🔒 | `/api/messages/{utilisateurId}` | historique de **ma** conversation avec cet utilisateur | 200, 404 (utilisateur inconnu) |

**Créer un compte**

```bash
curl -X POST http://localhost:8080/api/comptes \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","motDePasse":"motdepasse123"}'
```
```json
{"id":1,"username":"alice"}
```
Règles : `username` de 3 à 30 caractères (lettres, chiffres, `_`), `motDePasse` de 8 à 72 caractères.

**Se connecter**

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","motDePasse":"motdepasse123"}'
```
```json
{"token":"eyJhbGciOiJIUzI1NiJ9...","type":"Bearer","expiresIn":3600,"utilisateur":{"id":1,"username":"alice"}}
```
Le jeton est valable **60 minutes**.

**Lister les utilisateurs** 🔒

```bash
curl http://localhost:8080/api/utilisateurs -H "Authorization: Bearer <jeton>"
```
```json
[{"id":1,"username":"alice","enLigne":true},{"id":2,"username":"bob","enLigne":false}]
```
`enLigne` vaut `true` quand l'utilisateur a au moins une connexion WebSocket ouverte.

**Historique avec un utilisateur** 🔒

```bash
curl "http://localhost:8080/api/messages/2?limit=50" -H "Authorization: Bearer <jeton>"
```
Paramètres facultatifs : `limit` (50 par défaut, 200 maximum) et `avant` (id de message, pour charger les plus anciens).
Les messages sont renvoyés **du plus ancien au plus récent**.

**Format d'une erreur**

```json
{"statut":409,"erreur":"Conflict","message":"Le nom d'utilisateur « alice » est déjà pris","horodatage":"2026-11-01T14:20:00"}
```

> Tout cela peut aussi se tester sans ligne de commande dans **Swagger UI** :
> http://localhost:8080/swagger-ui.html, bouton **Authorize** pour coller le jeton.

---

## 9. Canal WebSocket

**Connexion**

```
ws://localhost:8080/ws/messages?token=<jeton JWT>
```

Le jeton est vérifié **au moment du handshake** (`JwtHandshakeInterceptor`) : sans jeton valide, la connexion est refusée (401).
Il passe dans l'URL parce qu'un navigateur ne peut pas ajouter d'en-tête `Authorization` à une connexion WebSocket.

**Envoyer un message** (client → serveur)

```json
{"type":"message","destinataireId":2,"contenu":"Bonjour !"}
```

Le client n'envoie **ni** `expediteurId` **ni** `dateEnvoi` : le serveur les pose lui-même.
S'ils sont présents, ils sont ignorés, ce qui empêche d'envoyer un message au nom de quelqu'un d'autre.

**Message reçu** (serveur → destinataire, et confirmation à l'expéditeur), au format du sujet :

```json
{"type":"message","id":42,"expediteurId":1,"destinataireId":2,"contenu":"Bonjour !","dateEnvoi":"2026-11-01T10:15:30"}
```

**Indicateur de saisie** (bonus), relayé au destinataire et jamais enregistré :

```json
{"type":"saisie","destinataireId":2}
```

**Erreurs** renvoyées uniquement à l'expéditeur :

```json
{"type":"erreur","code":"CONTENU_VIDE","message":"Le message est vide"}
```

| Code | Cause |
|---|---|
| `JSON_INVALIDE` | le texte reçu n'est pas un objet JSON |
| `TYPE_INCONNU` | `type` n'est ni `message` ni `saisie` |
| `CONTENU_VIDE` | message vide ou seulement des espaces |
| `CONTENU_TROP_LONG` | plus de 2000 caractères |
| `DESTINATAIRE_MANQUANT` | `destinataireId` absent |
| `DESTINATAIRE_INVALIDE` | message envoyé à soi-même |
| `DESTINATAIRE_INCONNU` | aucun utilisateur avec cet id |

> Pour tester le canal sans les pages web : Postman → **New** → **WebSocket**, collez l'URL avec le jeton, puis envoyez le JSON ci-dessus.

---

## 10. Fonctionnalités bonus

| Bonus du sujet | Où le voir |
|---|---|
| **Indicateur de saisie** (« … est en train d'écrire ») | dans la conversation : trois points animés, et « écrit… » dans la liste. Transmis par WebSocket (`type: "saisie"`). |
| **Documentation OpenAPI / Swagger** | http://localhost:8080/swagger-ui.html |
| **Notification hors ligne par webhook** | écrivez à quelqu'un qui n'est **pas connecté**, puis regardez le terminal du serveur : `🔔 [service tiers simulé] notification envoyée à bob : « … » (de alice)` |

Fonctionnement du webhook : `NotificationService` envoie une requête **HTTP POST** en JSON, de façon **asynchrone**, vers l'URL `app.webhook.url`.
Par défaut, cette URL pointe vers `ServiceTiersSimuleController` (`POST /simulation/webhook`), qui joue le rôle du service tiers
et écrit la notification dans les logs. Si le service tiers ne répond pas, le message reste enregistré : seule la notification est perdue.

En plus, côté clients :
- **bannière cliquable** quand un message arrive dans une autre conversation ;
- **notification système** (macOS, Windows) quand l'onglet est en arrière-plan, si le navigateur l'autorise ;
- reconnexion WebSocket automatique, thème sombre, avatars emoji.

---

## 11. Organisation du code

```
src/main/java/com/projet/messagerie/
├── MessagerieApplication.java        point d'entrée Spring Boot
├── config/                           SecurityConfig (règles JWT), WebSocketConfig, PasswordConfig (BCrypt), OpenApiConfig (Swagger)
├── controller/                       AuthController, UtilisateurController, MessageController : les routes REST
├── service/                          AuthService, UtilisateurService, MessageService : la logique métier et les validations
├── repository/                       accès à la base (Spring Data JPA)
├── model/                            entités JPA Utilisateur et Message
├── dto/                              objets JSON échangés (records Java) : l'entité n'est jamais exposée directement
├── security/                         JwtService (créer / vérifier un jeton), JwtAuthFilter (REST), JwtHandshakeInterceptor (WebSocket)
├── websocket/                        MessageWebSocketHandler (reçoit, enregistre, retransmet), SessionRegistry (qui est connecté)
├── notification/                     NotificationService (webhook) et ServiceTiersSimuleController (service tiers simulé)
└── exception/                        exceptions métier et GlobalExceptionHandler (erreurs → JSON + bon code HTTP)

src/main/resources/
├── application.yml                   configuration (MySQL, JWT, webhook)
└── static/                           les deux clients, servis par Spring Boot
    ├── index.html                    redirige vers mobile.html ou bureau.html
    ├── bureau.html  /  mobile.html   mise en page de chaque client
    ├── css/app.css                   styles communs (thème clair et sombre)
    └── js/app.js                     logique commune : REST (fetch) + WebSocket

src/test/                             18 tests JUnit + application-test.yml (base H2)
```

**Choix de sécurité :**
- mots de passe **hachés avec BCrypt**, jamais stockés ni renvoyés en clair ;
- API **sans état** (*stateless*) : pas de session serveur, le JWT suffit ;
- expéditeur d'un message toujours tiré du **jeton**, jamais du client ;
- secrets (`DB_PASSWORD`, `JWT_SECRET`) passés par **variables d'environnement**, absents du dépôt Git ;
- textes affichés côté client avec `textContent`, jamais `innerHTML` : pas d'injection de HTML ou de script.

---

## 12. Problèmes fréquents

| Message d'erreur | Cause | Solution |
|---|---|---|
| `Access denied for user 'messagerie_user'@'localhost'` | serveur lancé sans `DB_PASSWORD`, ou mauvais mot de passe | relancer avec la commande complète de la section 3 |
| `Could not resolve placeholder 'JWT_SECRET'` | `JWT_SECRET` oublié | idem |
| `Port 8080 was already in use` | un ancien serveur tourne encore | `kill $(lsof -ti tcp:8080 -sTCP:LISTEN)` puis relancer |
| `Communications link failure` | MySQL n'est pas démarré | `brew services start mysql` (macOS) |
| Le téléphone n'affiche pas la page | pas sur le même réseau, mauvaise adresse IP, ou pare-feu | vérifier l'IP (`ipconfig getifaddr en0`) et autoriser Java dans le pare-feu |
| La pastille reste sur « connexion… » | WebSocket bloqué ou jeton expiré (60 min) | recharger la page, ou se reconnecter |
| L'ancien design s'affiche encore | cache du navigateur | `Cmd + Maj + R` (ou `Ctrl + F5`) |

---