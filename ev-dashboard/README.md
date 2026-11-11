# Tableau de bord Codetics Supervision Lite

Le [guide principal](../README.md) décrit le démarrage et le déploiement. Les variables privées sont documentées dans [.env.example](.env.example).

Développement : `npm ci`, puis `npm run dev`. Vérification : `npm run lint`, `npm run format:check`, `npm run check:i18n` et `npm run build`.

## Langues

L’interface est disponible en français (langue par défaut), en anglais et en espagnol. La langue suit le cookie `codetics-locale`, choisi depuis le sélecteur de langue de l’en-tête, sinon l’en-tête `Accept-Language` du navigateur. Les requêtes vers le serveur transmettent la langue active dans `Accept-Language`.

Les textes sources sont rédigés en français dans le code et passent par `t()` ou `tp()` (`lib/i18n`). Leurs traductions anglaise et espagnole se trouvent dans `lib/i18n/messages/`, un fichier par fonctionnalité. `npm run check:i18n` signale les textes affichés sans traduction et les traductions manquantes ou incohérentes ; un chemin en argument limite la vérification, par exemple `npm run check:i18n -- features/sessions`.
