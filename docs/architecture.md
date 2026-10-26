# Architecture

Le serveur Kotlin possède les données, les autorisations et les états de recharge. Le tableau de bord Next.js communique avec son API par un mandataire côté serveur. Les fonctionnalités du tableau de bord sont regroupées dans `features/`, sans logique métier dans les fichiers de route.

## Séparation des responsabilités

Les **contrôleurs** traduisent les requêtes HTTP. Les **services** appliquent les règles métier et les limites des espaces clients. Les **dépôts** interrogent la base. Les **modèles** distinguent les entités persistées des objets exposés par l’API. L’adaptateur Stripe et les échanges OCPP possèdent leurs propres dossiers.

Les services applicatifs déduisent l’espace depuis le compte authentifié. Ils recherchent les objets avec cet identifiant ; un identifiant d’objet appartenant à un autre espace ne donne aucun accès. Les requêtes des conducteurs sont aussi limitées à leurs propres sessions.

La connexion utilise l’identifiant permanent de l’espace. La création d’un espace produit un administrateur à vérifier par courriel. Aucun routage par domaine n’est nécessaire.

## États des bornes

La connexion WebSocket de la borne, l’état d’un connecteur, une autorisation et une transaction sont des faits distincts. Les commandes possèdent un identifiant de corrélation, une échéance et un résultat persisté. Une commande acceptée ne constitue pas une preuve de démarrage.

Les messages OCPP reçus sont dédupliqués par espace, borne et identifiant de message, dans la limite d’une connexion, comme le prévoit OCPP-J. Sur une même connexion, un message répété à l’identique reçoit la réponse enregistrée ; un identifiant réutilisé avec un autre contenu est refusé (`ProtocolError`). Après une reconnexion, seuls `StartTransaction`, `StopTransaction` et `MeterValues` identiques sont rejoués ; tout autre message est traité comme nouveau, car de nombreuses bornes recomptent leurs identifiants depuis 1 au redémarrage. Un `StartTransaction` renvoyé sous un nouvel identifiant (même connecteur, badge, horodatage et index) reçoit la session déjà ouverte. Les mesures conservent leurs horodatages et leurs échantillons d’origine.

Les écarts courants des bornes du marché sont tolérés : horodatages sans fuseau (lus en UTC) ou avec un décalage `+0100`, valeurs de mesure numériques, index décimaux, fabricant et modèle jusqu’à 50 caractères, en-tête `Sec-WebSocket-Protocol` absent. Un code d’erreur inconnu est enregistré comme `OtherError`, le code d’origine étant conservé dans l’événement.

## Sécurité des bornes OCPP

Chaque borne a un profil de sécurité OCPP 1.6 (1 par défaut). Une borne peut se connecter avec un profil plus fort que le sien, jamais plus faible.

| Profil | Exigence |
|---|---|
| 0 | Aucune authentification. Réservé aux bornes existantes placées dans ce profil, et seulement si `OCPP_ALLOW_UNAUTHENTICATED=true`. |
| 1 | Authentification HTTP Basic : identifiant de la borne et mot de passe (`AuthorizationKey`, 40 caractères hexadécimaux). |
| 2 | Profil 1 sur TLS, terminé par le serveur ou par un mandataire de `OCPP_TRUSTED_PROXIES` qui envoie `X-Forwarded-Proto: https`. |
| 3 | TLS et certificat client. La chaîne est vérifiée par rapport à `OCPP_CLIENT_CA` et à l’autorité de signature ; le nom commun doit être l’identifiant de la borne, ou l’empreinte SHA-256 doit être celle épinglée. |

Derrière un mandataire, le certificat client est lu dans l’en-tête `OCPP_CLIENT_CERT_HEADER`, seulement si le pair TCP figure dans `OCPP_CLIENT_CERT_PROXIES` (à défaut `OCPP_TRUSTED_PROXIES`). Ce mandataire doit lui-même vérifier le certificat et écraser toute copie envoyée par le client. Un refus de connexion d’une borne connue est consigné dans ses événements (`CONNECTION_REJECTED`) avec son motif, sans aucun secret.

Un administrateur relève le profil à distance : le serveur envoie `ChangeConfiguration SecurityProfile`, continue d’accepter l’ancien profil et n’applique le nouveau qu’à la première connexion qui l’atteint. Un refus ou une absence de réponse annule la demande. Un abaissement ne s’applique que côté serveur, la borne étant à reconfigurer sur place.

Le renouvellement à distance du mot de passe envoie `ChangeConfiguration AuthorizationKey`. L’ancien et le nouveau mot de passe restent acceptés jusqu’à la reconnexion avec le nouveau, ou jusqu’à la révocation explicite de l’ancien. Le nouveau mot de passe n’est affiché qu’une fois et n’est conservé ni dans les commandes ni dans les paramètres de la borne. `SecurityProfile` et `AuthorizationKey` ne peuvent pas être modifiés par les commandes génériques ni par les modèles de borne.

Avec `OCPP_SIGNING_CA_CERTIFICATE` et `OCPP_SIGNING_CA_KEY`, le serveur signe les demandes `SignCertificate` dont le nom commun est l’identifiant de la borne, puis envoie `CertificateSigned`. Seul le dernier certificat accepté par la borne reste valide ; il est révocable depuis le tableau de bord et invalidé à la suppression de la borne. Les administrateurs sont prévenus 30 jours avant l’expiration d’un certificat connu et lors des événements de sécurité critiques (`SecurityEventNotification`).

Les relevés signés (`format: SignedData`, par exemple OCMF pour l’Eichrecht) de début et de fin de session sont conservés tels quels pour une vérification par logiciel de transparence ; une valeur OCMF sert d’index d’énergie lorsqu’aucune valeur brute n’est transmise. Les `DataTransfer` des fournisseurs déclarés sur la borne sont acceptés, les autres reçoivent `UnknownVendorId` ; tous sont consignés.

## Paiement public

Un point de charge publié relie explicitement un espace, une borne, un connecteur et un tarif. Le QR code contient l’adresse de son portail. Le prix et le compte Stripe sont choisis par le serveur, jamais par le navigateur.

Une demande de paiement contient un crédit et une clé de reprise. Le serveur conserve un instantané du tarif, une empreinte du secret de suivi et une copie chiffrée de ce secret pour reprendre la création du paiement après une interruption. L’accès public à la session exige ce secret dans un en-tête ; il n’accorde aucun accès au tableau de bord.

La notification Stripe signée doit correspondre au compte connecté, à la session Checkout, à la devise et au montant attendus. Les notifications sont dédupliquées. Après paiement, un badge temporaire limité à cette borne et à ce connecteur sert à demander le démarrage. Le démarrage réel consomme cette autorisation.

Le suivi de consommation et de durée demande l’arrêt à l’épuisement du crédit. Le relevé final déclenche le calcul et le remboursement du solde. Les opérations Stripe sont reprises avec des clés d’idempotence ; le suivi ne prétend jamais qu’une borne est arrêtée avant sa confirmation.

## Recharge automatique (AutoCharge)

La borne identifie le véhicule par son adresse MAC (EVCCID), transmise en OCPP 1.6 sous la forme `VID:92D50A693F80` dans `Authorize` puis `StartTransaction`. Cet identifiant n’est pas un secret : il peut être copié. La fonction est donc désactivée par défaut et entourée de garde-fous.

- Un administrateur l’active borne par borne. Le serveur envoie alors la clé `AutoChargeEnabled` à la borne, et la resynchronise au démarrage. Le serveur refuse les identifiants `VID:` d’une borne désactivée, quel que soit son paramétrage.
- Un `Authorize` n’est accepté que dans les 30 secondes qui suivent le branchement observé sur un connecteur. Un `StartTransaction` doit concerner le connecteur ayant reçu cet identifiant, ou arriver dans les deux minutes suivant le branchement. Le débranchement efface l’identifiant observé.
- Un conducteur enregistre son véhicule à partir d’une de ses sessions des dernières 24 heures, pendant laquelle la borne a transmis l’identifiant. Seul un administrateur peut saisir une adresse à la main. Un identifiant ne peut appartenir qu’à un seul véhicule.
- L’identifiant enregistré est un badge de type `VID` lié à un utilisateur et à un véhicule. Il suit les mêmes règles que les autres badges (activation, expiration, contrôle d’accès du site) et ne passe jamais par l’itinérance. Il est révoqué à la suppression du véhicule ou de l’utilisateur, ainsi qu’au changement de propriétaire du véhicule.
- Un identifiant inconnu est refusé sans notification. La borne revient au parcours habituel : badge, application ou QR code.

## Persistance

La base de production est PostgreSQL ou MongoDB, choisie au démarrage d’après le schéma de `DATABASE_URL`. Les deux partagent les mêmes entités, les mêmes dépôts Spring Data et la même API : seules la configuration et quelques opérations du paquet `persistence` diffèrent.

Avec PostgreSQL, les migrations Flyway créent les tables et les contraintes ; Hibernate valide le schéma. Les verrous de ligne sérialisent les opérations concurrentes sur une borne, un paiement ou une commande.

Avec MongoDB, chaque table devient une collection du même nom et les clés composées deviennent des `_id` structurés. Les contraintes d’unicité sont recréées par des index au démarrage. Les montants sont stockés en `Decimal128`, les identifiants numériques des sessions et des journaux par des compteurs. Un verrou est une écriture dans la transaction : une modification concurrente provoque un conflit, et l’opération est rejouée. Les dates sont conservées à la milliseconde.

Les montants et les mesures utilisent des nombres décimaux. Les clés d’espace restent présentes dans les relations métier.

Les historiques de session et les documents importés sont conservés. L’import d’archives Mongo Extended JSON préserve le document UTF-8 d’origine et son empreinte SHA-256. Il ne transforme pas automatiquement une archive en données opérationnelles : cette reprise exige un mappage adapté aux collections d’origine.

## Vérification

Le profil H2 sert aux tests locaux des dépôts, des contrôleurs et des échanges de session. Le paiement y utilise un adaptateur de test. Les captures sont produites depuis l’application réelle avec ce serveur local de test. La même suite s’exécute sur PostgreSQL ou MongoDB en définissant `DATABASE_URL`. Les paiements Stripe réels et les modèles de bornes déployés doivent également être essayés dans l’environnement cible.
