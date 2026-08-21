# High-Level Design: Trackr

## Problem

People tracking recurring health and lifestyle patterns (pain levels, food intake, medications, exercise, mood) have no lightweight tool that lets them define arbitrary event categories and log entries quickly. Existing apps are either too narrow (single-category trackers) or too heavy (full health platforms requiring accounts and structured schemas).

## Approach

A local-first Android app where users define their own event categories (each with a name, emoji, color, and value type) and log timestamped entries against them. All data lives on-device in a Room database. The data layer is abstracted behind a repository interface so a cloud sync or GraphQL backend can be added later without touching the UI.

## Target Users

Individuals tracking personal health and lifestyle data who want:
- Full control over what they track and how
- No account or cloud requirement
- Fast, frictionless daily logging

## Goals

- Log any event in under three taps
- Frictionless first run: a fresh install offers seeded starter categories and guided empty states rather than a blank screen
- Support at least seven value types: none (occurrence), scale 1–10, boolean, numeric with unit, free text, duration, exercise (sets × reps)
- User-defined categories with emoji, color, and value type
- Two-level category hierarchy (parent → subcategory); subcategories inherit color, emoji, and value type from their parent but can override any field individually
- Category edit screen displays a live preview of how a timeline row will look with the current name, emoji, color, and value type
- Category color used throughout the UI wherever categories are identified: timeline row avatars, filter chips, category list rows, quick-log category grid, and category edit screen
- Attach photos to an event (camera or gallery); quick-log captures one image, full edit allows multiple
- Timeline view of events grouped by day
- Data stays fully on-device; no network required
- Optional per-category reminders that prompt the user to log, either at a fixed time or at a time randomized inside a user-configured availability window — randomized reminders never land during declared unavailable hours (e.g. sleep)

## Non-Goals

- Cloud sync or multi-device in v1 (near-term: Android Auto Backup as a baseline; full sync is post-v1)
- Charts, trends, or analytics in v1
- Cross-user sharing in v1 (intended post-v1 feature; shapes the backend direction below)
- iOS support (Android-only)
- Social or community features in v1
- Adaptive or learned reminder timing (e.g. tuned from the user's own logging history) in v1 — reminders use a fixed, user-set availability window with simple in-window randomization only

## Guidelines

- **Silence over spam.** When a reminder-related design choice is ambiguous, lean toward fewer notifications rather than more — a missed or dismissed reminder is not escalated or repeated within the same window.
- **Public surfaces default to discreet.** Notification and lock-screen text defaults to generic phrasing rather than naming the specific category being tracked, since some categories (mood, symptoms, medication) are sensitive; showing more detail is an opt-in, not the default.

## System Design

```mermaid
graph TD
    UI[Compose UI Layer] --> VM[ViewModels]
    VM --> Repo[TrackrRepository interface]
    Repo --> Local[LocalTrackrRepository]
    Local --> Room[Room Database]
    Room --> DB[(SQLite)]
    VM --> Sched[ReminderScheduler]
    Sched --> Alarm[(AlarmManager)]
    Alarm --> Rcv[ReminderReceiver]
    Rcv --> Repo
    Rcv --> Notif[(NotificationManager)]
    VM --> Notif
```

**Major components:**

- **UI Layer** — Jetpack Compose screens: Home (timeline), Add Event (bottom sheet), Categories (management), Event detail/edit, About. A bottom navigation bar with two tabs (Timeline, Categories) provides top-level navigation; hidden on detail screens.
- **ViewModels** — state holders per screen; expose `StateFlow`; consume repository
- **Repository interface** — `TrackrRepository` — sole seam for future backend swap
- **LocalTrackrRepository** — Room-backed implementation
- **Room Database** — three tables: `categories`, `events`, `reminders`. `Category` supports a two-level hierarchy via `parentId: String?` (null = top-level). Subcategories store null for any field they inherit from their parent (color, emoji, valueType); top-level categories always carry explicit values. The UI layer resolves effective values by falling back to the parent when a field is null.
- **Image storage** — image files written to app-private storage (`context.filesDir`); paths stored as a JSON list in the `events` table; never stored as blobs
- **App Shell** — `TrackrApplication` (`@HiltAndroidApp`), `MainActivity` (`@AndroidEntryPoint`), Hilt modules, and the Compose `NavHost`. ViewModel arguments pass via `SavedStateHandle` (compatible with process-death restoration). The app's permanent Play Store identity is `applicationId = "net.clahey.trackr"`; this value is immutable once published.
- **Reminder Scheduler** — computes and arms per-category reminder alarms via `AlarmManager`, one per category with reminders enabled. A `BroadcastReceiver` handles the fired alarm: it posts the notification (tapping it deep-links into quick-log for that category) and immediately re-arms the next occurrence, since alarms are one-shot. A separate `BOOT_COMPLETED` receiver re-arms every pending reminder after device restart, since alarms do not survive reboot. Reminder configuration (enabled state, availability window) is edited from the category edit screen, owned by category management; the scheduler itself is a standalone component the rest of the app does not otherwise depend on. Reminders that have fired and not yet been dealt with are also listed at the top of the timeline, read from the notification shade rather than stored (see *Outstanding reminders live in the notification shade* below).

## Key Design Decisions

**Repository interface as the only seam.** All ViewModel code depends on `TrackrRepository`, not on Room directly. When a GraphQL backend is added, only a new implementation is needed — no ViewModel changes.

*Alternatives considered:* direct Room DAO injection into ViewModels (faster to write, impossible to swap); UseCase layer (extra indirection not justified at this scale).

**Outstanding reminders live in the notification shade, not in a table.** The timeline lists reminders that have fired and not yet been acted on, read live from `getActiveNotifications()`. A reminder is outstanding exactly as long as its notification is; swiping it away, or tapping it and not logging, ends it in both places at once because there is only one place. Acting on a row does what tapping its notification does — opens quick-log and cancels the notification. The list is empty after a reboot, since Android clears the shade and nothing re-posts.

This is a deliberate exception to *Repository interface as the only seam* above: a ViewModel reads state that is not behind `TrackrRepository`. It is bounded to state the OS already owns and that the app has no interest in outliving the shade — a backend swap has nothing to say about which of this device's notifications are currently showing.

The shade is read into an in-memory `StateFlow` that lives as long as the process, so screens observe a value rather than remembering to ask for one. That is a view, not a second store: it holds nothing across a process restart, and every change that produces it — the app posting, the app cancelling, the user dismissing — updates it as it happens. `setDeleteIntent` is what makes the third of those an event rather than something discovered later.

*Alternatives considered:* a persisted fired-reminders table, which survives reboot but makes the app's record and the shade two stores that can disagree, and forces every dismissal path to write to it; re-reading the shade on demand at each display, which needs no mirror but leaves every surface responsible for knowing when to look, and leaves a list stale whenever the trigger to look does not fire. The persisted table becomes the right answer if outstanding reminders should ever survive a reboot.

**Flexible value model: JSON storage + kotlinx.serialization TypeConverters.** Each category declares a `ValueType`; the stored value is JSON `String?` (null for `NONE`). A Room `TypeConverter` using kotlinx.serialization converts between the raw string and a typed `EventValue` sealed class. ViewModels and UI always work with the typed sealed class — never raw strings. This avoids a polymorphic table schema while keeping the entire stack above Room fully type-safe.

*Alternatives considered:* raw string interpreted at UI layer (leaks storage concerns into UI); separate tables per value type (over-normalized for this scale).

**Material You theming with brand-color fallback.** On Android 12+ the app uses dynamic color (adapts to the user's wallpaper). On older devices it falls back to a Material 3 tonal palette seeded from the brand color `0xFF37618E`. Dark mode is supported from the start via the standard `isSystemInDarkTheme()` switch. Default Material 3 typography and shapes throughout.

*Alternatives considered:* fixed palette only (ignores user's system preference); fully custom theme (unjustified work at this scale).

**Hilt for DI.** Standard Android DI; keeps ViewModels testable and repository injection straightforward.

**Localizable strings via Android resource system.** All user-visible text is defined in `res/values/strings.xml` (and future `res/values-XX/strings.xml` locale overrides) rather than hardcoded in Kotlin. Plurals use `<plurals>` resources and `pluralStringResource()`. The app follows the system locale; no in-app language picker is provided.

*Alternatives considered:* hardcoded strings (fast but unlocalizeable); third-party i18n libraries (unnecessary when the Android resource system covers the need).

**Two-level category hierarchy.** Categories support one level of nesting (parent → subcategory). A category with children cannot be re-parented; a category with a parent cannot receive children. This covers the grouping and subtype use case without recursive query complexity or deep-tree UI problems.

*Alternatives considered:* full tree (arbitrary depth) — adds recursive Room queries and unwieldy navigation UI; flat groups (separate entity, not loggable) — prevents logging at the group level, which allows the UX to be flexible to user needs.

**Subcategory inheritance via nullable fields.** Subcategories store `null` for color, emoji, and valueType to mean "inherit from parent." Top-level categories always carry explicit values. Effective values are resolved by the UI layer, not the repository, so all stored events reference the raw category without denormalization.

*Alternatives considered:* denormalize at write time (copy parent values) — breaks when the parent is later edited; separate override-flag columns — extra schema complexity without observability benefit over nullable fields.

**Image capture via FileProvider, no `CAMERA` permission.** Photos are captured by delegating to the system camera app via `FileProvider`; the app writes a target file to `filesDir/images` and exposes it via a `FileProvider` URI. Multiple images per event are supported on the full edit screen; the quick-log sheet captures at most one. Image paths are stored as a JSON array in the events table. File lifecycle (creation, deletion, orphan recovery) is the repository's responsibility, not the UI's.

*Alternatives considered:* in-app CameraX — richer UX but requires `CAMERA` permission and significant additional code; image BLOBs in SQLite — impractically large rows; external storage — broken by scoped storage restrictions on API 29+.

**Orphan recovery at startup.** Because DB row deletion and image file deletion are separate operations, a process kill between them can leave orphaned files. `onStartup()` scans `filesDir/images` and deletes any file not referenced by a current event row in the database, recovering storage without data loss. Stale category references (events whose category was deleted mid-session) surface gracefully as orphaned events with a missing header rather than crashing.

*Alternatives considered:* strict transactional cleanup across DB + filesystem (impossible without two-phase commit); ignore orphans (accumulates wasted device storage).

**Reminder scheduling via `AlarmManager.setExactAndAllowWhileIdle()`, not `WorkManager` or `setAlarmClock()`.** Both fixed-time and randomized-within-window reminders resolve to a single concrete timestamp before scheduling, and neither needs second-level precision — only reliable delivery close to that computed moment. `setExactAndAllowWhileIdle()` survives Doze (worst case ~9 minutes' slip under sustained deep Doze, per Android's per-app exact-alarm rate limit) without requiring the persistent alarm-clock status-bar icon that `setAlarmClock()` shows. Alarms are one-shot by design (repeating exact alarms are deprecated), so the receiver recomputes and re-arms the next occurrence every time one fires; a `BOOT_COMPLETED` receiver re-arms all pending reminders after reboot, since alarms don't survive it.

*Alternatives considered:* `WorkManager` periodic/delayed work — simpler API, but the OS may batch or defer execution well past the requested time under Doze/App Standby, unsuitable for landing near a specific randomized moment; `AlarmManager.setAlarmClock()` — fully exempt from Doze throttling, but shows a persistent alarm-clock icon implying a literal wake-up alarm is set, misrepresenting a logging nudge and intended for genuine alarm-clock use cases.

## Future Backend Strategy

The `TrackrRepository` interface is the sole seam for a future backend swap — ViewModels and UI are insulated from the storage layer.

### Near term: Android Auto Backup

Enable Android's built-in Auto Backup (configured in `AndroidManifest.xml`). The system backs up the Room database and `filesDir/images` to the user's Google account automatically, with no additional server infrastructure. Restores on reinstall or new device. This is the v1 data-safety baseline — low effort, zero operational burden.

### Post-v1: AppSync + DynamoDB + Lambda (AWS)

The intended full-sync and sharing backend. Chosen because the team is already in the AWS ecosystem.

- **AppSync** — managed GraphQL endpoint; matches the `TrackrRepository` interface's future GraphQL-backed implementation
- **Lambda resolvers** — server-side business logic layer; owns security and sharing rules (clients never have direct DB access)
- **DynamoDB** — primary data store; single-table design with GSIs for sharing access patterns (e.g., "user A shared category X with user B")
- **S3** — image storage; Lambda resolvers generate pre-signed URLs for upload/download
- **Cognito** — user auth and identity

**Implementation approach:** `LocalTrackrRepository` (Room) remains the offline-first local cache; a new `RemoteTrackrRepository` or sync layer talks to AppSync. Background sync keeps the two in agreement. Conflict resolution: last-write-wins on `updatedAt` timestamp (revisit if collaborative editing is needed).

**Sharing model:** sharing relationships are stored in DynamoDB; Lambda resolvers enforce read/write access per relationship. Enables a future web frontend against the same AppSync endpoint.

**Alternatives considered:** Firebase (Firestore + Cloud Functions) — managed but higher Google lock-in and Firestore's document model is less natural for the relational sharing graph. Supabase (Postgres + Edge Functions) — better relational fit, open source, but unfamiliar AWS tooling is not available. Custom server — maximum control but adds hosting and operational burden.

## Open Questions & Future Decisions

**Compose dependency currency, and the UI test gap behind it.** The Compose BOM is pinned at `2025.01.01` — roughly nineteen months behind as of 2026-08-21. Kotlin 2.1.0 and AGP 8.13.2 are recent enough that the bump itself is likely uneventful; the Compose compiler ships with Kotlin 2.x, so there is no separate compiler version to reconcile.

What it costs is verification rather than build breakage. Compose minor versions move visual defaults — ripple and indication, text metrics, gesture thresholds, Material3 component styling — and this project has no Compose UI tests at all, so every screen would need checking by hand. Most of that movement is Material improving rather than regressing, which is a reason to want it, but it still has to be looked at.

The two are worth doing together: the missing UI test infrastructure is what would make the *next* upgrade cheap, and it is the same gap that leaves REM-UI-\*, REM-PERM-\*, and EL-UI-096..099 without test citations today. Until then, one visible consequence: Compose 1.7's `HapticFeedbackType` offers only `LongPress` and `TextHandleMove`, so the swipe-threshold haptics in `HomeScreen.kt` go through `LocalView.performHapticFeedback` with platform constants and an explicit API-30 fallback. Compose 1.8's expanded type covers those directly and should let that block collapse.

## Success Metrics

- Cold launch to log-entry in ≤ 3 taps
- No data loss on process kill (Room transactions)
- Build compiles and installs on a physical or emulated Android 8+ device

## References

- [Room persistence library](https://developer.android.com/training/data-storage/room)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Hilt dependency injection](https://developer.android.com/training/dependency-injection/hilt-android)
- `docs/llds/publishing.md` — path to Play Store publishing, store-listing creative (icon, feature graphic, slogan), and compliance forms. No EARS specs or tests apply to this segment — it's process and external assets, not app behavior.
