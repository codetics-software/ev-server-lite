# Déploiement

Ce guide décrit une architecture indépendante du fournisseur, puis renvoie vers les guides [Google Cloud](gcp.md), [AWS](aws.md), [Azure](azure.md), [Scaleway](scaleway.md) et [Oracle Cloud](oracle-cloud.md). Les fichiers prêts à l’emploi sont dans [`deploy/`](../../deploy) : `docker-compose.yml` pour une machine virtuelle, `kubernetes/` pour un cluster.

## Architecture de référence

```text
                    Internet
     navigateurs      │       bornes OCPP 1.6J (wss://)
                      ▼
        ┌──────────────────────────────┐
        │ Mandataire / équilibreur     │  TLS, WebSocket, certificat client (profil 3)
        └──────┬───────────────┬───────┘
     /ocpp/*   │               │  tout le reste
     /ocpi/*   ▼               ▼
        ┌─────────────┐   ┌──────────────┐
        │ ev-server   │◄──│ ev-dashboard │  /api/backend/* → API Java (réseau privé)
        │ Java, 8080  │   │ Node, 3000   │
        └──────┬──────┘   └──────────────┘
               ▼
        PostgreSQL géré (ou MongoDB 8 en replica set)
```

| Composant | Image | Port | Exposition |
|---|---|---|---|
| Serveur | `ev-server/Dockerfile` | `PORT`, fixé à `8080` | `/ocpp/*` et `/ocpi/*` seulement, via le mandataire |
| Tableau de bord | `ev-dashboard/Dockerfile` (sortie `standalone` de Next.js) | `3000` | Tout le reste |
| Base | Service géré | 5432 | Réseau privé uniquement |

Le tableau de bord transmet lui-même l’API, les connexions OAuth et les notifications Stripe au serveur Java (`EV_SERVER_URL`). `/actuator`, `/api/v1/openapi` et `/api/v1/docs` ne doivent pas être publiés directement : seuls `/ocpp/` et `/ocpi/` vont au serveur Java depuis Internet.

Deux noms sont recommandés :

- `APP_HOST` (`supervision.exemple.fr`) : tableau de bord, API, notifications Stripe, OCPI, bornes en profils 1 et 2 ;
- `OCPP_HOST` (`ocpp.exemple.fr`) : bornes en profil 3 seulement, avec certificat client obligatoire. Un nom distinct évite qu’un navigateur propose un certificat sur le tableau de bord et permet une politique TLS stricte.

## Construire les images

```sh
docker build -t REGISTRE/ev-server:0.0.1 ev-server
docker build -t REGISTRE/ev-dashboard:0.0.1 ev-dashboard
docker push REGISTRE/ev-server:0.0.1
docker push REGISTRE/ev-dashboard:0.0.1
```

Les images ne contiennent aucun secret : tout passe par des variables d’environnement au démarrage. Pour une machine Arm (Ampere, Graviton, Cobalt), construisez avec `docker buildx build --platform linux/arm64` ou les deux architectures.

L’image du serveur s’exécute avec l’utilisateur non privilégié `10001`. Elle fixe `JAVA_TOOL_OPTIONS="-Xms64m -Xmx256m -XX:ActiveProcessorCount=1"` et expose le port 10000 pour l’hébergement actuel. Surchargez toujours `PORT=8080` et `JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"` pour que la JVM utilise la mémoire et les processeurs alloués au conteneur.

## Base de données

**PostgreSQL** est recommandé : chaque fournisseur propose un service géré avec sauvegardes et restauration à un instant donné. Version 15 ou ultérieure ; Flyway crée le schéma au premier démarrage, l’utilisateur doit donc posséder la base.

```text
DATABASE_URL=jdbc:postgresql://10.0.0.5:5432/ev_supervision?sslmode=require
DATABASE_USER=ev_supervision
DATABASE_PASSWORD=…
```

Le pool compte 12 connexions par défaut ; ajustez avec `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` si l’offre limite le nombre de connexions. Prévoyez le pool, plus les connexions d’administration et de sauvegarde.

**MongoDB 8** exige un replica set ou `mongos`, car les transactions sont indispensables. MongoDB Atlas est disponible chez les cinq fournisseurs. Les services « compatibles MongoDB » (Amazon DocumentDB, Azure Cosmos DB for MongoDB, API MongoDB d’Oracle) n’ont pas été validés : leur prise en charge des transactions et des index diffère de MongoDB 8.

## Secrets

Conservez dans le gestionnaire de secrets du fournisseur, et injectez comme variables d’environnement : `DATABASE_PASSWORD`, `BOOTSTRAP_PASSWORD` (puis supprimez-le), `SMTP_PASSWORD`, `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `APP_ENCRYPTION_KEY`, `OCPP_SIGNING_CA_KEY`, `EV_DATABASE_API_KEY`, `APNS_KEY`, `WALLET_CERTIFICATE`, `WALLET_CERTIFICATE_PASSWORD`, `FCM_SERVICE_ACCOUNT`, `GOOGLE_WALLET_SERVICE_ACCOUNT`.

`APP_ENCRYPTION_KEY` chiffre des secrets en base : sa perte rend ces données illisibles. Sauvegardez-la séparément de la base. Les valeurs PEM (`OCPP_CLIENT_CA`, `OCPP_SIGNING_CA_*`) acceptent un texte multiligne, une seule ligne avec `\n`, ou le chemin d’un fichier monté.

## Connexions OCPP

Les bornes ouvrent une connexion WebSocket permanente vers `/ocpp/{espace}/{borne}`. Le mandataire doit :

1. transmettre la mise à niveau WebSocket (`Upgrade`, `Connection`) en HTTP/1.1 vers le serveur ;
2. garder une connexion inactive **au moins 600 s, idéalement 3 600 s**, côté client comme côté serveur. Le serveur envoie un ping WebSocket à chaque borne toutes les 30 s, et le battement vaut `OCPP_HEARTBEAT_SECONDS` (60 s par défaut, borné entre 10 et 300) ou la valeur propre à la borne ; un délai d’inactivité plus long évite toute coupure si un intermédiaire ignore les pings ;
3. ne pas limiter la durée totale d’une connexion, ou la fixer à plusieurs heures. Une coupure provoque une reconnexion, normale mais visible dans l’historique ;
4. publier `X-Forwarded-For` et `X-Forwarded-Proto` en remplaçant toute valeur envoyée par le client.

L’affinité de session n’est pas nécessaire : une borne n’a qu’une connexion, et les commandes passent par une file en base. Le sous-protocole `ocpp1.6` est accepté, et une borne qui n’en annonce aucun est traitée comme OCPP 1.6.

Un refus de connexion répond HTTP 401 et ajoute un événement `CONNECTION_REJECTED` dans l’historique de la borne. C’est le premier endroit à consulter lors d’une mise en service.

Les ouvertures de connexion sont limitées à 1 000 par adresse IP et 60 par adresse et borne sur 15 minutes. Derrière un équilibreur, le serveur ne connaît l’adresse réelle de la borne que si `OCPP_TRUSTED_PROXIES` contient l’adresse du mandataire. Sinon, toutes les bornes partagent l’adresse de l’équilibreur et la limite de 1 000. Les éléments de `X-Forwarded-For` sous la forme `ip:port`, `[ipv6]:port` ou `for=` sont acceptés.

## Adresse des utilisateurs

Connexion, inscriptions, réinitialisation du mot de passe et paiement public par QR code sont limités par adresse IP. Ces requêtes passent par le tableau de bord, qui transmet `X-Forwarded-For` ; le serveur ne lit cet en-tête que si l’appelant appartient à `APP_TRUSTED_PROXIES`.

```text
APP_TRUSTED_PROXIES=<adresses du tableau de bord>,<plages de l’équilibreur placé devant lui>
```

Le mandataire placé devant le tableau de bord doit remplacer toute valeur de `X-Forwarded-For` et `X-Real-IP` envoyée par le client. N’ajoutez le tableau de bord à `APP_TRUSTED_PROXIES` que s’il est joignable uniquement à travers un tel mandataire : exposé directement, il laisserait un visiteur choisir l’adresse vue par le serveur. Sans `APP_TRUSTED_PROXIES`, tous les utilisateurs partagent l’adresse du tableau de bord : dix paiements publics en 15 minutes sur toute la plateforme suffisent alors à bloquer les suivants.

## Profils de sécurité OCPP

| Profil | Borne | Terminaison recommandée |
|---|---|---|
| 1 | `ws://` ou `wss://`, Basic | Comme le profil 2 ; évitez `ws://` hors réseau privé |
| 2 | `wss://`, Basic | TLS terminé par l’équilibreur, `X-Forwarded-Proto: https` |
| 3 | `wss://`, certificat client | mTLS vérifié par l’équilibreur, certificat transmis dans un en-tête |

Le serveur ne lit `X-Forwarded-Proto`, `X-Forwarded-For` et l’en-tête `OCPP_CLIENT_CERT_HEADER` que si l’adresse TCP de l’appelant appartient à `OCPP_TRUSTED_PROXIES`. Si `OCPP_CLIENT_CERT_PROXIES` est défini, l’en-tête du certificat n’est lu que depuis ces plages : réservez-les au mandataire qui vérifie les certificats, quand il dispose d’adresses distinctes. Ne mettez `0.0.0.0/0,::/0` que si le serveur n’est joignable que par l’équilibreur (Cloud Run, Container Apps, réseau privé filtré).

### A. TLS terminé par l’équilibreur (profils 1 et 2)

```text
OCPP_TRUSTED_PROXIES=<plages des adresses de l’équilibreur>
```

L’équilibreur termine TLS, ajoute `X-Forwarded-Proto: https` et l’adresse du client dans `X-Forwarded-For`, puis transmet en HTTP sur le réseau privé.

### B. mTLS vérifié par l’équilibreur (profil 3)

Règles obligatoires :

- l’équilibreur **vérifie lui-même** le certificat client contre l’autorité des bornes. Les modes qui laissent passer un certificat non vérifié sont interdits : AWS `passthrough`, nginx `optional_no_ca`, Caddy `request`, Azure Container Apps `accept`/`require` (aucune autorité configurable) ;
- son magasin de confiance contient les autorités de `OCPP_CLIENT_CA` et, si le serveur signe les demandes des bornes, `OCPP_SIGNING_CA_CERTIFICATE` ;
- il **remplace** toute copie de l’en-tête du certificat, de `X-Forwarded-For` et de `X-Forwarded-Proto` envoyée par le client ; sur `APP_HOST`, il supprime l’en-tête du certificat.

Le serveur vérifie à nouveau la chaîne contre `OCPP_CLIENT_CA` (et `OCPP_SIGNING_CA_CERTIFICATE`, approuvée automatiquement), la validité, puis le CN égal à l’identifiant de la borne ou l’empreinte SHA-256 épinglée.

```text
OCPP_TRUSTED_PROXIES=<plages des adresses de l’équilibreur>
OCPP_CLIENT_CERT_PROXIES=<plages du seul mandataire mTLS, si distinctes>
OCPP_CLIENT_CERT_HEADER=<nom de l’en-tête>
OCPP_CLIENT_CA=<autorités des bornes, intermédiaires compris>
```

Quand le mandataire ne transmet que le certificat final, placez aussi les autorités intermédiaires dans `OCPP_CLIENT_CA`.

| Mandataire | Mode | En-tête et valeur | Encodage |
|---|---|---|---|
| Caddy (`deploy/`) | `require_and_verify` | `X-Client-Cert: {http.request.tls.client.certificate_der_base64}` | DER Base64, certificat final |
| nginx | `ssl_verify_client on` | `X-Client-Cert: $ssl_client_escaped_cert` | PEM encodé URL |
| Google Cloud Application LB | `REJECT_INVALID` | `X-Client-Cert: {client_cert_leaf},{client_cert_chain}` | RFC 9440, DER Base64 entre deux-points |
| AWS ALB | `verify` | `X-Amzn-Mtls-Clientcert-Leaf` (ajouté par l’ALB) | PEM encodé URL |
| Azure Application Gateway v2 | strict | réécriture `X-Client-Cert: {var_client_certificate}` | PEM |
| OCI Load Balancer | vérification du pair | règle `X-Client-Cert: {oci_lb_client_cert_url_encoded}` | PEM encodé URL |

Le serveur reconnaît aussi `X-Forwarded-Client-Cert` (Envoy, `Cert="…"`).

### C. TCP de bout en bout, TLS terminé par Spring Boot

L’équilibreur transmet le flux TCP sans le déchiffrer ; le serveur Java présente son certificat et demande celui de la borne :

```text
SERVER_SSL_ENABLED=true
SERVER_SSL_CERTIFICATE=/etc/ev/tls/fullchain.pem
SERVER_SSL_CERTIFICATE_PRIVATE_KEY=/etc/ev/tls/privkey.pem
SERVER_SSL_TRUST_CERTIFICATE=/etc/ev/tls/ocpp-client-ca.pem
SERVER_SSL_CLIENT_AUTH=want
```

Tout le port du serveur passe alors en HTTPS : `EV_SERVER_URL` doit viser un nom couvert par ce certificat, et le renouvellement du certificat demande un redémarrage. L’adresse réelle de la borne n’est connue que si l’équilibreur la conserve (pas de protocole PROXY côté Java). Réservez cette option aux cas où aucun mandataire ne peut vérifier le certificat ; la variante recommandée est un Caddy ou un nginx derrière un équilibreur TCP (option B).

## Mise à l’échelle

**Limite actuelle : une seule réplique du serveur Java.** Les connexions WebSocket sont tenues en mémoire, et chaque instance exécute toutes les tâches planifiées :

- une borne reconnectée sur une autre instance peut être marquée hors ligne par la fermeture tardive de l’ancienne connexion ; la déconnexion forcée (désactivation, suppression, rotation du mot de passe) n’atteint que les bornes de l’instance qui la reçoit ;
- les envois OCPI, les calculs de recharge intelligente et les relevés d’équipements seraient faits plusieurs fois.

Dimensionnez donc verticalement et déployez avec la stratégie « recréer » (Kubernetes `Recreate`, une tâche à la fois ailleurs). Le tableau de bord, sans état, peut avoir plusieurs répliques.

| Parc indicatif | Serveur | Tableau de bord | PostgreSQL |
|---|---|---|---|
| Essai, moins de 50 bornes | 1 vCPU, 1 Go | 0,5 vCPU, 512 Mo | 1 vCPU, 2 Go, 10 Go |
| Quelques centaines de bornes | 2 vCPU, 2 à 4 Go | 1 vCPU, 512 Mo | 2 vCPU, 4 à 8 Go, haute disponibilité |

Ces valeurs sont des points de départ, sans campagne de charge publiée : surveillez processeur, mémoire, latence des réponses OCPP et connexions à la base. Une machine virtuelle unique avec PostgreSQL géré est souvent le meilleur rapport coût, simplicité et stabilité des connexions.

## Mises à jour et arrêts

À l’arrêt, le serveur termine les requêtes en cours (20 s au plus par phase), puis ferme chaque connexion OCPP avec le code 1012 (redémarrage du service) : les bornes se reconnectent selon leur propre délai. Les messages qu’elles mettent en file (transactions, relevés) sont rejoués ; les doublons sont reconnus. Planifiez les mises à jour hors des pointes et laissez au moins 40 s d’arrêt gracieux (`stop_grace_period`, `terminationGracePeriodSeconds`). Pour limiter l’interruption, construisez et poussez l’image avant de remplacer le conteneur.

Les migrations Flyway s’appliquent au démarrage. Sauvegardez la base avant chaque version qui en contient.

## Sauvegardes

- Base : sauvegardes automatiques quotidiennes du service géré, restauration à un instant donné sur 7 jours au moins, et une copie exportée hors du compte (`pg_dump` chiffré ou réplication inter-régions).
- `APP_ENCRYPTION_KEY`, `OCPP_SIGNING_CA_KEY` et les autorités des bornes : coffre du fournisseur, plus une copie hors ligne.
- Une restauration est essayée au moins une fois par trimestre sur un environnement séparé.

## Observabilité

- Santé du serveur, depuis le réseau privé seulement : `GET /actuator/health/liveness` (le processus répond) et `GET /actuator/health/readiness` (prêt à recevoir du trafic, sans contrôle de la base). `GET /actuator/health` inclut l’état de la base : utilisez-le pour la supervision, pas pour la sonde de vivacité, afin qu’une bascule de la base ne provoque pas de redémarrage. Pour le tableau de bord, une vérification TCP du port 3000 ou `GET /auth/login`.
- Journaux : sortie standard. `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` (ou `logstash`, `gelf`) produit du JSON lisible par Cloud Logging, CloudWatch, Azure Monitor, Cockpit ou OCI Logging.
- Alertes conseillées : santé en échec, redémarrages du conteneur, mémoire au-delà de 85 %, espace et connexions de la base, absence de journaux OCPP pendant plusieurs minutes, échecs de livraison des notifications Stripe.
- Côté métier, le tableau de bord signale déjà les bornes hors ligne, les commandes expirées et les refus de connexion.

## Stripe

La destination de notifications vise `https://APP_HOST/api/backend/api/v1/stripe/webhook`, avec les événements listés dans le [README](../../README.md#paiement-public-et-qr-code). Le corps doit arriver intact : aucun pare-feu applicatif ne doit le réécrire. Les appels sortants vers `api.stripe.com` doivent être autorisés.

## Courriel

Le port 25 sortant est bloqué ou restreint chez la plupart des fournisseurs. Utilisez un relais SMTP authentifié sur 587 avec STARTTLS (Amazon SES, Azure Communication Services, Scaleway Transactional Email, OCI Email Delivery, ou un prestataire externe) et déclarez SPF, DKIM et DMARC pour `MAIL_FROM`.

## Variables d’environnement

| Variable | Composant | Secret | Valeur en production |
|---|---|---|---|
| `PORT` | serveur | | `8080` |
| `JAVA_TOOL_OPTIONS` | serveur | | `-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError` |
| `DATABASE_URL`, `DATABASE_USER` | serveur | | base gérée |
| `DATABASE_PASSWORD` | serveur | oui | |
| `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` | serveur | | 12 par défaut |
| `APP_PUBLIC_URL` | serveur, tableau de bord | | `https://APP_HOST`, identique des deux côtés |
| `COOKIE_SECURE` | serveur | | `true` |
| `EV_SERVER_URL` | tableau de bord | | adresse privée du serveur, par exemple `http://server:8080` |
| `BOOTSTRAP_TENANT`, `BOOTSTRAP_EMAIL` | serveur | | premier démarrage seulement |
| `BOOTSTRAP_PASSWORD` | serveur | oui | à retirer après création du compte |
| `MAIL_ENABLED`, `MAIL_FROM`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_AUTH`, `SMTP_STARTTLS` | serveur | | relais SMTP 587 |
| `SMTP_PASSWORD` | serveur | oui | |
| `WORKSPACE_SIGNUP_ENABLED`, `SIGNUP_ENABLED`, `PUBLIC_TENANT` | serveur | | `false` par défaut |
| `STRIPE_PUBLISHABLE_KEY` | serveur | | |
| `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `APP_ENCRYPTION_KEY` | serveur | oui | |
| `PLATFORM_ADMIN_EMAILS` | serveur | | |
| `OCPI_PUBLIC_URL` | serveur | | `https://APP_HOST` (les routes `/ocpi/*` y sont publiées) |
| `OCPI_COUNTRY_CODE`, `OCPI_PARTY_ID` | serveur | | |
| `EV_DATABASE_API_KEY` | serveur | oui | facultatif |
| `APP_TRUSTED_PROXIES` | serveur | | tableau de bord et équilibreur placé devant lui |
| `OCPP_TRUSTED_PROXIES` | serveur | | plages de l’équilibreur ou du mandataire |
| `OCPP_CLIENT_CERT_HEADER` | serveur | | profil 3 derrière un mandataire |
| `OCPP_CLIENT_CERT_PROXIES` | serveur | | mandataire mTLS seul ; vide : `OCPP_TRUSTED_PROXIES` |
| `OCPP_CLIENT_CA` | serveur | | autorités des bornes, profil 3 |
| `OCPP_SIGNING_CA_CERTIFICATE` | serveur | | signature des CSR des bornes |
| `OCPP_SIGNING_CA_KEY` | serveur | oui | clé PKCS#8 |
| `OCPP_SIGNED_CERTIFICATE_DAYS` | serveur | | 365 |
| `OCPP_ALLOW_UNAUTHENTICATED` | serveur | | `false` |
| `OCPP_HEARTBEAT_SECONDS` | serveur | | 60 (10 à 300) |
| `OCPP_PUBLIC_URL` | serveur | | adresse `wss://…/ocpp` affichée dans la fiche de mise en service des bornes |
| `SERVER_SSL_*` | serveur | clé | option C seulement |
| `LOGGING_STRUCTURED_FORMAT_CONSOLE` | serveur | | `ecs`, `logstash` ou `gelf` |

## Machine virtuelle unique (Docker Compose)

Une VM Linux de 2 vCPU et 4 Go avec Docker Engine et le greffon Compose suffit pour démarrer. Ouvrez les ports 80 et 443 ; les noms `APP_HOST` (et `OCPP_HOST` pour le profil 3) doivent pointer vers son adresse publique avant le premier démarrage, pour l’émission des certificats Let’s Encrypt.

```sh
git clone <dépôt> ev-supervision && cd ev-supervision/deploy
cp .env.example .env
cp server.env.example server.env
# Renseigner .env et server.env, puis :
docker compose up -d --build
docker compose logs -f server
```

- `COMPOSE_PROFILES=local-db` démarre PostgreSQL dans un volume local. Avec une base gérée, retirez ce profil et définissez `DATABASE_URL`.
- Caddy (`deploy/caddy/Caddyfile`) obtient et renouvelle les certificats, transmet WebSocket et remplace `X-Forwarded-For` et `X-Forwarded-Proto`. Le serveur ne fait confiance qu’aux adresses fixes de Caddy (`172.30.0.10`, pour OCPP) et du tableau de bord (`172.30.0.30`, pour `APP_TRUSTED_PROXIES`).
- Profil 3 : copiez `caddy/sites/ocpp-mtls.caddy.example` en `caddy/sites/ocpp-mtls.caddy`, placez `caddy/sites/ocpp-client-ca.pem`, renseignez `OCPP_CLIENT_CA` (même contenu) et redémarrez Caddy.
- Mise à jour : `git pull && docker compose up -d --build`.
- Un nginx existant peut remplacer Caddy : voir `deploy/nginx/ev-supervision.conf`.

Sauvegardez le volume `postgres-data` si la base est locale (`docker compose exec postgres pg_dump -U ev_supervision ev_supervision`), ou préférez une base gérée.

## Kubernetes

`deploy/kubernetes/` fonctionne sur GKE, EKS, AKS, Kapsule et OKE :

```sh
kubectl create namespace ev-supervision
kubectl -n ev-supervision create secret generic ev-server-env --from-env-file=server.env
kubectl apply -k deploy/kubernetes
kubectl -n ev-supervision get service edge
```

Avant l’application, remplacez `REGISTRE` et les étiquettes dans la section `images` de `kustomization.yaml`, ainsi que les valeurs de `edge`. `server.env` contient ici toutes les variables du serveur, y compris `DATABASE_*`, `APP_PUBLIC_URL`, `OCPI_PUBLIC_URL`, `OCPP_TRUSTED_PROXIES` et `APP_TRUSTED_PROXIES`.

- Le serveur a une réplique et la stratégie `Recreate`. Le tableau de bord en a deux. Les sondes utilisent `/actuator/health/liveness` et `/actuator/health/readiness`.
- Le Service `edge` crée un équilibreur **TCP** devant Caddy : TLS et mTLS sont identiques partout. `externalTrafficPolicy: Local` conserve l’adresse des bornes quand l’équilibreur le permet ; sinon activez le protocole PROXY v2 sur l’équilibreur et dans Caddy (`listener_wrappers { proxy_protocol { allow <plage> } tls }`).
- `OCPP_TRUSTED_PROXIES`, `OCPP_CLIENT_CERT_PROXIES` et `APP_TRUSTED_PROXIES` visent la plage des pods, les adresses des pods n’étant pas fixes ; la NetworkPolicy fournie n’autorise vers le serveur que Caddy et le tableau de bord. Vérifiez que le plugin réseau du cluster applique les NetworkPolicy.
- Caddy conserve ses certificats dans un volume `ReadWriteOnce` : une seule réplique.
- Le projet ingress-nginx n’est plus maintenu depuis mars 2026 ([annonce](https://kubernetes.io/blog/2025/11/11/ingress-nginx-retirement/)) : ne l’utilisez pas pour une nouvelle installation.

## Application mobile iOS

L’application dialogue directement avec le serveur Java : le mandataire doit transmettre `/api/v1/*` au serveur (déjà fait dans `deploy/caddy`, `deploy/kubernetes` et `deploy/nginx`) et son adresse doit figurer dans `APP_TRUSTED_PROXIES`, sinon tous les téléphones partagent la même limite de débit. Le guide de configuration et de publication est dans [`ev-app-ios/README.md`](../../ev-app-ios/README.md).

Pour les liens universels, définissez `APPLE_APP_IDS` sur le tableau de bord (`EQUIPE.identifiant.application`, séparés par des virgules).

## Application mobile Android

Les règles du mandataire et de `APP_TRUSTED_PROXIES` sont les mêmes que pour iOS. Le guide de configuration et de publication est dans [`ev-app-android/README.md`](../../ev-app-android/README.md).

- **Notifications (FCM)** : dans la console Firebase, ouvrez **Paramètres du projet > Comptes de service** et générez une clé. Renseignez `FCM_SERVICE_ACCOUNT` (JSON, Base64 ou chemin de fichier monté). `FCM_PROJECT_ID` n’est utile que pour remplacer le projet de cette clé. Le serveur sort `https://fcm.googleapis.com` et `https://oauth2.googleapis.com`.
- **Google Wallet** : dans la Pay & Wallet Console, relevez l’identifiant d’émetteur (`GOOGLE_WALLET_ISSUER_ID`) et autorisez un compte de service (`GOOGLE_WALLET_SERVICE_ACCOUNT`). Le NFC demande l’accès Smart Tap accordé par Google et `GOOGLE_WALLET_SMART_TAP_REDEMPTION_ISSUER`. Vide : le passe présente seulement un QR code.
- **Liens d’application** : définissez `ANDROID_APP_LINKS` sur le tableau de bord (`paquet:EMPREINTE_SHA256`, séparés par des virgules). Le fichier est servi à `/.well-known/assetlinks.json`. Avec Play App Signing, utilisez l’empreinte indiquée par la console Play.

La migration `V10__push_device_android.sql` agrandit la colonne des jetons d’appareil. Elle s’applique au démarrage.

## Vérifications après mise en service

```sh
curl -sSI https://APP_HOST/auth/login
curl -sS -o /dev/null -w '%{http_code}\n' https://APP_HOST/actuator/health
curl --http1.1 -sS -o /dev/null -w '%{http_code}\n' \
  -H 'Connection: Upgrade' -H 'Upgrade: websocket' -H 'Sec-WebSocket-Version: 13' \
  -H 'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==' -H 'Sec-WebSocket-Protocol: ocpp1.6' \
  https://APP_HOST/ocpp/ESPACE/BORNE-INCONNUE
```

1. La page de connexion répond 200 ; `/actuator/health` répond 404 depuis Internet.
2. L’ouverture WebSocket sans identifiants répond 401 et un événement `CONNECTION_REJECTED` apparaît si la borne existe.
3. Une borne de test reste connectée plus d’une heure sans reconnexion dans son historique.
4. Profil 3 : une connexion sans certificat est refusée par le mandataire ; une requête vers `APP_HOST` avec un faux en-tête `X-Client-Cert` n’élève pas la borne au profil 3.
5. Une notification Stripe de test est reçue (tableau de bord Stripe, onglet des livraisons).
6. Un courriel de réinitialisation du mot de passe arrive.
