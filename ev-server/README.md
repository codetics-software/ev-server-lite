# Serveur Codetics Supervision Lite

Serveur Kotlin et Spring Boot. Les contrôleurs HTTP délèguent aux services métier ; les modèles persistés et les dépôts JPA sont regroupés dans leurs dossiers respectifs.

Le [guide principal](../README.md) décrit le démarrage Java, PostgreSQL, les espaces clients, Stripe et les connexions OCPP. Les variables sont documentées dans [.env.example](.env.example).

## Langues

L’API répond en français, en anglais ou en espagnol selon l’en-tête `Accept-Language` (`fr`, `en`, `es`) : messages d’erreur (`ProblemDetail`), libellés et textes générés pendant la requête. Sans en-tête ou pour une autre langue, elle répond en français. Une erreur de connexion due à une adresse non vérifiée porte en plus `code: "email_not_verified"`.

Chaque utilisateur a une langue préférée, champ `locale` (`fr`, `en` ou `es`) de `GET /api/v1/account`, modifiable par `PUT /api/v1/account`. Elle est fixée à l’inscription puis mise à jour à chaque connexion d’après `Accept-Language`, et sert aux courriels, aux notifications push et aux factures envoyés hors requête. Les textes se trouvent dans `src/main/resources/messages*.properties` ; les trois fichiers doivent garder les mêmes clés.

Vérification : `./mvnw test`. Construction de l’application Java : `./mvnw verify`.
