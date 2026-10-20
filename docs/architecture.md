# Architecture

Le serveur Kotlin possède les données, les autorisations et les états de recharge. Le tableau de bord Next.js communique avec son API par un mandataire côté serveur. Les fonctionnalités du tableau de bord sont regroupées dans `features/`, sans logique métier dans les fichiers de route.

## Séparation des responsabilités

Les **contrôleurs** traduisent les requêtes HTTP. Les **services** appliquent les règles métier et les limites des espaces clients. Les **dépôts** interrogent la base. Les **modèles** distinguent les entités persistées des objets exposés par l’API. L’adaptateur Stripe et les échanges OCPP possèdent leurs propres dossiers.

Les services applicatifs déduisent l’espace depuis le compte authentifié. Ils recherchent les objets avec cet identifiant ; un identifiant d’objet appartenant à un autre espace ne donne aucun accès. Les requêtes des conducteurs sont aussi limitées à leurs propres sessions.

La connexion utilise l’identifiant permanent de l’espace. La création d’un espace produit un administrateur à vérifier par courriel. Aucun routage par domaine n’est nécessaire.

## États des bornes

La connexion WebSocket de la borne, l’état d’un connecteur, une autorisation et une transaction sont des faits distincts. Les commandes possèdent un identifiant de corrélation, une échéance et un résultat persisté. Une commande acceptée ne constitue pas une preuve de démarrage.

Les messages OCPP reçus sont dédupliqués par espace, borne et identifiant de message. La réutilisation d’un identifiant avec un contenu différent est refusée. Les mesures conservent leurs horodatages et leurs échantillons d’origine.

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
