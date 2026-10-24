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
- [ ] Phase 2 : backend (auth, profils, clés VIP, catalogue, lecture)
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

Les destinations hors accueil affichent « Bientôt disponible » jusqu'à leur phase. Sans backend, l'accueil affiche l'état d'erreur réseau.
