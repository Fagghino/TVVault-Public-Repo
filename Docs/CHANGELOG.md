# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.8.6] - 2026-07-08

### Added
- Created `LibraryScreen` as a unified view containing both local Shows and Movies with a premium statistics header showing counts and hours spent.
- Created `SearchScreen` as a dedicated online discovery page for TMDb search, moving search interactions away from local library lists.
- Added `EpisodeDao.getNextEpisodeToWatch` and `MediaRepository.getNextEpisodeToWatch` to quickly find the first unwatched episode of a series.
- Added bulk episode state updates with transactional savings and show status recalculation via `MediaRepository.setEpisodesWatched`.
- Added soft-deletion for shows and movies via `MediaRepository.removeMediaItem`.
- Added Swipe-to-Dismiss on episodes (`DetailScreen`): swipe right to mark watched, swipe left to mark unwatched.
- Added automatic previous episode/season confirmation dialogs when marking non-consecutive episodes or seasons as watched.
- Added a top-bar three-dots action menu in `DetailScreen` for global status changes and deletion.
- Unified bulk updates for seasons and episodes using a single entry point method in `DetailScreen`.

### Fixed
- Excluded soft-deleted media items from `getGlobalWatchedEpisodesCount` to prevent statistics inconsistency after deleting a show.

### Changed
- Restructured bottom navigation bar to map: Libreria (Library), Uscite (Upcoming), Cerca (Search), Profilo (Profile).
- Removed TMDb online search logic from local `ShowsScreen` and `MoviesScreen`.
- Shows in the library list now dynamically display their next unwatched episode (e.g., "Prossimo: S1E3") or status.
- Removed the old personal status dropdown selector from the `DetailScreen` layout.

## [0.8.5] - 2026-07-08

### Changed
- Reworked `checkAndRunFirestoreMigration` in `FirestoreSyncEngine` to gather operations and push them in batches of 450 using `WriteBatch`, drastically reducing API calls and improving speed.
- Isolated state mutations (`toggleFavorite`, `updateStatus`, `updateRatingAndNotes`) in `MediaViewModel` per media item using `ConcurrentHashMap<Long, Mutex>` to prevent read-modify-write race conditions.

### Fixed
- Fixed an `Invalid document reference` crash in `FirestoreSyncEngine` by ensuring `remoteId.isNotBlank()` before adding items to batch or individual push operations.
- Fixed a potential `NullPointerException` in `updateStatus` when evaluating `startedAt` on a `null` initial state.

## [0.8.4] - 2026-07-08

### Changed
- Refactored `loadUpcomingEpisodes` in `MediaViewModel` to leverage a single reactive database JOIN query (`observeShowsWithState`).
- Optimized `addMediaToLibrary` in `MediaRepository` by replacing in-memory `.size` lists with `COUNT` queries to prevent memory bottlenecks.
- Optimized TMDb network requests in `loadUpcomingEpisodes` by implementing a `Semaphore` concurrency limit (max 10) to safely parallelize fetches without triggering 429 errors.

### Fixed
- Fixed a semantic inconsistency by clearing `completedAt` to `null` in `MediaRepository` (`setEpisodeWatched`, `setSeasonWatched`) and `MediaViewModel` (`updateStatus`) when a show loses its 'completed' status.

## [0.8.3] - 2026-07-08

### Added
- Added `MediaItemWithState` POJO and `getMediaItemsWithState` transaction query in `MediaItemDao` to support reactive Room JOIN relationships between shows and their states.
- Added direct SQLite aggregate count queries `getEpisodesCountForMedia` and `getWatchedEpisodesCountForMedia` in Room DAOs to prevent loading large episode lists in memory.
- Added bulk state retrieval query `getStatesByEpisodeIds` in `UserEpisodeStateDao` to prevent N+1 database reads.

### Changed
- Refactored `showsWithState` in `MediaViewModel` to observe the new reactive JOIN relationship, fixing show status updates on the main screen.
- Parallelized independent database reads in `MediaRepository` using coroutine concurrency (`async`/`await`).
- Refactored `setSeasonWatched` in `MediaRepository` to execute in-memory delta calculations and a single bulk state read, resolving N+1 database queries.
- Refactored `FirestoreSyncEngine` snapshot listeners to filter out local writes using document-level `metadata.hasPendingWrites()` checks.
- Optimized Firestore sync by batching operations using `WriteBatch` chunked at 450 operations.
- Isolated and parallelized Firestore pushes in `MediaRepository` using `repositoryScope` with `SupervisorJob` and `Dispatchers.IO` for optimistic UI feedback.

### Fixed
- Fixed redundant database read `getByEpisodeId` in `setEpisodeWatched` sync by reusing local memory state.

## [0.8.2] - 2026-07-08

### Changed
- Moved the TMDb API Key from hardcoded/database storage to local properties loaded at build-time via `BuildConfig` to secure the codebase for public access.
- Refactored `TmdbClient` and `TVVaultApp` to use `BuildConfig.TMDB_API_KEY`, removing the `AppSettingDao` dependency from `TmdbClient.createService`.
- Removed database-level settings operations for saving and retrieving the TMDb API key from `MediaRepository` and `MediaViewModel`.

### Removed
- Removed the TMDb API Key configuration input field and section from the settings screen layout in `SettingsScreen`.
- Removed obsolete English and Italian TMDb API configuration string resources (`tmdb_config`, `tmdb_desc`, `tmdb_placeholder`, `save_api_key`, `api_key_saved`) from resource files.

## [0.8.1] - 2026-07-08

### Added
- Added legacy mapping queues (`pendingMappingMediaItemOrphans` and `pendingMappingEpisodeOrphans`) in `FirestoreSyncEngine` to resolve parent references for legacy Firestore documents using original `localId` fields.

### Changed
- Refactored `MediaRepository.addMediaToLibrary` to implement an auto-repair check: if the TV show is already present locally but has no seasons/episodes (e.g. due to previous crash interruptions), it automatically re-fetches and reconstructs them from TMDb.

## [0.8.0] - 2026-07-08

### Added
- Created `LoginScreen` for mandatory Google Login flow.
- Added automatic Firestore schema migration (`checkAndRunFirestoreMigration()`) on startup to resolve and upload parent remote ID fields for legacy database collections.

### Changed
- Integrated mandatory login flow in `MainActivity` (displays `LoginScreen` if user is unauthenticated).
- Wrapped all `FirestoreSyncEngine` listeners in robust try-catch blocks to catch and log exceptions instead of crashing.
- Updated `TVVaultApp` to inject `appSettingDao` into `FirestoreSyncEngine`.

### Fixed
- Fixed write crash propagation by wrapping all `push*` methods in `try-catch` blocks to safely handle Firestore/network write exceptions instead of crashing on user clicks.

## [0.7.0] - 2026-07-08

### Added
- Added `mediaItemRemoteId` and `episodeRemoteId` to all sync-relevant child entities to guarantee proper cross-device parent-child relationships.
- Added support for thread-safe temporary orphan queues (`pendingMediaItemOrphans` and `pendingEpisodeOrphans` using `ConcurrentHashMap`) inside `FirestoreSyncEngine` to resolve out-of-order document arrivals during synchronization.
- Added `getById` to `EpisodeDao` for parent resolution during watch event logging.

### Changed
- Refactored `FirestoreSyncEngine` to resolve local parent IDs from parent remote IDs when receiving Firestore updates.
- Refactored `MediaRepository` to lookup parent remote IDs and populate parent links for child entities on creation or update.
- Updated all Room read queries in DAOs to filter out soft-deleted records (`deleted = 0`).
- Implemented Room database `MIGRATION_2_3` to add remote ID parent columns to all child tables and populate existing rows.

## [0.6.0] - 2026-07-07

### Added
- Integrated Firebase BoM (32.8.0), Firebase Auth, and Firestore for cloud synchronization.
- Added `AuthManager` to handle Google Sign-In via Android Credential Manager API.
- Implemented `FirestoreSyncEngine` for real-time bidirectional synchronization with Firestore.
- Added `firestore.rules` for strict user data isolation in the cloud.
- Added a new Account and Synchronization section inside `SettingsScreen` for user login.

### Changed
- Implemented Room database `MIGRATION_1_2` to add `remoteId`, `updatedAt`, and `deleted` fields to all synchronizable entities.
- Refactored Room entities to provide default constructor values, enabling native Firebase serialization via `toObject()`.
- Updated `MediaRepository` to push operations (save, delete, update state) directly to the `FirestoreSyncEngine`.
- Injected authentication and synchronization dependencies into `MediaViewModel` and `TVVaultApp`.

## [0.5.0] - 2026-07-07

### Added
- Added `.zip` and other archive extensions to `.gitignore`.
- Added automatic APK renaming logic in `app/build.gradle.kts` including version, build type and datetime.
- Added a new `SettingsScreen` to manage app preferences and backup/restore data.
- Added TMDb API Key configuration section in `SettingsScreen`.

### Changed
- Moved the Import/Export actions from the `ProfileScreen` to the new `SettingsScreen`.
- Cleaned up the `ProfileScreen` UI for a more focused user experience.

### Fixed
- Fixed an issue causing placeholder shows and movies to be added to the database on first launch.
- Explicitly set `org.gradle.java.home` in `gradle.properties` to fix stale cache errors.

## [0.4.0] - 2026-07-07

### Added
- Added a new `MediaCard` reusable component for grid lists.
- Implemented a modern typography scale in `Type.kt`.

### Changed
- Refactored `ShowsScreen` to display items in a responsive grid layout instead of a vertical list.
- Refactored `MoviesScreen` to display items in a responsive grid layout.
- Updated the `DetailScreen` to feature a massive, immersive edge-to-edge header image.
- Redesigned the bottom navigation bar to be more iconic and blend seamlessly with the dark theme.
- Updated the app's color palette in `Color.kt` and `Theme.kt` to match a more modern, premium aesthetic.

## [0.3.0] - 2026-07-07

### Added
- Settings Screen to manage user preferences (theme, group order, profile images).
- Upcoming Episodes Screen to track upcoming releases.
- Navigation links to Settings and Upcoming screens from MainActivity.
- `Coil Compose` dependency for asynchronous image loading.
- API method to fetch TV season details with air dates.
- Repository methods for user settings and upcoming episodes.

### Changed
- Improved Detail Screen and Movies Screen layouts.
- Enhanced Profile Screen appearance.
- Updated application theme settings.

### Fixed
- Updated Italian and English localized strings for filters and statuses.

## [0.2.0] - 2026-07-06

### Added
- English and Italian string resource localized directories (`values/strings.xml`, `values-it/strings.xml`).
- Space Optimization settings utility to clean unused metadata and cache entries.
- UI screens skeleton and details pages navigation (`ShowsScreen`, `MoviesScreen`, `DetailScreen`, `ProfileScreen`, `ImportJobsScreen`, `ReconciliationScreen`).

### Changed
- Optimized Float states by replacing `mutableStateOf` with `mutableFloatStateOf` in DetailScreen to prevent JVM autoboxing.
- Cleaned unused imports and redundant variables inside MainActivity, DetailScreen, ProfileScreen, Daos, MediaViewModel, and TvTimeImporter.

### Fixed
- Corrected invalid string formatting specifier templates (`%1$s`) in Italian and English localized resource tables.
- Escaped literal apostrophes (`\'`) in Italian translation files to comply with Android Lint correctness rules.
- Replaced literal three dots (`...`) with standard XML typographical ellipsis entity (`&#8230;`) in string files.
- Silenced compiler warnings and Android Lint failures related to Kotlinx Serialization generated classes by adding compiler `-opt-in` parameters and configuring Gradle `lint` properties.

## [0.1.0] - 2026-07-06

### Added
- Room Database schema with entities (`MediaItem`, `UserMediaState`, `Season`, `Episode`, `UserEpisodeState`, `WatchEvent`, `AppSetting`, `ImportJob`, `ImportMatchCandidate`).
- MediaRepository and MediaViewModel mapping local queries and network flows to Jetpack Compose screens.
- TMDb online network search and metadata synchronization (Retrofit + OkHttp with API Key interceptor and cache).
- TV Time CSV importer parsing followed shows CSV logs and resolving ambiguities manually in reconciliation matches dashboard.
- BackupManager exporting versioned JSON archives and CSV ZIP tables compilations.
- Gradle wrapper executable binary and helper shell scripts (`gradlew`, `gradlew.bat`).

### Changed
- Upgraded Retrofit dependency from `2.9.0` to `2.11.0` and fixed dependency group mappings inside `build.gradle.kts`.
