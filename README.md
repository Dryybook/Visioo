# VISIOO

Plateforme de streaming Android, tablette et Android TV. L'accès est contrôlé par des clés VIP validées côté serveur.

- Spécification complète : [docs/MASTER_SPEC.md](docs/MASTER_SPEC.md)
- Design system (tokens + planche visuelle) : [design/](design/)
- Logos de référence : [brand/](brand/)
- Tokens Compose : [core/designsystem/VisiooTokens.kt](core/designsystem/VisiooTokens.kt)

## Contenu et droits

VISIOO ne diffuse que des sources que l'éditeur est autorisé à distribuer. Aucun fournisseur tiers non autorisé n'est intégré. La résolution de sources passe par une interface abstraite.

## Avancement

- [x] Phase 0 : dépôt, spec, tokens, planche de design
- [x] Phase 1 : squelette Android (Gradle, thème, navigation mobile + TV, client API, écran d'accueil branché sur `GET /home`)
- [x] Phase 2 : backend (`backend/` : auth, profils, appareils, clés VIP, catalogue, lecture signée, admin de base). Restent : récupération 12 mots, Google, MFA admin, CMS contenu (voir `backend/README.md`)
- [x] Phase 3 : authentification Android (démarrage, onboarding, connexion, inscription, Google, phrase de récupération 12 mots, activation de clé VIP, choix du profil, stockage Keystore, rafraîchissement automatique des jetons). Non testée sur appareil.
- [ ] Phases suivantes : voir la section 57 de la spec

## Développer

Prérequis : JDK 17 ou plus, Android SDK (plateforme 35).

```bash
export ANDROID_HOME=/chemin/vers/android-sdk
./gradlew :core:network:test :app:testDebugUnitTest :app:assembleDebug
```

L'URL de l'API se règle avec `-Pvisioo.apiBaseUrl=...` (debug, défaut `http://10.0.2.2:3000/api/v1/`) et `-Pvisioo.apiBaseUrl.release=...` (release, valeur par défaut factice à remplacer avant publication).

## Structure

- `app/` : application unique mobile + Android TV (détection `UiModeManager`), navigation, écrans par fonctionnalité.
- `core/designsystem/` : tokens, thème, focus TV, composants communs.
- `core/network/` : Retrofit/OkHttp, DTO, mapping d'erreurs, tests.

Les destinations hors accueil et profil affichent « Bientôt disponible » jusqu'à leur phase. Sans backend, l'inscription et la connexion échouent avec un message réseau.

### Réglages optionnels (propriétés Gradle)

- `-Pvisioo.googleWebClientId=...` : client web Google. Vide, le bouton Google est masqué. Le même identifiant doit être dans `GOOGLE_CLIENT_ID` côté backend. Le bouton n'apparaît pas sur Android TV.
- `-Pvisioo.termsUrl=...` et `-Pvisioo.privacyUrl=...` : réservés aux liens légaux, pas encore affichés.
