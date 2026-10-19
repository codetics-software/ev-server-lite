# Codetics Supervision Lite

Une supervision de recharge volontairement légère : un serveur **Kotlin / Spring Boot**, un tableau de bord **Next.js**, une base **PostgreSQL**. Développée par Kevin Vavelin, [Codetics](https://codetics.fr).

Le produit couvre les bornes et connecteurs, les commandes OCPP 1.6, les sessions, les utilisateurs et badges RFID, les sites, les tarifs et le paiement public par QR code. L’enregistrement d’un véhicule reste facultatif. Pour des besoins de supervision plus avancés, contactez Codetics.

## Organisation

```text
ev-server/src/main/kotlin/fr/codetics/ev_server/
  controllers/     Points d’entrée HTTP par domaine
  services/        Règles de gestion par domaine
  models/          Entités persistées et modèles des échanges
  repositories/    Accès aux données JPA
  security/        Comptes, sessions et autorisations
  ocpp/            Échanges avec les bornes et file de commandes
  ocpi/            Interfaces d’itinérance OCPI 2.1.1
  stripe/          Adaptateur Stripe
  core/            Configuration et utilitaires communs

ev-dashboard/
  app/             Routes et mises en page Next.js
  features/        Écrans, composants et logique par fonctionnalité
  components/ui/   Composants d’interface partagés
  lib/             Client HTTP, formats et libellés français
```

## Démarrage local

Prérequis : **Java 25 ou supérieur**, **Node.js 22 ou supérieur**. Maven est fourni par le projet.

Dans un premier terminal :

```sh
cd ev-server
SPRING_PROFILES_ACTIVE=local \
BOOTSTRAP_EMAIL=admin@example.test \
BOOTSTRAP_PASSWORD='une-phrase-de-passe-locale-a-remplacer' \
./mvnw spring-boot:run
```

Dans un deuxième terminal :

```sh
cd ev-dashboard
npm ci
npm run dev
```

Ouvrez `http://localhost:3000/auth/login`. L’identifiant de l’espace initial est `codetics`. Le profil `local` utilise une base H2 en mémoire : ses données disparaissent à l’arrêt. Le compte initial est créé uniquement lorsque la base ne contient aucun utilisateur. Retirez ensuite ses identifiants de la configuration.

## Déploiement

Le serveur s’exécute comme une application Java classique ; le tableau de bord comme une application Node.js Next.js. Aucune plateforme d’hébergement particulière n’est imposée.

```sh
cd ev-server
./mvnw verify
java -jar target/ev-server-0.0.1-SNAPSHOT.jar
```

```sh
cd ev-dashboard
npm ci
npm run build
npm run start
```

Copiez les exemples [du serveur](ev-server/.env.example) et [du tableau de bord](ev-dashboard/.env.example) dans la configuration de votre hébergeur. Spring Boot ne charge pas automatiquement un fichier `.env` : transmettez ces valeurs comme variables d’environnement.

En production, utilisez PostgreSQL et HTTPS. Flyway applique les migrations ; Hibernate vérifie le schéma sans modifier les tables. Le mandataire inverse doit transmettre les connexions WebSocket OCPP au serveur Java. `EV_SERVER_URL` reste une variable privée du tableau de bord.

## Espaces clients

Tous les espaces partagent la même application et peuvent utiliser la même adresse web. La connexion demande explicitement l’identifiant de l’espace. Le serveur déduit l’espace autorisé de la session, jamais du nom de domaine ni d’un identifiant d’espace envoyé avec une opération métier.

Les rôles sont **administrateur**, **exploitant**, **lecteur** et **conducteur**. Les conducteurs consultent leurs propres sessions. Chaque espace possède ses bornes, ses utilisateurs, ses badges, ses tarifs et son compte Stripe.

La création d’espaces depuis `/auth/create-workspace` s’active avec `WORKSPACE_SIGNUP_ENABLED=true`, `MAIL_ENABLED=true` et un serveur SMTP. Le premier administrateur doit vérifier son adresse électronique. L’inscription libre de conducteurs dans l’espace défini par `PUBLIC_TENANT` est une option séparée, désactivée par défaut.

## Paiement public et QR code

1. Créez un tarif en euros dans **Tarifs**.
2. Dans **Paiements**, connectez le compte Stripe de l’espace et terminez la vérification chez Stripe. Activez les paiements publics.
3. Dans **Points de charge publics**, associez une borne, un connecteur physique et un tarif. Publiez le portail et téléchargez son QR code.
4. Le QR ouvre `/charge/{espace}/{point-de-charge}`. Le conducteur branche son véhicule, choisit un crédit entre 5 € et 100 € et paie via Stripe Checkout, sans créer de compte.

Les encaissements sont effectués directement sur le compte connecté de chaque espace. Le serveur attend une notification Stripe signée confirmant le paiement avant de demander le démarrage. Seul le message de démarrage de la borne confirme une recharge en cours.

Le tarif est figé au début de la demande. À la fin réelle de la recharge, le serveur calcule le montant à partir des mesures de la borne, plafonne la facturation au crédit payé et rembourse le solde. Un refus de démarrage ou l’absence de démarrage sous cinq minutes entraîne un remboursement intégral. Les reprises utilisent des clés d’idempotence Stripe.

À l’épuisement du crédit, le serveur demande l’arrêt. Cette commande dépend de la connexion et de la réponse de la borne : elle ne constitue pas une coupure physique garantie. Si la borne ne confirme pas l’arrêt, la session reste en attente du relevé final. Un remboursement refusé est signalé dans **Paiements** pour intervention de l’exploitant.

Configurez `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` et une clé `APP_ENCRYPTION_KEY` de 32 octets aléatoires encodés en Base64. Dans Stripe, créez une destination de notifications **pour les comptes connectés**, vers l’adresse publique `/api/backend/api/v1/stripe/webhook`, avec ces événements :

```text
checkout.session.completed
checkout.session.async_payment_succeeded
checkout.session.async_payment_failed
checkout.session.expired
```

L’adresse peut aussi viser directement `/api/v1/stripe/webhook` sur le serveur Java si celui-ci est exposé. Utilisez des clés et une destination de test pour les essais, puis les équivalents de production. Les paiements réels nécessitent un compte Stripe actif et une borne compatible ; les tests locaux utilisent un adaptateur réservé aux tests.

## Connexion des bornes

Ajoutez une borne dans le tableau de bord et conservez son mot de passe, affiché une seule fois. Configurez OCPP 1.6 JSON, le sous-protocole `ocpp1.6` et l’authentification Basic :

```text
wss://adresse-du-serveur/ocpp/{espace}/{identifiant-borne}
Authorization: Basic <base64(identifiant-borne:mot-de-passe)>
```

Les commandes sont persistées et suivies jusqu’à la réponse ou au dépassement de leur délai. Une réponse positive à une commande de démarrage ne prouve pas que le véhicule recharge.

## Vérifications et documentation

```sh
cd ev-server
./mvnw test
```

```sh
cd ev-dashboard
npm run lint
npm run format:check
npm run build
```

Les essais locaux couvrent les droits, l’isolation des espaces, les échanges de session et le cycle de paiement avec un adaptateur Stripe de test. Ils ne remplacent pas les essais PostgreSQL, Stripe et bornes de l’installation cible.

Consultez [l’architecture](docs/architecture.md), [l’authentification](docs/authentication.md) et les [captures d’écran en français](screenshots/README.md). La description de l’API est réservée aux administrateurs à `/api/v1/openapi` sur le serveur Java.

## Licence

Le code original est distribué sous la [licence d’attribution Codetics](LICENSE). Conservez le crédit visible à Kevin Vavelin, Codetics, ainsi que `NOTICE` et les licences des dépendances. Les textes juridiques tiers conservent leur version d’origine.
