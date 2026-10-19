# Serveur Codetics Supervision Lite

Serveur Kotlin et Spring Boot. Les contrôleurs HTTP délèguent aux services métier ; les modèles persistés et les dépôts JPA sont regroupés dans leurs dossiers respectifs.

Le [guide principal](../README.md) décrit le démarrage Java, PostgreSQL, les espaces clients, Stripe et les connexions OCPP. Les variables sont documentées dans [.env.example](.env.example).

Vérification : `./mvnw test`. Construction de l’application Java : `./mvnw verify`.
