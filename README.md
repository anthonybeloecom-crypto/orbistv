# Orbis TV pour iPhone (version de test)

App SwiftUI, même fonctionnement que la version Android : connexion Xtream Codes ou M3U, Direct, Films, Séries,
Favoris, reprise de lecture, épisode suivant automatique. Lecteur natif d'Apple avec AirPlay et image dans l'image.

## Nouveautés 1.1

- Profils « Qui regarde ? » (favoris et reprise par personne), onglet **Moi**
- Onglet **Guide** : programmes en cours et à venir (comptes Xtream)
- Films et séries « en grand » : à la une + rangées par catégorie
- « Wi-Fi ou fournisseur ? » : diagnostic réseau (Moi › Wi-Fi ou fournisseur ?)
- Bouton format d'image dans le lecteur (Adapter, Zoom, Étirer)
- Connexion plus robuste : lien get.php converti en Xtream, nouvel essai si le serveur refuse l'app (code 884)

## Il te faut

- Un Mac avec **Xcode 16 ou plus récent** (gratuit sur le Mac App Store)
- Ton iPhone (iOS 16 minimum) et son câble
- Ton identifiant Apple (le compte gratuit suffit pour tester)

## Lancer l'app sur ton iPhone

1. Décompresse le dossier, puis double-clique sur **OrbisTV.xcodeproj**.
2. Dans Xcode : **Xcode › Réglages › Comptes** : ajoute ton identifiant Apple.
3. Clique sur le projet **OrbisTV** (à gauche), onglet **Signing & Capabilities** : dans **Team**, choisis ton nom (Personal Team).
   Si Xcode signale que l'identifiant `com.belo.orbistv` est déjà pris, remplace-le par un autre, par exemple `com.tonnom.orbistv`.
4. Branche ton iPhone et déverrouille-le. Accepte « Faire confiance à cet ordinateur ».
5. Sur l'iPhone : **Réglages › Confidentialité et sécurité › Mode développeur** : active-le (l'iPhone redémarre).
6. Dans Xcode, en haut, choisis ton iPhone comme destination, puis clique sur **▶ (Run)**.
7. Au premier lancement, l'iPhone peut bloquer l'app : **Réglages › Général › VPN et gestion de l'appareil** › ton identifiant › **Faire confiance**.

Avec un compte Apple gratuit, l'app reste installée **7 jours**. Il suffit de la relancer depuis Xcode pour la renouveler.
Pour la distribuer à d'autres personnes (TestFlight / App Store), il faut le programme développeur Apple (99 €/an).

## Limites du lecteur iPhone

- Le direct passe en **HLS (.m3u8)** : c'est le réglage par défaut. Le format TS n'est pas lu par le lecteur d'Apple.
- Les films en **MKV ou AVI** ne sont pas lus par le lecteur d'Apple (le MP4 oui). Une version future pourra embarquer un lecteur VLC.
- Les données (compte, favoris, progression) restent sur l'iPhone.

## Structure

```
OrbisTV/
├── OrbisApp.swift      Point d'entrée, onglets, file de lecture
├── Models.swift        Modèles (Stream, Catalog, Episode, Account…)
├── Store.swift         Compte, favoris, reprise, réglages (UserDefaults)
├── Repo.swift          API Xtream Codes, parseur M3U, URLs de lecture
├── Components.swift    Couleurs, boutons, images, états
├── LoginView.swift     Connexion
├── CatalogScreen.swift Direct / Films / Séries
├── DetailViews.swift   Fiches film et série, Favoris, Réglages
└── PlayerScreen.swift  Lecteur (AVPlayer)
```
