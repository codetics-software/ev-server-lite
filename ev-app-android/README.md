<!-- EV Supervision Lite — built by Kevin Vavelin, Codetics — https://codetics.fr — LicenseRef-Codetics-Attribution-1.0 -->
# EV App pour Android

Application client Kotlin et Jetpack Compose (Android 8.0, API 26, minimum) du serveur `ev-server`. Elle couvre uniquement le parcours conducteur et reprend les fonctions de `ev-app-ios` : aucune fonction d’administration.

## Fonctions

- Carte et liste des bornes autour de l’utilisateur, filtres, itinéraire, photos prises sur place.
- Recharge par QR code, avec un badge ou sans compte (paiement par carte, remboursement du solde).
- Suivi de la charge en temps réel, historique, statistiques, données de comptage signées.
- Lecture NFC d’une carte et vérification sur le compte, activation d’une carte commandée.
- Passe Google Wallet, notifications FCM, véhicules et charge automatique, factures, moyens de paiement, suppression du compte.
- Android Auto (catégorie recharge) : bornes proches, itinéraire, suivi et arrêt de la charge.

## Configurer avant de publier

Remplacez toutes les valeurs `__…__` de `app/src/main/assets/AppConfiguration.properties` :

| Clé | Rôle | Exemple |
|---|---|---|
| `API_URL` | Serveur Java, joignable publiquement | `https://supervision.reseau.fr` |
| `PORTAL_URL` | `APP_PUBLIC_URL` du serveur, celle des QR codes et des liens d’application | `https://supervision.reseau.fr` |
| `WORKSPACE_SLUG` | Identifiant de l’espace client | `reseau` |
| `BRAND_NAME`, `SUPPORT_EMAIL`, `PRIVACY_POLICY_URL` | Marque, assistance, confidentialité | |
| `GOOGLE_MAPS_API_KEY` | Clé Google Maps SDK for Android. **Laissez vide pour utiliser MapLibre** | `AIza…` |
| `MAP_STYLE_URL` | Style MapLibre, utilisé sans clé Google ou sans Google Play | préréglé sur OpenFreeMap |
| `FIREBASE_*` | Valeurs de l’application Android dans la console Firebase. Laissez les quatre vides pour désactiver le push | |

Trois contrôles empêchent de publier une copie non configurée :

1. La tâche Gradle `verifyReleaseConfiguration` **fait échouer toute compilation Release** s’il reste une valeur à remplacer ou une adresse non HTTPS. En Debug, ce sont des avertissements. Dans toutes les variantes, un fichier source sans licence ou un écran **À propos** sans attribution fait échouer la compilation.
2. Au lancement, `ConfigurationValidator` affiche la liste des problèmes et n’appelle aucun serveur.
3. L’application interroge ensuite `/api/v1/public/mobile/configuration` et refuse un serveur injoignable ou incompatible.

## Carte

Avec `GOOGLE_MAPS_API_KEY` renseignée et les services Google Play présents, l’application affiche Google Maps. Sinon, elle affiche MapLibre et les tuiles de `MAP_STYLE_URL`. Restreignez la clé Google au nom de paquet et à l’empreinte SHA-256 de signature. Pour une forte audience, vérifiez les conditions du fournisseur de tuiles ou indiquez votre propre style. L’attribution des données cartographiques reste visible sur la carte et dans **À propos**.

## Serveur et comptes à préparer

| Fonction | Démarche | Variables du serveur |
|---|---|---|
| Notifications | Créez un projet Firebase, ajoutez l’application Android, créez une clé de compte de service | `FCM_SERVICE_ACCOUNT`, `FCM_PROJECT_ID` |
| Google Wallet | Créez un émetteur dans la Pay & Wallet Console et autorisez un compte de service | `GOOGLE_WALLET_ISSUER_ID`, `GOOGLE_WALLET_SERVICE_ACCOUNT`, `GOOGLE_WALLET_ORGANIZATION_NAME` |
| Wallet NFC | **Demande à Google** : accès Smart Tap et identifiant d’émetteur des lecteurs | `GOOGLE_WALLET_SMART_TAP_REDEMPTION_ISSUER` |
| Liens d’application | Publiez l’empreinte de signature (Play App Signing compris) | `ANDROID_APP_LINKS` sur le tableau de bord : `paquet:SHA256`, séparés par des virgules |

Sans ces valeurs, l’application masque ou signale proprement la fonction. Le mandataire doit transmettre `/api/v1/*` au serveur Java : voir `docs/deploiement/README.md`.

## Limite du passe Google Wallet NFC

Un passe Smart Tap transmet une donnée chiffrée à un lecteur compatible. Il **n’émule pas l’UID d’une carte RFID** : une borne dont le lecteur lit seulement cet UID ne le reconnaîtra pas. Sans Smart Tap, le passe affiche le badge en QR code. Vérifiez vos lecteurs avant d’annoncer cette fonction.

## Publier sur le Play Store

1. Choisissez votre `applicationId` dans `app/build.gradle.kts`.
2. Créez `keystore.properties` à la racine du projet (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Ce fichier est ignoré par Git.
3. Compilez avec `./gradlew bundleRelease`, puis envoyez le fichier `.aab`.
4. Dans la console Play : politique de confidentialité, section Sécurité des données, déclaration de localisation au premier plan, classification du contenu.
5. Pour Android Auto, activez le format **Android Auto** dans **Paramètres avancés > Formats**, puis publiez sur le canal dédié : Google examine séparément les applications pour voitures.
6. Remplacez le bouton « Ajouter à Google Wallet » par l’élément graphique officiel de Google avant la publication, comme l’exigent ses règles de marque.

Le paiement passe par Stripe : il rémunère une recharge et une carte physique, biens consommés hors de l’application. Faites confirmer ce point au moment de votre déclaration Play.

## Compte de démonstration (Debug uniquement)

Sur l’écran « Application non configurée », choisissez **Continuer avec le compte de démonstration**, ou lancez :

```sh
adb shell am start -n fr.codetics.evapp/.MainActivity --ez demoAccount true
```

Ajoutez `--ez demoGuest true` pour le parcours sans compte. Le dossier `app/src/debug` contient le serveur simulé : il n’est pas compilé en Release. Pour viser un serveur local depuis l’émulateur : `--es EV_API_URL http://10.0.2.2:8080`, et de même pour les autres clés (`EV_PORTAL_URL`…).

## Tests

```sh
./gradlew testDebugUnitTest
python3 scripts/capture_demo_screenshots.py
```

- Tests unitaires : configuration, QR codes, badges, filtres, client HTTP, écrans Android Auto, et décodage des réponses réelles du serveur (`app/src/test/resources/fixtures`, enregistrées par `AppContractFixtureTests` côté serveur).
- Le script pilote l’émulateur et produit `screenshots/android`.
- Android Auto se teste avec la Desktop Head Unit : `adb forward tcp:5277 tcp:5277`, puis `desktop-head-unit` depuis `extras/google/auto` du SDK.

## Licence

Distribué sous la [licence d’attribution Codetics](../LICENSE). L’écran **À propos** affiche l’attribution exigée : ne la retirez pas. Les icônes Material Symbols (Apache 2.0), MapLibre (BSD-2-Clause) et les autres dépendances conservent leurs licences.
