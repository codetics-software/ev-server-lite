# Personnaliser la supervision en marque blanche

Ce guide explique comment présenter la supervision sous votre marque : nom, logo, couleurs, police, domaine, courriels et applications mobiles. La [licence d’attribution Codetics](../LICENSE) l’autorise, y compris pour un usage commercial. Elle impose une seule contrepartie : l’attribution reste présente et visible.

## Ce qui doit rester

Ces éléments ne se retirent pas, ne se masquent pas et ne se rendent pas illisibles (licence, sections 2 et 4) :

- Les fichiers `LICENSE`, `NOTICE`, `AGENTS.md`, le dossier `licenses/` et l’en-tête de licence de chaque fichier source.
- L’attribution visible, sur un écran accessible à tous les utilisateurs et pas seulement aux administrateurs :

      EV Supervision Lite — built by Kevin Vavelin, Codetics
      https://codetics.fr

  Elle figure dans l’écran **À propos** des applications mobiles et doit figurer en pied de page du tableau de bord, de l’écran de connexion et du portail de paiement par QR code.
- L’attribution des passes Apple Wallet et Google Wallet, de la réponse `/api/v1/auth/eula` du serveur et de la documentation de l’API.
- Les contrôles `scripts/verify-attribution.sh`, `verifyReleaseConfiguration` (Android) et `Config/verify-configuration.sh` (iOS). Ne les désactivez pas.

Vous pouvez restyler le pied de page avec vos couleurs et votre police, à condition que l’attribution reste lisible. Le nom et les logos de Codetics ne servent qu’à cette attribution : ne les utilisez pas comme votre marque, et ne laissez pas entendre que Codetics approuve votre service.

Pour retirer l’attribution, il faut des conditions écrites distinctes, à demander sur [codetics.fr](https://codetics.fr/fr/contact?project=mobility).

## 1. Sans toucher au code

Dans le tableau de bord, un administrateur de l’espace ouvre **Espace client** :

- **Nom de l’espace** : affiché dans la barre latérale et l’en-tête. L’espace initial créé par `BOOTSTRAP_TENANT` s’appelle « Codetics » : renommez-le ici.
- **Logo** : PNG, JPEG ou WebP de 256 Ko au plus. Il remplace le logo par défaut dans la barre latérale, sur l’écran de connexion lorsque l’identifiant de l’espace est saisi, et en tête du portail de paiement.

Créez un espace par client : chacun a son nom, son logo, son compte Stripe et ses utilisateurs.

Côté serveur, choisissez aussi :

| Variable | Rôle |
|---|---|
| `BOOTSTRAP_TENANT` | Identifiant de l’espace initial, saisi à la connexion. Remplacez `codetics` par le vôtre avant le premier démarrage. |
| `APP_PUBLIC_URL` (serveur et tableau de bord) | Votre domaine, utilisé par les QR codes et les liens d’application. |
| `MAIL_FROM` | Adresse d’envoi des courriels, sur votre domaine. |
| `WALLET_ORGANIZATION_NAME`, `GOOGLE_WALLET_ORGANIZATION_NAME` | Nom de votre organisation sur les passes Wallet. |

## 2. Tableau de bord

Les textes « Codetics » visibles sans logo d’espace sont écrits dans le code. Pour les retrouver :

```sh
grep -rn "Codetics" ev-dashboard/app ev-dashboard/features ev-dashboard/components
```

| Fichier | À changer | À garder |
|---|---|---|
| `app/layout.tsx` | `title` et `description` de la page | |
| `app/favicon.ico` | Icône de l’onglet | |
| `public/codetics-logo.svg`, `components/codetics-mark.tsx` | Ajoutez votre logo dans `public/` et faites pointer `src` vers lui. C’est le logo affiché quand l’espace n’a pas de logo. | |
| `features/auth/auth-intro.tsx` | Lien, logo, nom et textes de présentation de l’écran de connexion | La ligne de crédit en bas, à compléter avec l’attribution |
| `features/auth/auth-screen.tsx`, `create-workspace.tsx`, `eula-screen.tsx` | Le sous-titre « Codetics Supervision » des cartes | Le crédit « Kevin Vavelin, Codetics » du pied de carte |
| `features/workspace/workspace-sidebar.tsx` | Nom et sous-titre affichés sans logo d’espace | |
| `features/workspace/workspace-shell.tsx` | Texte d’en-tête par défaut | Le pied de page, à compléter avec l’attribution |
| `features/charging/portal-layout.tsx` | Logo et lien affichés sans logo d’espace ; la mention « Supervision par Codetics » | La mention Stripe et l’attribution en pied de page |
| `features/workspace/workspace-settings.tsx` | Logo par défaut de l’aperçu | |

Exemple de pied de page conforme, pour `workspace-shell.tsx` ou `portal-layout.tsx` :

```tsx
<footer className="flex flex-wrap items-center justify-between gap-2 px-6 py-3 text-xs text-muted-foreground">
  <span>Votre Réseau · Supervision de recharge</span>
  <span>
    EV Supervision Lite — built by Kevin Vavelin, Codetics ·{" "}
    <a href="https://codetics.fr" target="_blank" rel="noreferrer">
      https://codetics.fr
    </a>
  </span>
</footer>
```

### Couleurs

Les couleurs sont des variables CSS dans `app/globals.css`, sous `:root` (thème clair) et `.dark` (thème sombre). Elles sont en `oklch`.

| Variable | Usage |
|---|---|
| `--primary`, `--primary-foreground` | Boutons principaux, liens actifs, texte posé dessus |
| `--sidebar-primary`, `--sidebar-primary-foreground` | Élément actif de la barre latérale |
| `--ring` | Contour de focus |
| `--chart-1` à `--chart-5` | Graphiques des statistiques |
| `--radius` | Arrondi général des composants |

Modifiez les deux thèmes. Vérifiez le contraste du texte sur `--primary` : au moins 4,5:1 pour le texte courant.

### Police

La police Source Sans 3 est déclarée par les blocs `@font-face` de `app/globals.css` et appliquée par `--font-sans`. Pour la remplacer, placez vos fichiers dans `public/fonts/`, adaptez les blocs `@font-face` et le premier nom de `--font-sans`. Ajoutez la licence de la nouvelle police dans `licenses/` et mentionnez-la dans `NOTICE`.

### Contenus juridiques

`features/auth/eula-screen.tsx` contient les conditions d’utilisation présentées aux utilisateurs. Faites-les relire et adaptez-les à votre société, en tant qu’exploitant du service.

## 3. Serveur

| Fichier | À changer | À garder |
|---|---|---|
| `security/AccountService.kt` | Objet des courriels « Codetics Supervision — … » ; nom « Codetics » de l’espace initial | |
| `ocpi/OcpiCredentials.kt` | `business_details` : nom et site de votre société, présentés à vos partenaires d’itinérance | |
| `ocpi/OcpiPublisher.kt` | Nom d’émetteur par défaut « Codetics » | |
| `core/AppConfiguration.kt` | Description de l’API | Le titre, le contact Kevin Vavelin — Codetics et le lien https://codetics.fr |
| `security/AuthController.kt` | | L’attribution renvoyée par `/api/v1/auth/eula` |
| `services/tokens/WalletPassService.kt`, `GoogleWalletPassService.kt` | | L’attribution des passes |

Les chemins sont relatifs à `ev-server/src/main/kotlin/fr/codetics/ev_server/`. Le paquet `fr.codetics` peut rester tel quel : il n’est pas visible des utilisateurs.

## 4. Applications mobiles

Les applications [iOS](../ev-app-ios/README.md) et [Android](../ev-app-android/README.md) se personnalisent par configuration :

1. Renseignez `BRAND_NAME`, `API_URL`, `PORTAL_URL`, `WORKSPACE_SLUG`, `SUPPORT_EMAIL` et `PRIVACY_POLICY_URL` (`AppConfiguration.plist` sur iOS, `AppConfiguration.properties` sur Android).
2. Remplacez l’icône de l’application par la vôtre, aux tailles demandées par Xcode et Android Studio. Le dossier `EV_APP_LOGO/` contient l’icône d’origine comme modèle de format.
3. Choisissez votre identifiant d’application : *Bundle Identifier* dans Xcode, `applicationId` dans `app/build.gradle.kts`.
4. Publiez sur vos propres comptes Apple Developer et Google Play.

L’écran **À propos** garde l’attribution : la compilation échoue si elle disparaît.

## 5. Identifier vos modifications

Une version modifiée doit le signaler sans laisser entendre que Kevin Vavelin ou Codetics l’approuve (licence, section 2). Ajoutez par exemple à la fin de `NOTICE`, sans rien retirer :

```text
Version modifiée par Votre Société (https://votre-societe.fr) : marque, couleurs,
textes et logos. Ces modifications ne sont ni réalisées ni approuvées par
Kevin Vavelin ou Codetics.
```

Reprenez la même mention dans l’écran **À propos** des applications ou dans vos documents de livraison.

## 6. Vérifier avant la mise en ligne

1. Lancez `scripts/verify-attribution.sh` : il doit afficher « Licence and attribution are in place. »
2. Recherchez les textes oubliés avec la commande `grep` de la partie 2.
3. Connectez-vous avec un compte **conducteur**, pas administrateur : l’attribution doit être visible en pied de page.
4. Ouvrez un portail de paiement par QR code sans être connecté : l’attribution doit être visible en pied de page.
5. Ouvrez **À propos** dans chaque application compilée en Release.
6. Lancez les vérifications habituelles : `./mvnw test` dans `ev-server`, puis `npm run lint`, `npm run format:check` et `npm run build` dans `ev-dashboard`.
