# Nidaa TV — application Android TV / box Android

Enveloppe native (Kotlin) qui affiche l'écran web Nidaa (`apps/screen`, PWA) dans un
WebView plein écran, en mode kiosque. Elle existe pour ce que le navigateur seul ne permet pas :

- jouer le **son de l'adhan sans clic** (`mediaPlaybackRequiresUserGesture = false`) ;
- garder l'écran allumé, en plein écran immersif, en paysage ;
- **démarrer toute seule** à l'allumage de la box, ou servir de lanceur (écran d'accueil) ;
- neutraliser la télécommande (la touche Retour ne fait rien) ;
- afficher un écran d'attente et réessayer toutes les 30 s si la page n'a jamais pu être chargée.

Le fonctionnement hors ligne reste assuré par la PWA elle-même (service worker + IndexedDB) :
une fois l'écran chargé et appairé, il continue de fonctionner sans internet.

> Ce dossier n'est **pas** un paquet pnpm : il se construit avec Gradle, indépendamment du monorepo.

## Prérequis

- **Android Studio** (Koala ou plus récent), ou bien en ligne de commande :
  **JDK 17** (ou 21) + **Android SDK** avec `platforms;android-34` et `build-tools;34.0.0`.
- Indiquer l'emplacement du SDK : variable `ANDROID_HOME`, ou fichier `local.properties`
  contenant `sdk.dir=/chemin/vers/Android/Sdk` (non versionné).
- Accès internet au premier build (Gradle 8.7, plugin Android 8.5.2, Kotlin 1.9.24, AndroidX).

Le wrapper Gradle est fourni (`gradlew`, `gradle/wrapper/`). S'il manquait
`gradle/wrapper/gradle-wrapper.jar`, le régénérer une fois avec un Gradle installé :
`gradle wrapper --gradle-version 8.7`.

## Construire l'APK

```bash
cd apps/tv

# Debug (HTTP en clair autorisé : pratique pour un serveur de test sur le réseau local)
./gradlew assembleDebug -PnidaaScreenUrl=http://192.168.1.20:5173
# → app/build/outputs/apk/debug/app-debug.apk

# Release (HTTPS uniquement)
./gradlew assembleRelease -PnidaaScreenUrl=https://nidaa-ecran.netlify.app
# → app/build/outputs/apk/release/app-release.apk (ou app-release-unsigned.apk sans keystore)
```

`nidaaScreenUrl` est l'adresse chargée par défaut (valeur par défaut : `https://nidaa-ecran.netlify.app`).
Elle reste modifiable sur la box depuis le menu de réglages.

### Signer la version release

Créer un keystore une fois, puis copier `keystore.properties.example` en `keystore.properties`
(non versionné) et le remplir :

```bash
keytool -genkeypair -v -keystore nidaa-release.jks -alias nidaa -keyalg RSA -keysize 2048 -validity 10000
```

Conserver précieusement ce keystore : une mise à jour doit être signée avec la même clé,
sinon il faut désinstaller l'appli (et réappairer l'écran).

## Installer sur une box

### Par le réseau (adb)

1. Sur la box : Paramètres → À propos → appuyer 7 fois sur « Numéro de build », puis activer
   le **débogage USB / réseau** dans les options pour les développeurs.
2. Depuis le PC, sur le même réseau :

```bash
adb connect 192.168.1.50:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dj.nidaa.tv/.MainActivity
```

### Par clé USB

1. Copier l'APK sur une clé USB et la brancher sur la box.
2. Ouvrir l'APK avec le gestionnaire de fichiers de la box (sur Android TV, installer au besoin
   un gestionnaire de fichiers depuis le Play Store).
3. Accepter « Installer des applis inconnues » pour ce gestionnaire de fichiers.

### Définir Nidaa comme lanceur (vrai mode kiosque)

L'appli se déclare comme écran d'accueil possible (catégorie `HOME`). C'est la méthode la plus
fiable : la box démarre directement sur l'écran de la mosquée et la touche HOME y ramène.

- Sur une box Android classique : appuyer sur HOME après l'installation, choisir **Nidaa**
  puis **Toujours** (ou Paramètres → Applications → Applications par défaut → Écran d'accueil).
- Sur Android TV / Google TV, le choix du lanceur n'est pas toujours proposé ; on peut alors
  s'appuyer sur l'option « Démarrer au boot », ou désactiver le lanceur d'origine par adb
  (dépend du modèle, à faire en connaissance de cause), par exemple :
  `adb shell pm disable-user --user 0 com.google.android.tvlauncher`.

Pour revenir en arrière : Paramètres Android → Applications → Nidaa → Effacer les valeurs par
défaut (tant que Nidaa est le lanceur, « Quitter » ramène aussitôt sur l'écran Nidaa).

### Démarrage automatique

L'option « Démarrer au boot » (activée par défaut) relance l'écran après un redémarrage ou une
mise à jour de l'appli. L'appli doit avoir été ouverte au moins une fois. Sur Android 10 et
plus, certains systèmes bloquent le lancement d'une appli en arrière-plan : dans ce cas,
définir Nidaa comme lanceur (ci-dessus).

## Menu de réglages (caché)

Trois façons de l'ouvrir avec la télécommande :

- touche **MENU** ;
- **appui long sur OK** (2 secondes) ;
- **5 appuis sur RETOUR** en moins de 3 secondes.

Contenu : adresse de l'écran (modifiable), **Recharger**, **Vider le cache et dissocier**
(efface toutes les données web : il faudra réappairer l'écran), **Démarrer au boot**,
version de l'appli et du WebView, **Quitter**. Tout se pilote aux flèches + OK.

## Matériel de référence conseillé

- Box Android TV ou box Android « générique », **Android 9 ou plus** (minimum technique : Android 6).
- **2 Go de RAM** ou plus, 8 Go de stockage.
- **Ethernet** de préférence au Wi-Fi ; sortie HDMI 1080p.
- Réglages de la box : désactiver la mise en veille et l'économiseur d'écran, activer l'heure
  automatique (réseau), activer HDMI-CEC si l'on veut allumer la TV avec la box.

## Pont JavaScript

La page dispose de `window.NidaaTV` :

| Méthode | Retour |
| --- | --- |
| `getVersion()` | version de l'appli, ex. `"0.1.0"` |
| `getDeviceInfo()` | chaîne JSON : `model`, `manufacturer`, `androidVersion`, `sdkInt`, `appVersion` |
| `reload()` | recharge l'écran |

Le user-agent se termine par ` NidaaTV/<version>`. Rien d'autre n'est exposé (pas d'accès
fichiers, pas de réglages).

## Dépannage

- **Horaires décalés / mauvaise heure** : l'écran utilise l'horloge de la box. Vérifier
  Paramètres → Date et heure → heure automatique (NTP) et le fuseau horaire. Une box sans pile
  d'horloge redémarrée sans internet peut afficher une date fausse jusqu'au retour du réseau ;
  une date fausse fait aussi échouer HTTPS (certificat « pas encore valide »).
- **Page blanche, affichage cassé ou pas de son** : WebView trop ancien. Mettre à jour
  **Android System WebView** (Play Store, ou APK correspondant à l'architecture de la box).
  La version installée est affichée dans le menu de réglages.
- **« Connexion impossible — nouvelle tentative dans 30 s »** : la page n'a jamais pu être
  chargée. Vérifier le câble/Wi-Fi, l'adresse dans le menu de réglages et, en version release,
  que l'adresse est bien en `https://` (HTTP en clair refusé hors debug).
- **Mauvaise adresse ou mauvaise mosquée** : menu de réglages → corriger l'adresse, ou
  « Vider le cache et dissocier » puis réappairer.
- **Journaux** : `adb logcat -s NidaaTV` (inclut la console JavaScript de la page).
  En debug, la page est inspectable depuis `chrome://inspect`.
