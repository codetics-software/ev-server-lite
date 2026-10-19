# Authentification

L’installation utilise une adresse publique de tableau de bord. Next.js transmet les cookies et les en-têtes nécessaires au serveur Java privé. Les secrets des fournisseurs de connexion restent sur le serveur.

## Comptes et espaces

La connexion par mot de passe exige l’identifiant de l’espace client. Les sessions utilisent des valeurs aléatoires opaques ; seules leurs empreintes SHA-256 sont conservées en base. Les cookies sont protégés par `HttpOnly`, `Secure` en production et `SameSite=Lax`.

Les nouveaux mots de passe utilisent PBKDF2. La vérification d’anciens mots de passe BCrypt reste disponible. Les liens de vérification d’adresse et de réinitialisation sont utilisables une seule fois pendant trente minutes.

Les rôles administrateur, exploitant, lecteur et conducteur sont distincts des rôles de marché OCPI. Les utilisateurs et espaces désactivés sont contrôlés à chaque authentification. Une modification des droits, une déconnexion globale ou une réinitialisation du mot de passe révoque les sessions concernées.

La création publique d’un espace exige `WORKSPACE_SIGNUP_ENABLED=true`, l’activation des courriels et un serveur SMTP. Le compte administrateur créé doit vérifier son adresse avant la connexion. L’inscription libre de conducteurs est distincte : elle exige `SIGNUP_ENABLED=true` et l’espace cible `PUBLIC_TENANT`.

## Fournisseurs facultatifs

| Fournisseur | Variables d’environnement | Adresse de retour |
| --- | --- | --- |
| Google | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | `/login/oauth2/code/google` |
| Microsoft | `MICROSOFT_CLIENT_ID`, `MICROSOFT_CLIENT_SECRET`, `MICROSOFT_TENANT_ID` | `/login/oauth2/code/microsoft` |
| Apple | `APPLE_CLIENT_ID`, `APPLE_CLIENT_SECRET` | `/login/oauth2/code/apple` |

Enregistrez l’adresse de retour complète sous `APP_PUBLIC_URL` chez chaque fournisseur. Un fournisseur non configuré n’apparaît pas à la connexion. Spring Security contrôle l’état OAuth ainsi que la signature, l’émetteur, l’audience, la validité et le nonce des jetons OIDC. Google et Microsoft utilisent PKCE.

Microsoft exige l’identifiant UUID d’un annuaire Entra précis. Une identité Microsoft sans adresse électronique vérifiée doit être associée depuis un compte déjà authentifié.

Le secret client Apple est un JWT signé avec votre clé ES256, votre équipe Apple, votre identifiant de clé et votre identifiant de service. Conservez la clé privée hors du dépôt et renouvelez le JWT avant expiration. Configurez une adresse de retour HTTPS, `COOKIE_SECURE=true` et `OAUTH_COOKIE_SAME_SITE=none` pour le cookie temporaire OAuth : Apple renvoie une requête POST depuis un autre site. Le profil HTTP local ne valide pas ce parcours Apple réel.

## Association d’un compte externe

Une identité externe est identifiée par son émetteur et son sujet. Une adresse électronique identique ne suffit pas à associer deux comptes. Connectez-vous d’abord, puis utilisez **Mon compte → Associer**. Le retour du fournisseur vérifie que la session d’origine est toujours valide.

L’inscription publique via un fournisseur exige l’activation explicite de l’inscription, l’espace `PUBLIC_TENANT` et une adresse vérifiée. Elle crée uniquement un conducteur. Le fournisseur ne choisit ni l’espace ni le rôle.

## Requêtes du navigateur

Les modifications exigent le jeton CSRF obtenu par `GET /api/v1/auth/csrf`, avec le cookie correspondant. Le mandataire Next.js contrôle aussi l’origine de la requête.

Deux routes ont une protection distincte : le retour Apple, contrôlé par l’état OAuth et le nonce, et la notification Stripe, contrôlée par sa signature. Ces exceptions ne désactivent pas la protection CSRF des autres routes.

Le suivi public d’une recharge utilise un secret aléatoire propre à la session, envoyé dans `X-Charge-Token`. Ce secret est conservé dans la mémoire de session du navigateur et ne permet pas de se connecter au tableau de bord.
