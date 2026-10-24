# VISIOO — Master Product & Technical Specification
## Android + Android TV Streaming Platform

> **Document purpose:** This is the implementation brief for Claude Code. It defines the target product, architecture, UX, player, VIP access system, Android TV behavior, backend, administration, security, testing and delivery plan.
>
> **Reference:** the supplied APK was inspected as a technical/product reference. The reference package contains AndroidX Compose/TV components, Media3/ExoPlayer, HLS/DASH/RTSP-related playback components, FFmpeg JNI, QuickJS, WebView/embed handling, DataStore, TMDB/OpenSubtitles integrations, profiles, history/continue watching, watchlists, VIP checks and multiple source/provider endpoints.
>
> **Important:** VISIOO must be an original implementation. Do not copy protected source code, proprietary assets, branding, artwork, or unauthorized content from the reference application. Reproduce functional patterns and UX principles, then improve them with an original VISIOO design system. Only use video/content sources that VISIOO is legally authorized to distribute.

---

# 1. PRODUCT IDENTITY

## 1.1 Name

**VISIOO**

Never use:
- Movix
- MovieSlam
- the reference application's logo
- the reference application's package name
- copied branding or visual assets

## 1.2 Product positioning

VISIOO is a premium streaming application for:
- Android phones
- Android tablets
- Android TV / Google TV-class devices

The experience should feel:
- premium
- cinematic
- fast
- minimal
- modern
- easy to operate with a remote
- optimized for low-to-moderate bandwidth
- visually distinctive without being visually overloaded

The primary visual direction is **deep black + violet**, with restrained use of gradients and glow.

## 1.3 Business model

There is **no payment checkout inside the client application**.

Access is controlled by **VIP keys**.

Business flow:

```text
ADMIN
  ↓
Generate VIP key
  ↓
Sell/distribute key outside the application
  ↓
User creates/signs into account
  ↓
User enters VIP key
  ↓
Backend validates key
  ↓
VIP entitlement attached to account
  ↓
Premium catalog/playback unlocked
```

The application must never trust a locally stored `vip=true` flag as the source of truth.

---

# 2. PRODUCT PRINCIPLES

1. Fast startup.
2. Minimal network consumption outside video playback.
3. High-quality video playback.
4. Adaptive quality whenever an adaptive stream is available.
5. Real TV navigation, not a stretched mobile interface.
6. Clear focus states for D-pad navigation.
7. Original VISIOO visual identity.
8. Server-authoritative authentication and VIP entitlement.
9. Secure key activation with replay protection.
10. Graceful degradation when a source fails.
11. Source abstraction: the UI must never know how a stream was resolved.
12. Local caching for metadata/images/settings where appropriate.
13. Robust player state restoration.
14. Accessibility and readable TV typography.
15. Observability and structured error reporting.
16. Content availability must respect licensing/authorization.

---

# 3. REFERENCE APK — VERIFIED TECHNICAL OBSERVATIONS

The supplied reference APK contains evidence of the following components.

## 3.1 Application/package indicators

Observed package namespace:

```text
com.movix.tv
```

Observed high-level classes include:

```text
com.movix.tv.MainActivity
com.movix.tv.MovixApp
com.movix.tv.AppContainer
com.movix.tv.ui.embed.EmbedLayout
com.movix.tv.adblock.AdblockNative
com.movix.tv.data.update.UpdateInstallReceiver
```

These are **reference observations only**. VISIOO must use a new namespace, for example:

```text
com.visioo.app
```

or another final namespace chosen before release.

## 3.2 UI technology

The APK contains extensive AndroidX Jetpack Compose libraries and AndroidX TV material components.

VISIOO should therefore use:

- Kotlin
- Jetpack Compose
- Compose Material 3
- Compose for TV / TV Material APIs where appropriate
- Navigation Compose
- lifecycle/ViewModel
- Kotlin Coroutines + Flow

## 3.3 Playback technology

The reference contains AndroidX Media3 / ExoPlayer components and evidence for:

- HLS
- DASH
- MP4
- RTSP
- SmoothStreaming-related classes
- FFmpeg audio decoder extension
- multiple audio formats
- subtitle/track selection

VISIOO should use **Media3 ExoPlayer** as the playback foundation.

## 3.4 Native libraries observed

The APK contains native libraries including:

```text
libffmpegJNI.so
libquickjs.so
libmovix_adblock.so
libdatastore_shared_counter.so
libandroidx.graphics.path.so
```

VISIOO should not automatically reproduce every native component.

Use native/FFmpeg functionality only where it solves a concrete product requirement.

## 3.5 Web/embed technology

The reference contains:

- Android WebView
- JavaScript injection/communication
- QuickJS
- an `EmbedLayout`
- extraction-related JavaScript assets

VISIOO should isolate this into a dedicated **authorized provider adapter layer**.

Do not make the UI dependent on arbitrary web scraping.

## 3.6 Metadata/catalog

The reference contains TMDB-related models for:

- movie details
- TV details
- seasons
- episodes
- credits
- videos/trailers
- ratings
- release dates
- external IDs
- search/find results

VISIOO may use TMDB or another properly licensed/authorized metadata provider.

TMDB should be treated as **metadata**, not as the source of video rights.

## 3.7 Subtitle ecosystem

The reference contains OpenSubtitles-related strings and subtitle translation infrastructure.

VISIOO should implement a clean subtitle provider abstraction:

```text
SubtitleProvider
  ├── LocalSubtitleProvider
  ├── LicensedSubtitleProvider
  └── OptionalExternalProvider
```

Translation should be an optional feature and must respect the provider's terms.

## 3.8 Reference source/provider indicators

The APK contains source/provider-related strings and endpoints such as:

```text
api/tmdb/movie/
api/tmdb/tv/
api/links/movie/
api/links/tv/
api/fstream/movie/
api/fstream/tv/
api/wiflix/movie/
api/wiflix/tv/
api/kisskh/movie/
api/kisskh/tv/
api/cpasmal/movie/
api/cpasmal/tv/
api/purstream/movie/
api/purstream/tv/
api/swiftflow/movie/
api/swiftflow/tv/
api/j1f/movie/
api/j1f/tv/
api/drama/tv/
api/imdb/movie/
api/history/continue
api/profiles
api/check-vip
```

These are reference observations. **Do not reproduce or call unauthorized third-party sources.**

For VISIOO, implement the same concept as an abstract resolver:

```text
Content
  ↓
SourceResolver
  ↓
AuthorizedSource[]
  ↓
Health / availability ranking
  ↓
PlayableSource
  ↓
Media3
```

---

# 4. VISIOO INFORMATION ARCHITECTURE

## 4.1 Main navigation — mobile

Recommended bottom navigation:

```text
Accueil
Explorer
Recherche
Ma liste
Profil
```

The player is not part of the persistent navigation.

## 4.2 Main navigation — TV

Use a left-side rail or compact top navigation depending on screen size:

```text
Accueil
Films
Séries
Recherche
Ma liste
Profil
Paramètres
```

Navigation must work with:

- D-pad up/down/left/right
- center/select
- back
- long-press where useful
- mouse pointer
- keyboard arrows where available

## 4.3 Main screens

VISIOO should contain at minimum:

1. Splash
2. Onboarding
3. Sign in
4. Create account
5. Google sign-in
6. Recovery phrase creation
7. Recovery phrase confirmation
8. Home
9. Search
10. Search results
11. Category/listing
12. Movie details
13. Series details
14. Season selector
15. Episode list
16. Player
17. Source selector
18. Audio selector
19. Subtitle selector
20. Quality selector
21. Watchlist
22. Continue watching
23. History
24. Profiles
25. Profile creation/edit
26. VIP activation
27. VIP status
28. Settings
29. Language
30. Playback settings
31. Account/security
32. Device management
33. Error/offline states
34. Admin web dashboard

---

# 5. VISUAL DESIGN SYSTEM

## 5.1 Color palette

Primary:

```text
VISIOO Black       #050507
Deep Black         #09090D
Surface            #111117
Elevated Surface   #171720
Violet             #7C3AED
Bright Violet      #9B5CFF
Soft Violet        #B88CFF
Primary Text       #FFFFFF
Secondary Text     #A7A7B2
Muted Text         #6F6F7A
Success            #39D98A
Warning            #F5B942
Error              #FF5C68
```

Do not overuse purple. Purple should identify:
- active state
- selected item
- VIP
- primary CTA
- progress
- focus accent

## 5.2 Typography

Use a modern sans-serif.

Recommended:
- Inter
- Roboto Flex
- or another Android-compatible premium sans-serif

Hierarchy:

```text
Hero title       32–48sp mobile / 42–64sp TV
Section title    20–26sp
Card title       14–18sp
Body             14–16sp
Metadata         12–14sp
```

TV typography must be larger and readable from distance.

## 5.3 Cards

Movie/series posters:
- 2:3 aspect ratio
- rounded corners 8–14dp
- subtle shadow
- no excessive borders
- focus ring on TV

Landscape cards:
- 16:9
- used for Continue Watching and featured content

## 5.4 Hero

Home hero:
- full-width cinematic artwork
- black gradient overlay
- title
- metadata
- short synopsis
- primary "Regarder" CTA
- secondary "Ajouter à ma liste"
- optional VIP badge

Avoid overly busy hero animation.

---

# 6. MOTION & ANIMATIONS

Animations should be fast and subtle.

## 6.1 Global timing

```text
Micro interaction: 100–160ms
Standard transition: 180–260ms
Modal transition: 220–320ms
Hero transition: 350–550ms
```

Use spring animations sparingly.

## 6.2 Splash

Sequence:

```text
Black background
↓
VISIOO icon appears
↓
soft violet glow expands
↓
wordmark fades/slides in
↓
home/auth screen
```

Do not use a long intro.

## 6.3 TV focus animation

Focused card:

```text
scale 1.00 → 1.04
elevation increases
violet edge/glow appears
```

Duration ~160ms.

Never use large scaling that causes focus jumps.

## 6.4 Hero carousel

When changing item:
- artwork crossfade
- title fade/slide
- metadata crossfade
- progress indicator updates

Avoid full-screen movement.

## 6.5 Player controls

Tap/remote select:
- controls fade in
- timeline appears
- controls disappear after timeout
- remote movement keeps controls alive

---

# 7. HOME EXPERIENCE

Home should feel immediately cinematic.

Recommended order:

```text
Hero
↓
Continuer à regarder
↓
Nouveautés
↓
Films populaires
↓
Séries populaires
↓
Films francophones
↓
Séries francophones
↓
Action
↓
Comédie
↓
Animation
↓
Drame
↓
Recommandations
```

Rows should be data-driven.

Backend response example:

```json
{
  "sections": [
    {
      "id": "continue_watching",
      "title": "Continuer à regarder",
      "layout": "landscape",
      "items": []
    },
    {
      "id": "popular_movies",
      "title": "Films populaires",
      "layout": "poster",
      "items": []
    }
  ]
}
```

Do not hard-code the catalog layout into the Android client.

---

# 8. CONTENT MODEL

## 8.1 Movie

```text
Movie
- id
- tmdbId (optional)
- title
- originalTitle
- overview
- posterUrl
- backdropUrl
- logoUrl
- releaseDate
- year
- runtime
- genres
- countries
- rating
- ageRating
- cast
- directors
- trailer
- languages
- subtitles
- isVip
- sources
```

## 8.2 Series

```text
Series
- id
- externalId
- title
- originalTitle
- overview
- posterUrl
- backdropUrl
- year
- genres
- rating
- ageRating
- seasons
- isVip
```

## 8.3 Season

```text
Season
- id
- seriesId
- seasonNumber
- title
- posterUrl
- episodes
```

## 8.4 Episode

```text
Episode
- id
- seasonId
- episodeNumber
- title
- overview
- thumbnailUrl
- runtime
- airDate
- sources
```

---

# 9. SOURCE ARCHITECTURE

## 9.1 Core rule

The Android client must never contain provider-specific logic in screens.

Bad:

```text
MovieDetailsScreen → ProviderX → URL
```

Good:

```text
MovieDetailsScreen
    ↓
PlaybackViewModel
    ↓
ResolvePlaybackUseCase
    ↓
SourceRepository
    ↓
AuthorizedSourceResolver
    ↓
PlayableSource
```

## 9.2 Source model

```kotlin
data class PlayableSource(
    val id: String,
    val provider: String,
    val url: String,
    val type: SourceType,
    val quality: VideoQuality?,
    val language: String?,
    val subtitleTracks: List<SubtitleTrack>,
    val audioTracks: List<AudioTrack>,
    val headers: Map<String, String>,
    val expiresAt: Instant?,
    val drm: DrmConfig?
)
```

## 9.3 Source types

```text
HLS
DASH
MP4
WebM
Other authorized Media3-compatible formats
```

Do not implement arbitrary stream extraction by default.

## 9.4 Source ranking

Rank sources by:

1. authorization status
2. availability
3. language match
4. preferred quality
5. adaptive-stream availability
6. latency
7. recent health
8. provider priority

Example:

```text
authorized HLS 1080p
    >
authorized DASH 1080p
    >
authorized HLS 720p
    >
authorized MP4 720p
```

---

# 10. PLAYER

## 10.1 Technology

Use:

```text
AndroidX Media3 ExoPlayer
```

Core modules:

```text
media3-exoplayer
media3-ui
media3-ui-compose
media3-exoplayer-hls
media3-exoplayer-dash
media3-datasource-okhttp
```

Add FFmpeg extension only if a real compatibility requirement exists.

## 10.2 Player capabilities

Required:

- play/pause
- seek forward/back
- timeline
- buffered position
- current position
- playback speed
- quality
- audio track
- subtitle track
- fullscreen
- next episode
- previous episode where applicable
- autoplay next episode
- resume from last position
- skip intro if metadata exists
- error recovery
- source fallback
- picture-in-picture on supported mobile devices
- TV remote controls

## 10.3 Quality

Support:

```text
Auto
360p
480p
720p
1080p
1440p where available
2160p / 4K where available
```

Do not transcode everything on the client.

For adaptive HLS/DASH, let the player choose the track according to bandwidth/device constraints.

## 10.4 Audio

Player must expose:

```text
VF
VO
Other available audio tracks
```

The UI should display human-friendly names.

## 10.5 Subtitles

Support:

```text
French
English
Other available languages
Off
```

Subtitle appearance:
- font size
- background
- opacity
- text color
- edge/shadow
- bottom offset

Store preferences per user/profile.

---

# 11. PLAYER UX — MOBILE

Portrait:

```text
Player surface
↓
Tap
↓
Centered play/pause
Bottom:
timeline
current time / duration
settings
subtitle
audio
quality
fullscreen
```

Landscape:
- true immersive fullscreen
- controls overlay
- edge-safe controls
- orientation locked to landscape while appropriate

---

# 12. PLAYER UX — ANDROID TV

TV player should be built for remote operation.

Remote mapping:

```text
D-pad center → play/pause
D-pad left/right → seek
Up → controls
Down → controls
Back → close controls / exit player
Long center → extra options
```

Overlay:

```text
                [video]

       title / episode information

 ───────────────────────────────
  -10     Play/Pause     +10

  timeline─────────────────────

 Audio   Subtitles   Quality   Episodes
```

Controls must be large enough for TV.

---

# 13. CONTINUE WATCHING

Persist:

```text
userId
profileId
contentId
episodeId
positionMs
durationMs
percentage
updatedAt
```

Resume threshold:

```text
if position < 2 minutes:
    restart from beginning

if percentage > 95%:
    treat as completed

otherwise:
    resume
```

For series, automatically offer next episode.

---

# 14. WATCHLIST

Operations:

```text
add
remove
check
list
```

Sync across devices.

Optimistic UI is allowed, but server state remains authoritative.

---

# 15. PROFILES

Support multiple profiles.

Suggested first version:

```text
Primary profile
Child profile
Additional profile
```

Profile fields:

```text
id
userId
name
avatar
isChild
language
subtitleLanguage
maturityLevel
autoplay
```

Child profiles must not simply rely on client-side hiding. The backend should enforce maturity restrictions.

---

# 16. AUTHENTICATION

Required methods:

### Email

```text
email
password
```

### Google

Use official Google identity APIs.

### Recovery phrase

VISIOO may provide a 12-word recovery mechanism.

Important:
- generate cryptographically secure words
- never log the phrase
- never send plaintext recovery phrase to analytics
- never display it again by default
- store only a secure verifier/derivative where possible
- require confirmation during creation
- provide secure account-recovery UX

Do not present this as a cryptocurrency wallet or imply that the phrase controls money.

---

# 17. VIP KEY SYSTEM

## 17.1 Key format

Use random, non-sequential keys.

Example:

```text
VSOO-8K7M-QP4X-2L9D
```

The exact format may change.

## 17.2 Key entity

```text
VipKey
- id
- keyHash
- displayPrefix
- planId
- status
- maxActivations
- activationCount
- createdAt
- expiresAt
- activatedAt
- activatedByUserId
- deviceLimit
- notes
```

Never store the raw key in logs.

Prefer storing a cryptographic hash.

## 17.3 Key activation

```text
POST /v1/vip/activate
```

Request:

```json
{
  "key": "VSOO-XXXX-XXXX-XXXX",
  "deviceId": "..."
}
```

Server:

1. authenticate user
2. normalize key
3. hash key
4. lookup key
5. validate status
6. validate expiration
7. validate activation limit
8. attach entitlement
9. record activation
10. return current entitlement

## 17.4 Entitlement

```text
VipEntitlement
- id
- userId
- planId
- startsAt
- endsAt
- status
- deviceLimit
```

---

# 18. DEVICE MANAGEMENT

Each account should have a device registry.

```text
Device
- id
- userId
- installationId
- platform
- model
- appVersion
- lastSeenAt
- createdAt
- revokedAt
```

VIP key can specify device limit.

Example:

```text
1 device
2 devices
3 devices
5 devices
```

Admin can revoke a device.

Do not use hardware identifiers that violate platform privacy rules.

Use an app-generated installation ID plus server-issued device token.

---

# 19. BACKEND

Recommended stack:

```text
Node.js
TypeScript
NestJS or Fastify
PostgreSQL
Redis
Object storage where required
Docker
```

Alternative backend stacks are acceptable, but the API contracts must remain stable.

## 19.1 Services

Logical modules:

```text
auth
users
profiles
catalog
metadata
search
recommendations
playback
sources
subtitles
history
watchlist
vip
devices
admin
analytics
health
```

---

# 20. DATABASE

Core tables:

```text
users
profiles
devices
vip_plans
vip_keys
vip_key_activations
vip_entitlements
movies
series
seasons
episodes
genres
movie_genres
series_genres
sources
source_health
audio_tracks
subtitle_tracks
watch_history
watchlist
search_history
admin_users
audit_logs
```

Indexes required on:

```text
users.email
profiles.user_id
devices.user_id
vip_keys.key_hash
vip_entitlements.user_id
watch_history.user_id + profile_id
watchlist.user_id + profile_id
sources.content_id
```

---

# 21. API CONTRACT

Base:

```text
/api/v1
```

## Authentication

```text
POST /auth/register
POST /auth/login
POST /auth/google
POST /auth/refresh
POST /auth/logout
POST /auth/recover
```

## Users

```text
GET /me
PATCH /me
```

## Profiles

```text
GET /profiles
POST /profiles
PATCH /profiles/:id
DELETE /profiles/:id
```

## Catalog

```text
GET /home
GET /movies
GET /movies/:id
GET /series
GET /series/:id
GET /series/:id/seasons
GET /episodes/:id
GET /genres/:id
GET /search
```

## Playback

```text
POST /playback/resolve
POST /playback/session
POST /playback/progress
DELETE /playback/session/:id
```

## VIP

```text
GET /vip
POST /vip/activate
```

## History

```text
GET /history
GET /history/continue
POST /history/progress
DELETE /history/:id
```

## Watchlist

```text
GET /watchlist
POST /watchlist
DELETE /watchlist/:contentId
```

## Devices

```text
GET /devices
DELETE /devices/:id
```

---

# 22. PLAYBACK SECURITY

Never expose long-lived permanent stream URLs if avoidable.

Preferred:

```text
Client requests playback
        ↓
Backend verifies entitlement
        ↓
Backend returns short-lived signed URL
        ↓
Media3 plays URL
```

Possible signed URL lifetime:

```text
5–30 minutes
```

Renew when necessary.

For DRM-protected content, use the appropriate licensed DRM workflow.

Never put private provider credentials in the Android APK.

---

# 23. ADMIN DASHBOARD

A web dashboard is strongly recommended.

Modules:

```text
Dashboard
Users
Profiles
VIP keys
VIP plans
Devices
Movies
Series
Episodes
Sources
Source health
Subtitles
Categories
Homepage
Featured content
Analytics
Audit logs
Settings
```

## 23.1 VIP key generator

Admin selects:

```text
Plan
Duration
Device limit
Quantity
Optional expiration
```

Example:

```text
Plan: VIP 30 days
Quantity: 100
Devices: 2
```

The system generates 100 unique keys.

Admin can export a CSV:

```text
key
plan
duration
device_limit
status
created_at
```

Do not expose raw keys again after initial generation unless intentionally exported.

---

# 24. ADMIN SECURITY

Admin authentication:
- email/password
- MFA recommended
- role-based permissions
- audit log

Roles:

```text
SUPER_ADMIN
ADMIN
CONTENT_MANAGER
SUPPORT
```

Sensitive actions require audit records.

---

# 25. SEARCH

Search across:

```text
movie title
original title
series title
episode title
actors
directors
genres
```

Use debounce:

```text
250–400ms
```

Search results should support:
- poster
- title
- year
- type
- rating
- VIP badge

TV search:
- on-screen keyboard
- physical keyboard support
- remote navigation

---

# 26. OFFLINE / CACHE

The first production version should be **online-first**.

Cache:
- metadata
- images
- home configuration
- user preferences
- playback position
- authentication tokens securely

Do not cache premium video permanently unless an explicit legal offline-download feature is implemented.

---

# 27. NETWORK RESILIENCE

Handle:

```text
timeout
no internet
DNS failure
HTTP 401
HTTP 403
HTTP 404
HTTP 429
HTTP 5xx
expired source
source unavailable
```

Player behavior:

```text
Source A fails
↓
retry once when appropriate
↓
try Source B
↓
show source error only if all authorized sources fail
```

Do not loop infinitely.

---

# 28. PERFORMANCE

Target:

- fast cold start
- lazy lists everywhere
- image memory caching
- thumbnail sizing
- prefetch only when useful
- avoid loading full-resolution backdrops into poster components
- minimize JSON payloads
- paginate large catalogs
- avoid unnecessary recomposition
- stable Compose keys
- baseline profile
- R8 in release
- network compression
- HTTP caching

For TV:
- avoid rendering huge off-screen lists
- preserve focus state
- preload only nearby images

---

# 29. IMAGE PIPELINE

Use Coil.

Rules:

```text
Poster → correct poster size
Backdrop → correct backdrop size
Avatar → small circular size
Logo → transparent asset where available
```

Prefer CDN resizing.

Do not request a 1920px image to render a 150px poster.

---

# 30. ANDROID PROJECT STRUCTURE

Recommended:

```text
visioo/
├── app/
├── core/
│   ├── common/
│   ├── network/
│   ├── database/
│   ├── designsystem/
│   ├── security/
│   └── analytics/
├── feature/
│   ├── auth/
│   ├── home/
│   ├── search/
│   ├── details/
│   ├── player/
│   ├── watchlist/
│   ├── history/
│   ├── profiles/
│   ├── vip/
│   └── settings/
├── tv/
│   ├── navigation/
│   ├── home/
│   ├── details/
│   ├── player/
│   └── search/
└── shared/
```

Use feature-based architecture rather than one giant package.

---

# 31. ANDROID ARCHITECTURE

Recommended:

```text
Compose UI
    ↓
ViewModel
    ↓
Use Case
    ↓
Repository
    ↓
Remote / Local data source
```

Example:

```text
HomeScreen
  ↓
HomeViewModel
  ↓
GetHomeUseCase
  ↓
CatalogRepository
  ├── CatalogApi
  └── LocalCache
```

Playback:

```text
PlayerScreen
  ↓
PlayerViewModel
  ↓
ResolvePlaybackUseCase
  ↓
PlaybackRepository
  ↓
Backend
  ↓
PlayableSource
  ↓
Media3 ExoPlayer
```

---

# 32. STATE MANAGEMENT

Use sealed UI state models.

Example:

```kotlin
sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Success(
        val sections: List<HomeSection>
    ) : HomeUiState

    data class Error(
        val message: String
    ) : HomeUiState
}
```

Do not expose Retrofit/database models directly to Compose.

---

# 33. SECURE STORAGE

Use Android Keystore-backed storage for sensitive tokens.

Store:
- access token
- refresh token
- installation ID
- secure session information

Do not store:
- plaintext passwords
- raw VIP keys unnecessarily
- recovery phrase plaintext
- provider secrets

---

# 34. AUTH SESSION

Use:

```text
short-lived access token
longer-lived refresh token
```

On 401:
1. refresh
2. retry once
3. if refresh fails → logout

Avoid infinite retry loops.

---

# 35. TV-SPECIFIC DESIGN

TV is a first-class platform.

Do not simply use:

```text
Mobile UI scaled to 1920x1080
```

Instead create TV layouts.

## TV Home

```text
┌───────────────────────────────────────────────┐
│ VISIOO       Accueil Films Séries Recherche │
├───────────────────────────────────────────────┤
│                                               │
│              HERO CONTENT                    │
│                                               │
│     [Regarder]  [Ma liste]                   │
│                                               │
├───────────────────────────────────────────────┤
│ Continuer à regarder                          │
│ [ 16:9 ] [ 16:9 ] [ 16:9 ] [ 16:9 ]         │
│                                               │
│ Films populaires                              │
│ [poster] [poster] [poster] [poster]          │
└───────────────────────────────────────────────┘
```

## Focus

Focused item:
- scale
- glow
- elevation
- title metadata update
- clear focus border

Never make focus ambiguous.

---

# 36. MOUSE SUPPORT ON TV

Where the device supports a pointer:
- clickable surfaces must expose correct hit targets
- hover may activate subtle elevation
- click maps to primary action
- pointer must not break D-pad focus

D-pad remains the primary TV interaction model.

---

# 37. ACCESSIBILITY

Support:
- content descriptions
- sufficient contrast
- readable font sizes
- screen reader semantics where relevant
- no color-only status communication
- TV focus visibility

---

# 38. ERROR UX

Examples:

### Network

> Impossible de se connecter. Vérifiez votre connexion Internet.

Actions:
- Réessayer
- Retour

### VIP expired

> Votre accès VIP a expiré.

Actions:
- Activer une nouvelle clé
- Retour

### Source unavailable

> Cette source est temporairement indisponible.

Action:
- Essayer une autre source

Do not expose raw server stack traces.

---

# 39. ANALYTICS

Collect only necessary product analytics.

Useful events:

```text
app_open
login_success
profile_selected
search
content_open
playback_started
playback_failed
source_failed
source_switched
playback_completed
watchlist_added
vip_activation_success
vip_activation_failed
device_added
device_removed
```

Do not collect:
- passwords
- recovery phrases
- raw VIP keys
- private payment information
- unnecessary personal data

---

# 40. LOGGING

Use structured logging.

Example:

```json
{
  "event": "playback_failed",
  "contentId": "movie_123",
  "sourceId": "source_4",
  "errorCode": "HTTP_403"
}
```

Do not log signed stream URLs if they contain credentials/tokens.

---

# 41. TESTING

## Unit

Test:
- VIP validation
- entitlement calculation
- progress calculation
- source ranking
- search mapping
- authentication state
- recovery logic

## Integration

Test:
- API
- database
- authentication
- playback resolution
- VIP activation

## UI

Test:
- navigation
- player controls
- focus
- profiles
- search
- VIP activation

## TV

Test with:
- emulator
- real Android TV device
- remote
- mouse
- keyboard

---

# 42. SECURITY TESTING

Test:
- expired keys
- reused keys
- brute-force activation
- token expiration
- revoked devices
- unauthorized playback
- modified client VIP state
- API rate limits
- admin privilege escalation

Backend must remain secure even if the APK is reverse engineered.

---

# 43. RATE LIMITING

Recommended:

```text
Login: 5 attempts/minute/IP + account protections
VIP activation: 5 attempts/minute/user/device
Search: reasonable per-user/IP limit
Admin APIs: strict rate limit
Playback resolve: protected against abuse
```

Do not lock users out permanently after a small number of mistakes.

---

# 44. RELEASE CHANNELS

Development:

```text
debug
staging
production
```

Production should have:
- R8 enabled
- logging reduced
- API production URL
- secure configuration
- versioned database migrations

---

# 45. UPDATE SYSTEM

VISIOO may use:

```text
Play Store
```

and/or an authorized private distribution channel.

If using a custom update manifest:

```json
{
  "versionCode": 12,
  "versionName": "1.1.0",
  "downloadUrl": "...",
  "sha256": "...",
  "mandatory": false,
  "notes": {
    "fr": "..."
  }
}
```

Verify:
- HTTPS
- checksum
- signature
- version ordering

Do not install arbitrary APKs silently.

---

# 46. CONTENT MANAGEMENT

Admin should be able to:
- create/edit movie
- create/edit series
- create season
- create episode
- attach authorized source
- attach audio
- attach subtitles
- mark VIP
- publish/unpublish
- feature content
- assign categories
- update artwork

---

# 47. SOURCE HEALTH

Backend should monitor source health.

Fields:

```text
source_id
last_checked_at
status
latency_ms
failure_count
success_count
availability_percent
```

Source statuses:

```text
HEALTHY
DEGRADED
DOWN
UNKNOWN
```

The client should receive only appropriate sources.

---

# 48. HOMEPAGE CMS

Admin can configure sections:

```text
Hero
Continue Watching
Popular
New Releases
French Films
French Series
Action
Comedy
Animation
Recommendations
```

Each section:

```text
id
title
type
order
contentIds
active
```

This avoids publishing a new APK every time homepage ordering changes.

---

# 49. RECOMMENDATION ENGINE — V1

Start simple.

Inputs:
- genres watched
- completion rate
- watchlist
- language
- popularity
- recency

Score:

```text
recommendationScore =
  genreAffinity
  + languageAffinity
  + popularity
  + recency
  + similarity
```

Later:
- collaborative filtering
- embeddings
- personalized ranking

---

# 50. BRANDING

## Logo direction

The VISIOO icon must be:
- minimal
- recognizable at 24dp
- recognizable at TV scale
- usable without text
- usable in monochrome
- usable on black
- suitable for app icon
- suitable for splash
- suitable for favicon/admin

Possible visual territory:
- abstract V
- lens/aperture
- cinematic frame
- eye/vision abstraction
- play symbol integrated subtly into V

Avoid copying Netflix's N or Prime Video's smile/arrow.

The final logo should be original.

---

# 51. APP ICON

Required variants:

```text
adaptive icon
legacy icon
monochrome icon where supported
TV launcher icon
```

Background:
- black or deep violet

Foreground:
- minimal VISIOO mark

Do not put detailed artwork inside the icon.

---

# 52. DESIGN TOKENS

Centralize:

```kotlin
object VisiooColors
object VisiooTypography
object VisiooSpacing
object VisiooShapes
object VisiooMotion
```

No random colors inside feature screens.

---

# 53. THEMING

Support:
- dark mode as primary
- light mode optional

VISIOO's cinematic design is primarily dark.

TV should remain dark by default.

---

# 54. LOCALIZATION

V1:

```text
French
English
```

Structure all strings for localization.

Do not hard-code user-visible French strings in Kotlin.

---

# 55. DATA PRIVACY

Implement:
- privacy policy URL
- terms URL
- account deletion
- data export where required
- minimal analytics
- secure tokens
- secure backend

The app should not request permissions it does not need.

---

# 56. RECOMMENDED TECHNOLOGY STACK

## Android

```text
Kotlin
Jetpack Compose
Compose Material 3
Compose for TV
Navigation Compose
ViewModel
Coroutines
Flow
Retrofit
OkHttp
Kotlin Serialization
Room
DataStore
Coil
AndroidX Media3 / ExoPlayer
Android Keystore
```

## Backend

```text
Node.js
TypeScript
NestJS/Fastify
PostgreSQL
Redis
JWT/session tokens
Docker
```

## Admin

```text
Next.js
TypeScript
Tailwind or equivalent design system
React Query
```

---

# 57. RECOMMENDED BUILD ORDER

Claude Code must not attempt to build everything simultaneously.

## Phase 1 — Foundation

1. Create Android project.
2. Configure Gradle.
3. Configure Kotlin.
4. Configure Compose.
5. Configure Material 3.
6. Configure TV.
7. Configure navigation.
8. Create design system.
9. Create environments.
10. Create API client.

## Phase 2 — Backend

1. PostgreSQL.
2. migrations.
3. auth.
4. users.
5. profiles.
6. VIP keys.
7. entitlements.
8. devices.
9. catalog.
10. playback API.

## Phase 3 — Android authentication

1. splash.
2. login.
3. register.
4. Google.
5. recovery mechanism.
6. session management.

## Phase 4 — Catalog

1. home.
2. search.
3. details.
4. categories.
5. seasons.
6. episodes.

## Phase 5 — Player

1. Media3.
2. HLS.
3. DASH.
4. MP4.
5. source abstraction.
6. track selection.
7. subtitles.
8. progress.
9. autoplay.
10. TV player.

## Phase 6 — VIP

1. key activation.
2. entitlement.
3. device limit.
4. expiration.
5. revoked key handling.

## Phase 7 — Personalization

1. profiles.
2. watchlist.
3. history.
4. continue watching.
5. recommendations.

## Phase 8 — Admin

1. dashboard.
2. users.
3. VIP keys.
4. content.
5. sources.
6. homepage.
7. analytics.

## Phase 9 — Hardening

1. security.
2. performance.
3. offline states.
4. error recovery.
5. accessibility.
6. TV navigation testing.
7. release build.
8. CI/CD.

---

# 58. CLAUDE CODE IMPLEMENTATION RULES

Claude Code must follow these rules:

### Rule 1
Do not create a fake demo when implementing production features.

### Rule 2
Do not hard-code movie data into UI components.

### Rule 3
Do not put provider/source URLs directly inside Compose screens.

### Rule 4
Do not trust VIP state stored only on the device.

### Rule 5
Do not put backend secrets in the APK.

### Rule 6
Do not copy reference application source code or protected assets.

### Rule 7
Use interfaces around external providers.

### Rule 8
Write tests for business-critical functionality.

### Rule 9
Keep mobile and TV UI behavior separate where necessary while sharing domain/data layers.

### Rule 10
Every feature must have:
- data model
- repository
- use case where appropriate
- ViewModel
- UI state
- loading state
- empty state
- error state

### Rule 11
Every network call must have:
- timeout
- error mapping
- retry strategy where appropriate
- logging without secrets

### Rule 12
Every player failure must have a recoverable state.

---

# 59. DEFINITION OF DONE

A feature is complete only when:

```text
[ ] UI implemented
[ ] loading state
[ ] empty state
[ ] error state
[ ] mobile navigation
[ ] TV navigation where relevant
[ ] backend API
[ ] database model where required
[ ] tests
[ ] analytics event where justified
[ ] accessibility
[ ] performance checked
```

---

# 60. MVP ACCEPTANCE CRITERIA

VISIOO MVP is considered functional when a user can:

1. Install the Android application.
2. Open VISIOO.
3. Create an account.
4. Log in.
5. Create/select a profile.
6. Enter a valid VIP key.
7. Receive VIP entitlement.
8. Browse home.
9. Search content.
10. Open movie details.
11. Open series details.
12. Select a season.
13. Select an episode.
14. Start authorized playback.
15. Select quality.
16. Select audio.
17. Select subtitles.
18. Pause/resume.
19. Seek.
20. Close/reopen and resume playback.
21. Add content to watchlist.
22. Continue watching.
23. Use the same account on another authorized device within limits.
24. Use Android TV with a remote.
25. Navigate the entire TV app without touch.
26. Use a mouse where supported.
27. Admin can generate a VIP key.
28. Admin can revoke a key.
29. Admin can see users/devices.
30. Backend rejects unauthorized playback.

---

# 61. FUTURE FEATURES

Not required for MVP but architecture should allow:

```text
Offline downloads
Picture-in-picture improvements
Chromecast
AirPlay where appropriate
Multiple audio languages
Dolby formats where legally/licensed
DRM
4K
Kids mode
Advanced parental controls
Profiles PIN
Smart recommendations
Continue Watching sync
Cross-device playback
Promotional codes
Referral system
Admin notifications
Push notifications
Android Automotive if relevant
```

---

# 62. IMPORTANT LEGAL / CONTENT REQUIREMENT

VISIOO is a software platform.

The implementation must separate:

```text
software
metadata
content rights
streaming source
subscription entitlement
```

A technically playable URL is not automatically a legally usable source.

Only connect providers and streams that VISIOO is authorized to use/distribute.

The architecture intentionally supports multiple authorized sources without embedding any specific unauthorized streaming provider.

---

# 63. FINAL CLAUDE CODE PROMPT

Use the following as the implementation instruction:

> Build VISIOO as a production-ready Android + Android TV streaming application based on this specification.
>
> VISIOO must be an original product. Use the supplied reference application only to understand general UX patterns, technical capabilities and information architecture. Do not copy its source code, branding, artwork, proprietary implementation, or unauthorized content/source extraction.
>
> Implement a real architecture rather than a static prototype.
>
> Android must use Kotlin, Jetpack Compose, Media3/ExoPlayer, Coroutines/Flow, Retrofit/OkHttp, Room/DataStore and a secure token system.
>
> Android TV must have a dedicated TV-first UI with D-pad focus management, remote navigation, mouse support where available, readable typography and TV-optimized player controls.
>
> The visual system must use deep black and violet with a minimal premium cinematic aesthetic.
>
> The product name is VISIOO.
>
> There is no in-app payment checkout. Access is controlled by server-authoritative VIP keys. The admin generates keys; users activate them inside the app; the backend grants time/device-limited entitlements.
>
> Implement email authentication, Google authentication and a secure 12-word account recovery mechanism.
>
> Implement profiles, watchlist, history, continue watching, search, movie/series details, seasons, episodes, source resolution, playback, audio tracks, subtitles, quality selection and player progress synchronization.
>
> Implement authorized HLS/DASH/MP4 playback through Media3. The client must not contain private provider credentials. Playback URLs should preferably be short-lived and server-authorized.
>
> Implement a backend with PostgreSQL and Redis, REST API versioning and a web admin dashboard.
>
> Implement automated tests and clear separation of domain, data, UI and platform-specific TV code.
>
> Build in phases and keep the application compiling after each phase.
>
> Do not replace production features with mocks unless explicitly marked as a temporary development adapter.
>
> At the end of each implementation phase, report:
> 1. files created/changed,
> 2. architecture decisions,
> 3. tests executed,
> 4. remaining issues,
> 5. next implementation phase.
>
> Prioritize correctness, security, performance, TV usability and maintainability over rushing to produce a visual demo.

---

# 64. PROJECT CHECKLIST

## Product
- [ ] VISIOO branding
- [ ] logo
- [ ] icon
- [ ] splash
- [ ] design tokens

## Android
- [ ] project
- [ ] navigation
- [ ] auth
- [ ] home
- [ ] search
- [ ] details
- [ ] player
- [ ] profiles
- [ ] history
- [ ] watchlist
- [ ] VIP
- [ ] settings

## Android TV
- [ ] dedicated navigation
- [ ] D-pad focus
- [ ] mouse
- [ ] TV home
- [ ] TV details
- [ ] TV search
- [ ] TV player
- [ ] TV settings

## Backend
- [ ] auth
- [ ] users
- [ ] profiles
- [ ] devices
- [ ] VIP
- [ ] catalog
- [ ] sources
- [ ] playback
- [ ] history
- [ ] watchlist
- [ ] admin

## Security
- [ ] token security
- [ ] key hashing
- [ ] rate limits
- [ ] signed playback URLs
- [ ] device limits
- [ ] audit logs
- [ ] admin MFA

## Quality
- [ ] unit tests
- [ ] integration tests
- [ ] Android UI tests
- [ ] TV tests
- [ ] player tests
- [ ] network failure tests
- [ ] release build
- [ ] R8
- [ ] performance
- [ ] accessibility

---

# 65. REFERENCE FILE

The supplied reference APK used for the technical/product analysis:

```text
movix-tv(1).apk
```

The APK contains approximately 9.9 MB of data and was inspected at the archive/native-library/string level. It includes AndroidX/Compose/TV components, Media3/ExoPlayer, FFmpeg JNI, QuickJS, WebView/embed infrastructure, DataStore, TMDB/OpenSubtitles-related integrations, profile/history/watchlist/VIP concepts and multiple source-provider abstractions.

Use those observations as architectural inspiration only.

---

## END OF VISIOO MASTER SPECIFICATION
