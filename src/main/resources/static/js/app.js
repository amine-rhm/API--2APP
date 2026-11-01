"use strict";
/*
 * Écho — logique commune aux deux clients (bureau.html et mobile.html).
 *  - REST (fetch)   : inscription, connexion, liste des utilisateurs, historique
 *  - WebSocket      : envoi et réception des messages en temps réel, « en train d'écrire »
 * Tout le texte venant du serveur est affiché avec textContent (jamais innerHTML) : pas d'injection HTML possible.
 */

const etat = {
  token: null,
  moi: null,                 // { id, username }
  contacts: [],              // [{ id, username, enLigne }]
  actif: null,               // id du contact dont la conversation est ouverte
  affiches: new Set(),       // ids des messages déjà affichés (évite les doublons)
  nonLus: new Map(),         // id du contact → nombre de messages non lus
  saisies: new Map(),        // id du contact → minuteur « écrit… »
  ws: null,
  tentatives: 0,             // reconnexions WebSocket ratées d'affilée
  minuteurReconnexion: null,
  minuteurPresence: null,
  derniereSaisie: 0,
  mode: "connexion",         // onglet de l'écran d'accueil : "connexion" ou "inscription"
};

const $ = (selecteur) => document.querySelector(selecteur);
const CLE_SESSION = "echo.session";
const CLE_THEME = "echo.theme";
const EMOJIS = ["🦊", "🐼", "🐨", "🦁", "🐯", "🐸", "🐙", "🦉", "🐧", "🦄", "🐢", "🐝", "🐳", "🦋", "🐻", "🐰"];
const FONDS_AVATAR = ["#cfe2d4", "#f6dfae", "#f5cdb9"]; // sauge, moutarde, terracotta en pâle

/* ================= REST ================= */

async function api(chemin, options = {}) {
  const entetes = { "Content-Type": "application/json" };
  if (etat.token) {
    entetes.Authorization = "Bearer " + etat.token;
  }
  const reponse = await fetch(chemin, { ...options, headers: entetes });
  if (reponse.status === 401 && etat.token) {
    deconnexion("Session expirée : reconnecte-toi.");
    throw new Error("Session expirée");
  }
  const corps = await reponse.json().catch(() => null);
  if (!reponse.ok) {
    throw new Error(corps?.message || "Erreur " + reponse.status);
  }
  return corps;
}

/* ================= Connexion / inscription ================= */

function changerOnglet(mode) {
  etat.mode = mode;
  document.querySelectorAll(".onglet").forEach((o) => o.classList.toggle("actif", o.dataset.onglet === mode));
  $("#auth-aide").hidden = mode !== "inscription";
  $("#auth-mdp").autocomplete = mode === "inscription" ? "new-password" : "current-password";
  $("#auth-bouton").textContent = mode === "inscription" ? "Créer mon compte →" : "Se connecter →";
  $("#auth-erreur").textContent = "";
}

async function soumettreAuth(evenement) {
  evenement.preventDefault();
  demanderPermissionNotifications(); // pendant le clic : les navigateurs l'exigent
  const identifiants = { username: $("#auth-nom").value.trim(), motDePasse: $("#auth-mdp").value };
  const bouton = $("#auth-bouton");
  bouton.disabled = true;
  $("#auth-erreur").textContent = "";
  try {
    if (etat.mode === "inscription") {
      await api("/api/comptes", { method: "POST", body: JSON.stringify(identifiants) });
    }
    const reponse = await api("/api/auth/login", { method: "POST", body: JSON.stringify(identifiants) });
    const session = { token: reponse.token, moi: reponse.utilisateur };
    try { sessionStorage.setItem(CLE_SESSION, JSON.stringify(session)); } catch { /* stockage indisponible */ }
    $("#auth-mdp").value = "";
    demarrer(session);
  } catch (e) {
    $("#auth-erreur").textContent = e.message;
  } finally {
    bouton.disabled = false;
  }
}

function demarrer(session) {
  if (etat.token) {
    return; // déjà démarré (double envoi du formulaire) : sinon on ouvrirait deux WebSocket
  }
  etat.token = session.token;
  etat.moi = session.moi;
  document.body.classList.add("connecte");
  $("#moi-nom").textContent = etat.moi.username;
  $("#moi-avatar").replaceChildren(avatar(etat.moi));
  chargerContacts();
  etat.minuteurPresence = setInterval(chargerContacts, 5000); // la présence est relue toutes les 5 s
  connecterWs();
}

function deconnexion(message = "") {
  clearInterval(etat.minuteurPresence);
  clearTimeout(etat.minuteurReconnexion);
  const ws = etat.ws;
  etat.ws = null;          // avant close() : onclose verra que c'est voulu et ne se reconnectera pas
  ws?.close();
  etat.saisies.forEach(clearTimeout);
  Object.assign(etat, { token: null, moi: null, contacts: [], actif: null, tentatives: 0 });
  etat.affiches.clear();
  etat.nonLus.clear();
  etat.saisies.clear();
  try { sessionStorage.removeItem(CLE_SESSION); } catch { /* rien */ }
  document.body.classList.remove("connecte", "conversation-ouverte");
  $("#messages").replaceChildren();
  $("#conv-entete").hidden = true;
  $("#composer").hidden = true;
  $("#indicateur").hidden = true;
  if ($("#vide")) $("#vide").hidden = false;
  $("#auth-erreur").textContent = message;
  majTitre();
}

/* ================= WebSocket ================= */

function connecterWs() {
  const protocole = location.protocol === "https:" ? "wss" : "ws";
  const ws = new WebSocket(`${protocole}://${location.host}/ws/messages?token=${encodeURIComponent(etat.token)}`);
  etat.ws = ws;
  statutWs("connexion", "connexion…");

  ws.onopen = () => {
    etat.tentatives = 0;
    statutWs("ok", "en direct");
    chargerContacts();
  };
  ws.onmessage = (e) => recevoir(JSON.parse(e.data));
  ws.onclose = () => {
    if (etat.ws !== ws) {
      return; // déconnexion volontaire, ou ancienne connexion
    }
    etat.ws = null;
    statutWs("coupe", "hors ligne");
    // reconnexion automatique : 1 s, 2 s, 4 s... jusqu'à 15 s
    const delai = Math.min(1000 * 2 ** etat.tentatives, 15000);
    etat.tentatives++;
    etat.minuteurReconnexion = setTimeout(connecterWs, delai);
  };
}

function statutWs(code, texte) {
  const pastille = $("#statut-ws");
  pastille.dataset.etat = code;
  pastille.textContent = texte;
}

function wsOuvert() {
  return etat.ws && etat.ws.readyState === WebSocket.OPEN;
}

function recevoir(donnee) {
  switch (donnee.type) {
    case "message": recevoirMessage(donnee); break;
    case "saisie": montrerSaisie(donnee.expediteurId); break;
    case "erreur": toast(donnee.message); break;
  }
}

function recevoirMessage(m) {
  const deMoi = m.expediteurId === etat.moi.id;
  const autre = deMoi ? m.destinataireId : m.expediteurId;
  if (!deMoi) {
    cacherSaisie(autre); // le message est arrivé : il n'est plus « en train d'écrire »
  }
  if (!contact(autre)) {
    chargerContacts();   // quelqu'un qui vient de s'inscrire
  }
  if (autre === etat.actif) {
    ajouterMessage(m);
    defilerEnBas();
  } else if (!deMoi) {
    etat.nonLus.set(autre, (etat.nonLus.get(autre) || 0) + 1);
    afficherContacts();
    majTitre();
    banniere(m);              // une autre conversation est ouverte : on prévient dans l'appli
  }
  if (!deMoi && document.hidden) {
    notificationSysteme(m);   // l'onglet est en arrière-plan : notification macOS / Windows / Android
  }
}

/* ================= Notifications ================= */

/** Petite bannière cliquable en bas de l'écran : « 🐨 alice — Salut ! ». */
function banniere(m) {
  const expediteur = contact(m.expediteurId) || { id: m.expediteurId, username: "Nouveau message" };
  const el = document.createElement("button");
  el.type = "button";
  el.className = "banniere";
  const texte = document.createElement("span");
  texte.className = "texte";
  const nom = document.createElement("strong");
  nom.textContent = expediteur.username;
  const apercu = document.createElement("span");
  apercu.textContent = m.contenu;
  texte.append(nom, apercu);
  el.append(avatar(expediteur), texte);
  el.addEventListener("click", () => {
    el.remove();
    ouvrirConversation(m.expediteurId);
  });
  $("#toasts").append(el);
  setTimeout(() => el.remove(), 5000);
}

function demanderPermissionNotifications() {
  if ("Notification" in window && Notification.permission === "default") {
    Notification.requestPermission();
  }
}

function notificationSysteme(m) {
  if (!("Notification" in window) || Notification.permission !== "granted") {
    return; // refusée, ou pas disponible (ex. page en http:// sur un téléphone)
  }
  const expediteur = contact(m.expediteurId);
  const notif = new Notification(expediteur ? expediteur.username : "Écho", {
    body: m.contenu,
    tag: "echo-" + m.expediteurId, // plusieurs messages de la même personne : une seule notification
  });
  notif.onclick = () => {
    window.focus();
    ouvrirConversation(m.expediteurId);
    notif.close();
  };
}

/* ================= Contacts ================= */

async function chargerContacts() {
  try {
    const tous = await api("/api/utilisateurs");
    etat.contacts = tous.filter((u) => u.id !== etat.moi?.id);
    afficherContacts();
    majEntete();
  } catch (e) {
    if (etat.token) console.warn(e.message);
  }
}

function contact(id) {
  return etat.contacts.find((c) => c.id === id);
}

function afficherContacts() {
  const filtre = $("#recherche").value.trim().toLowerCase();
  const visibles = etat.contacts
    .filter((c) => c.username.toLowerCase().includes(filtre))
    .sort((a, b) => (b.enLigne - a.enLigne) || a.username.localeCompare(b.username)); // en ligne d'abord

  const elements = visibles.map((c) => {
    const bouton = document.createElement("button");
    bouton.type = "button";
    bouton.className = "contact" + (c.id === etat.actif ? " actif" : "");
    bouton.addEventListener("click", () => ouvrirConversation(c.id));

    const texte = document.createElement("span");
    texte.className = "texte";
    const nom = document.createElement("strong");
    nom.textContent = c.username;
    texte.append(nom, sousTitre(c));
    bouton.append(avatar(c, c.enLigne), texte);

    const nonLus = etat.nonLus.get(c.id);
    if (nonLus) {
      const badge = document.createElement("span");
      badge.className = "badge";
      badge.textContent = nonLus > 99 ? "99+" : nonLus;
      bouton.append(badge);
    }
    const li = document.createElement("li");
    li.append(bouton);
    return li;
  });

  $("#contacts").replaceChildren(...elements);
  $("#contacts-vide").hidden = etat.contacts.length > 0;
}

function sousTitre(c) {
  const el = document.createElement("span");
  const ecrit = etat.saisies.has(c.id);
  el.className = "sous-titre" + (ecrit ? " ecrit" : "");
  el.textContent = ecrit ? "écrit…" : c.enLigne ? "en ligne" : "hors ligne";
  return el;
}

function avatar(utilisateur, enLigne = false) {
  // choisi à partir de l'id : unique, et identique sur tous les appareils
  const el = document.createElement("span");
  el.className = "avatar" + (enLigne ? " en-ligne" : "");
  el.textContent = EMOJIS[utilisateur.id % EMOJIS.length];
  el.style.background = FONDS_AVATAR[utilisateur.id % FONDS_AVATAR.length];
  el.title = utilisateur.username;
  return el;
}

/* ================= Conversation ================= */

async function ouvrirConversation(id) {
  etat.actif = id;
  etat.nonLus.delete(id);
  etat.affiches.clear();
  document.body.classList.add("conversation-ouverte");
  if ($("#vide")) $("#vide").hidden = true;
  $("#conv-entete").hidden = false;
  $("#composer").hidden = false;
  const zone = $("#messages");
  zone.replaceChildren();
  delete zone.dataset.jour;
  majEntete();
  afficherContacts();
  majTitre();
  if (matchMedia("(pointer: fine)").matches) $("#saisie").focus(); // pas sur mobile : ça ouvrirait le clavier

  try {
    const historique = await api(`/api/messages/${id}?limit=100`);
    if (etat.actif !== id) return; // on a changé de conversation pendant le chargement
    historique.forEach(ajouterMessage);
    defilerEnBas();
  } catch (e) {
    toast(e.message);
  }
}

function fermerConversation() {
  etat.actif = null;
  document.body.classList.remove("conversation-ouverte");
  afficherContacts();
}

function majEntete() {
  const c = contact(etat.actif);
  if (!c) return;
  $("#conv-nom").textContent = c.username;
  $("#conv-avatar").replaceChildren(avatar(c, c.enLigne));
  $("#conv-statut").replaceChildren(sousTitre(c));
  $("#indicateur").hidden = !etat.saisies.has(c.id);
}

function ajouterMessage(m) {
  if (etat.affiches.has(m.id)) return;
  etat.affiches.add(m.id);

  const zone = $("#messages");
  const date = new Date(m.dateEnvoi);
  const jour = date.toDateString();
  if (zone.dataset.jour !== jour) {
    const separateur = document.createElement("div");
    separateur.className = "jour";
    separateur.textContent = libelleJour(date);
    zone.append(separateur);
    zone.dataset.jour = jour;
  }

  const deMoi = m.expediteurId === etat.moi.id;
  const precedent = zone.lastElementChild;
  const bulle = document.createElement("div");
  bulle.className = "bulle " + (deMoi ? "moi" : "lui");
  bulle.dataset.expediteur = m.expediteurId;
  if (precedent?.dataset.expediteur === String(m.expediteurId)) {
    bulle.classList.add("suite"); // plusieurs messages d'affilée : on les rapproche
  }
  const texte = document.createElement("p");
  texte.textContent = m.contenu;
  const heure = document.createElement("time");
  heure.dateTime = m.dateEnvoi;
  heure.textContent = date.toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" });
  bulle.append(texte, heure);
  zone.append(bulle);
}

function libelleJour(date) {
  const aujourdhui = new Date();
  const hier = new Date();
  hier.setDate(aujourdhui.getDate() - 1);
  if (date.toDateString() === aujourdhui.toDateString()) return "Aujourd'hui";
  if (date.toDateString() === hier.toDateString()) return "Hier";
  return date.toLocaleDateString("fr-FR", { weekday: "long", day: "numeric", month: "long" });
}

function defilerEnBas() {
  const zone = $("#messages");
  zone.scrollTop = zone.scrollHeight;
}

function envoyer(evenement) {
  evenement.preventDefault();
  const champ = $("#saisie");
  const contenu = champ.value.trim();
  if (!contenu || !etat.actif) return;
  if (!wsOuvert()) {
    toast("Connexion temps réel coupée, reconnexion en cours…");
    return;
  }
  // pas d'expediteurId ni de date : le serveur les pose lui-même
  etat.ws.send(JSON.stringify({ type: "message", destinataireId: etat.actif, contenu }));
  champ.value = "";
  ajusterHauteur();
  etat.derniereSaisie = 0;
}

/* ================= Bonus : « en train d'écrire » ================= */

function signalerSaisie() {
  ajusterHauteur();
  const maintenant = Date.now();
  if (!etat.actif || !wsOuvert() || maintenant - etat.derniereSaisie < 2000) return; // au plus 1 envoi / 2 s
  etat.derniereSaisie = maintenant;
  etat.ws.send(JSON.stringify({ type: "saisie", destinataireId: etat.actif }));
}

function montrerSaisie(id) {
  clearTimeout(etat.saisies.get(id));
  etat.saisies.set(id, setTimeout(() => cacherSaisie(id), 3000));
  afficherContacts();
  majEntete();
  if (id === etat.actif) defilerEnBas();
}

function cacherSaisie(id) {
  if (!etat.saisies.has(id)) return;
  clearTimeout(etat.saisies.get(id));
  etat.saisies.delete(id);
  afficherContacts();
  majEntete();
}

/* ================= Divers ================= */

function ajusterHauteur() {
  const champ = $("#saisie");
  champ.style.height = "auto";
  champ.style.height = Math.min(champ.scrollHeight + 1, 160) + "px"; // + 1 : évite une barre de défilement inutile
}

function toast(message) {
  const el = document.createElement("div");
  el.className = "toast";
  el.textContent = message;
  $("#toasts").append(el);
  setTimeout(() => el.remove(), 4000);
}

function majTitre() {
  let total = 0;
  etat.nonLus.forEach((n) => (total += n));
  document.title = (total ? `(${total}) ` : "") + document.body.dataset.titre;
}

function appliquerTheme(theme) {
  document.documentElement.dataset.theme = theme;
  try { localStorage.setItem(CLE_THEME, theme); } catch { /* rien */ }
}

/* ================= Démarrage ================= */

document.body.dataset.titre = document.title;

let themeEnregistre = null;
try { themeEnregistre = localStorage.getItem(CLE_THEME); } catch { /* rien */ }
document.documentElement.dataset.theme =
  themeEnregistre || (matchMedia("(prefers-color-scheme: dark)").matches ? "sombre" : "clair");

document.querySelectorAll(".onglet").forEach((o) => o.addEventListener("click", () => changerOnglet(o.dataset.onglet)));
$("#form-auth").addEventListener("submit", soumettreAuth);
$("#composer").addEventListener("submit", envoyer);
$("#saisie").addEventListener("input", signalerSaisie);
$("#saisie").addEventListener("keydown", (e) => {
  if (e.key === "Enter" && !e.shiftKey && !e.isComposing) { // Entrée envoie, Maj+Entrée va à la ligne
    e.preventDefault();
    $("#composer").requestSubmit();
  }
});
$("#recherche").addEventListener("input", afficherContacts);
$("#btn-deconnexion").addEventListener("click", () => deconnexion());
$("#btn-theme").addEventListener("click", () =>
  appliquerTheme(document.documentElement.dataset.theme === "sombre" ? "clair" : "sombre"));
$("#btn-retour")?.addEventListener("click", fermerConversation); // seulement sur mobile.html

// session déjà ouverte dans cet onglet (sessionStorage : chaque onglet peut être un utilisateur différent)
try {
  const session = JSON.parse(sessionStorage.getItem(CLE_SESSION));
  if (session?.token) demarrer(session);
} catch { /* pas de session */ }