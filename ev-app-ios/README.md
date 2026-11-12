<!-- EV Supervision Lite — built by Kevin Vavelin, Codetics — https://codetics.fr — LicenseRef-Codetics-Attribution-1.0 -->
# EV App pour iOS

Application client SwiftUI (iOS 26.2 minimum) du serveur `ev-server`. Elle couvre uniquement le parcours conducteur : aucune fonction d’administration.

Le scanner reconnaît aussi le QR code d’un autre opérateur : le conducteur choisit entre le site de l’opérateur et l’application, qui retrouve la borne par son identifiant EVSE (`FR*ABC*E12345*1`), lu dans le lien ou saisi à la main. CarPlay affiche les bornes proches avec leurs connecteurs, filtre les bornes disponibles, suit la charge et demande confirmation avant de l’arrêter.

## Configurer avant de publier

Remplacez toutes les valeurs `__…__` :

| Fichier | Clé | Exemple |
|---|---|---|
| `EV App/EV App/AppConfiguration.plist` | `API_URL` : serveur Java, joignable publiquement | `https://supervision.reseau.fr` |
| | `PORTAL_URL` : `APP_PUBLIC_URL` du serveur, celle des QR codes | `https://supervision.reseau.fr` |
| | `WORKSPACE_SLUG` : identifiant de l’espace client | `reseau` |
| | `BRAND_NAME`, `SUPPORT_EMAIL`, `PRIVACY_POLICY_URL` | |
| `EV App/Config/EVApp.entitlements` | `applinks:__APP_LINK_HOST__` | `applinks:supervision.reseau.fr` |

Trois contrôles empêchent de publier une copie non configurée :

1. La phase de compilation `Verify configuration` (`Config/verify-configuration.sh`) **fait échouer toute compilation Release** s’il reste une valeur à remplacer, une adresse non HTTPS ou un fichier source sans licence. En Debug, ce sont des avertissements.
2. Au lancement, `ConfigurationValidator` affiche la liste des problèmes et n’appelle aucun serveur.
3. L’application interroge ensuite `/api/v1/public/mobile/configuration` et refuse un serveur injoignable ou incompatible.

## Capacités Apple à obtenir

Renseignez votre équipe et votre identifiant d’application dans Xcode, puis activez :

| Capacité | Démarche |
|---|---|
| Push Notifications | Standard. Créez une clé APNs `.p8` pour le serveur. |
| NFC Tag Reading | Standard. |
| Associated Domains | Standard. Définissez `APPLE_APP_IDS` sur le tableau de bord. |
| CarPlay `carplay-charging` | **Demande à Apple**, sur dossier : developer.apple.com/contact/carplay |
| Passes Apple Wallet | Standard pour le Pass Type ID et son certificat. |
| Passes NFC | **Demande à Apple**, sur dossier : developer.apple.com/contact/passkit |

Tant que l’autorisation CarPlay n’est pas accordée, retirez `com.apple.developer.carplay-charging` de `Config/EVApp.entitlements` : sinon le profil de signature ne correspond pas.

## Configurer le serveur

Variables décrites dans `ev-server/.env.example` : `APNS_*` pour les notifications, `WALLET_*` pour Apple Wallet, `APP_SESSION_DAYS` pour la durée des sessions de l’application. Sans ces valeurs, l’application masque ou signale proprement la fonction correspondante.

Le mandataire doit transmettre `/api/v1/*` au serveur Java et figurer dans `APP_TRUSTED_PROXIES` : voir `docs/deploiement/README.md`.

## Cartes de recharge

1. L’administrateur active la vente dans **Cartes de recharge** du tableau de bord (prix, envoi postal ou remise en main propre). Le compte Stripe de l’espace doit être connecté.
2. Le conducteur commande et paie dans **Ma carte de recharge**, sur le tableau de bord. L’application y renvoie depuis l’onglet Badges.
3. L’exploitant associe une carte physique à la commande : identifiant RFID et, si possible, numéro imprimé. La carte reste refusée par les bornes. Le conducteur est prévenu.
4. Le conducteur active la carte dans l’onglet Badges, en la scannant ou en saisissant son numéro.

Seul le conducteur qui a commandé la carte peut l’activer : connaître un numéro ne suffit pas à prendre la carte d’un autre.

## Limite du passe Wallet NFC

Un passe Wallet NFC transmet une donnée chiffrée par le protocole VAS d’Apple. Il **n’émule pas l’UID d’une carte RFID** : une borne dont le lecteur lit seulement cet UID ne le reconnaîtra pas. Vérifiez la compatibilité de vos lecteurs avant d’annoncer cette fonction.

## Compte de démonstration (Debug uniquement)

Sur l’écran « Application non configurée », choisissez **Continuer avec le compte de démonstration**, ou lancez l’application avec l’argument `-demoAccount` (`-demoGuest` pour le parcours sans compte). Le dossier `Demo/` est compilé uniquement en Debug. CarPlay s’affiche dans Simulator par **I/O → External Displays → CarPlay**.

## Tests

```sh
xcodebuild test -project "EV App.xcodeproj" -scheme "EV App" -parallel-testing-enabled NO \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro'
```

- `EV AppTests` : validation de la configuration, QR codes, badges, et décodage de réponses réelles du serveur (`Fixtures/`, enregistrées par `AppContractFixtureTests` côté serveur).
- `LiveServerTests` : parcours complet contre un vrai serveur et une borne. Ignoré sauf si `TEST_RUNNER_EV_LIVE_API_URL` et les variables `EV_LIVE_*` associées sont définies.
- `EV AppUITests` : parcours de démonstration et captures de `screenshots/ios`.

## Licence

Distribué sous la [licence d’attribution Codetics](../LICENSE). L’écran **À propos** affiche l’attribution exigée : ne la retirez pas.
