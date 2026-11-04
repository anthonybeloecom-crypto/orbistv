# Orbis TV

Lecteur IPTV pour **Android TV / Google TV / Fire TV**, qui marche aussi sur téléphone et tablette.
Orbis TV est **uniquement un lecteur** : il ne fournit aucune chaîne ni aucun contenu. L'utilisateur
connecte son propre abonnement (Xtream Codes ou playlist M3U).

> « Orbis TV » est un nom provisoire. Pour le changer : `app_name` dans `res/values/strings.xml`,
> `applicationId` dans `app/build.gradle.kts`, le `Wordmark` dans `ui/Components.kt` et la bannière `res/drawable-xhdpi/banner.png`.

## Fonctionnalités (v1.0)

- **Connexion** Xtream Codes (serveur, identifiant, mot de passe) ou lien M3U/M3U8
- **TV en direct** : catégories, logos, zapping Haut/Bas ou CH+/CH- dans le lecteur, OK pour afficher la chaîne
- **Films** : grille d'affiches par catégorie, fiche (synopsis, genre, année, casting), reprise de lecture
- **Séries** : saisons, épisodes, progression par épisode, **épisode suivant automatique** (compte à rebours 10 s)
- **Favoris** : appui long sur OK (ou touche Menu) sur une chaîne, bouton sur les fiches films/séries
- **Recherche** globale (chaînes + films + séries), insensible aux accents
- **Lecteur Media3/ExoPlayer** : TS, HLS, MP4, MKV, pistes audio et sous-titres, reconnexion automatique
- **Réglages** : masquage des catégories adultes, format direct TS/HLS, rechargement des listes, déconnexion
- Interface pensée télécommande (focus, zoom, liseré) et adaptée au tactile sur téléphone
- Tout est stocké en local sur l'appareil, aucun serveur Orbis

## Nouveautés 1.1

- **Profils « Qui regarde ? »** : favoris et reprise de lecture séparés pour chaque personne
- **Guide TV** : ce qui passe maintenant et ensuite, chaîne par chaîne (comptes Xtream)
- **Aperçu de la chaîne** en mode Liste sur grand écran, avec le programme en cours
- **Films et séries en grand** : affiche à la une avec image de fond, rangées par catégorie
- **Wi-Fi ou fournisseur ?** : diagnostic connexion, Internet, serveur et débit réel d'une chaîne
- **Format d'image** dans le lecteur : touche Menu ou Info de la télécommande (Adapter, Zoom, Étirer)
- **Configuration au téléphone** : QR code affiché sur la TV, formulaire sur le téléphone (même Wi-Fi)
- **Connexion plus robuste** : un lien get.php est converti automatiquement en compte Xtream ;
  si un serveur refuse l'app (codes 403, 884…), elle réessaie avec un identifiant de lecteur standard

## Obtenir l'APK sans rien installer (GitHub Actions)

1. Crée un dépôt GitHub **privé** et envoies-y ce dossier (bouton « uploading an existing file », ou `git push`).
2. Onglet **Actions** → le workflow « Build APK » se lance tout seul (environ 5 min).
3. Une fois terminé, ouvre le run → section **Artifacts** → télécharge `OrbisTV-apk` (zip contenant `app-release.apk`).

## Compiler avec Android Studio

1. Ouvre le dossier `OrbisTV` dans Android Studio (Ladybug ou plus récent).
2. Laisse la synchro Gradle se terminer (accepte les mises à jour proposées si besoin).
3. `Build > Build App Bundle(s) / APK(s) > Build APK(s)`, ou bouton ▶ avec une TV/émulateur Android TV branché.

## Installer sur la TV

- **Google TV / Android TV** : Paramètres → Système → À propos → clique 7 fois sur « Build Android TV OS » pour activer le mode développeur,
  puis autorise les sources inconnues pour l'app « Downloader » (ou « Send Files to TV »). Héberge l'APK (Drive, Dropbox…) et ouvre le lien dans Downloader.
- **Fire TV** : Paramètres → Ma Fire TV → Options pour les développeurs → Applications de sources inconnues → Downloader.
- **ADB** : `adb connect IP_DE_LA_TV` puis `adb install app-release.apk`.

L'APK release est signé avec la clé debug pour faciliter l'installation directe. Avant toute diffusion publique, crée ta propre clé de signature.

## Structure

```
app/src/main/java/app/orbistv/
├── OrbisApp.kt            Application, dépendances, file de lecture
├── data/
│   ├── Models.kt          Modèles (Stream, Catalog, Episode, Account…)
│   ├── Prefs.kt           Compte, favoris, reprise, réglages (SharedPreferences)
│   ├── M3uParser.kt       Parseur #EXTINF
│   └── Repository.kt      API Xtream Codes, M3U, cache, recherche, URLs de lecture
├── player/PlayerActivity.kt   Lecteur plein écran Media3
└── ui/
    ├── MainActivity.kt    Navigation
    ├── Components.kt      Composants focusables, images, chargement
    ├── theme/Theme.kt     Couleurs
    └── screens/           Connexion, Accueil, Direct, Films/Séries, Fiches, Favoris, Recherche, Réglages
```

## Pistes pour la suite

- Guide des programmes (EPG XMLTV / `get_short_epg`) et timeshift
- Profils multiples et contrôle parental par code PIN
- Configuration depuis le téléphone par QR code
- Plusieurs playlists, tri et réorganisation des chaînes
- Écran « Continuer à regarder » sur l'accueil
