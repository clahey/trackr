# App Shell Specs

## App Identity

- [x] **APP-ID-001**: The application shall use `applicationId = "net.clahey.trackr"` as its permanent Play Store identity; this value shall not be changed after first publish.
- [x] **APP-ID-002**: The Kotlin `namespace` shall match the `applicationId` (`net.clahey.trackr`).
- [x] **APP-ID-003**: The FileProvider authority shall be declared as `${applicationId}.fileprovider`, resolved to `net.clahey.trackr.fileprovider` at build time.

## Dependency Injection

- [x] **APP-DI-001**: The application class shall be annotated `@HiltAndroidApp`; `MainActivity` shall be annotated `@AndroidEntryPoint`; all ViewModels shall be annotated `@HiltViewModel`.
- [x] **APP-DI-002**: A Hilt module shall provide `TrackrDatabase`, `CategoryDao`, and `EventDao` as singletons built with `Room.databaseBuilder`.
- [x] **APP-DI-003**: A Hilt module shall provide a singleton `DataStore<Preferences>` via the `preferencesDataStore` delegate.
- [x] **APP-DI-004**: A Hilt module shall bind `LocalTrackrRepository` as `TrackrRepository` and `LocalImageStore` as `ImageStore`, both as singletons.

## Navigation

- [x] **APP-NAV-001**: The app shall use a single-Activity architecture with a Compose `NavHost` as the sole navigation host.
- [x] **APP-NAV-002**: The start destination shall be the timeline screen.
- [x] **APP-NAV-003**: `EventEditViewModel` shall read its required `eventId` argument from `SavedStateHandle` under the key `"eventId"`.
- [x] **APP-NAV-004**: `CategoryEditViewModel` shall read its optional `categoryId` argument from `SavedStateHandle` under the key `"categoryId"`; a null value indicates create mode.
- [x] **APP-NAV-005**: When `MainActivity` is cold-started by a reminder notification's `PendingIntent` (`docs/specs/reminders.md § Notifications`, `REM-NOTIF-005`) carrying a `categoryId` extra, the system shall read that extra and pass it into the nav graph's start-destination route as `Routes.timeline(quickLogCategoryId = categoryId)`, instead of always starting bare at `"timeline"`.
- [x] **APP-NAV-006**: When `MainActivity` is already running and receives a new intent carrying a `categoryId` extra (warm start via `onNewIntent`), the system shall forward it the same way as APP-NAV-005; `singleTop` launch semantics shall prevent a second `MainActivity` instance from being created.

## Bottom Navigation

- [x] **APP-UI-001**: The app shall display a bottom navigation bar with exactly two items: Timeline and Categories.
- [x] **APP-UI-002**: The bottom navigation bar shall be visible only when the current destination is the timeline or the category list; it shall be hidden on the event edit, category edit, and quick-log destinations.
- [x] **APP-UI-003**: Tapping the Timeline bottom nav item shall navigate to the timeline screen.
- [x] **APP-UI-004**: Tapping the Categories bottom nav item shall navigate to the category list screen.
- [x] **APP-UI-005**: Tapping the currently active bottom nav item shall be a no-op — the screen shall not re-navigate or reset its state.

## Startup

- [x] **APP-PROC-001**: On application startup, the system shall invoke `repository.onStartup()` to run the orphan image scan before any screen is shown.
- [x] **APP-PROC-002**: On application startup, the system shall separately invoke `reminderScheduler.reconcileOnStartup()` (`docs/specs/reminders.md § Scheduling Engine`, `REM-SCHED-017`) as its own independent fire-and-forget coroutine, not composed into or dependent on `repository.onStartup()` (APP-PROC-001), so a slow or failing image scan does not block reminder re-arming or vice versa.

## Reminder Integration

- [x] **APP-REM-001**: `AndroidManifest.xml` shall declare `ReminderReceiver` and `ReminderRearmReceiver` (behavior specified in `docs/specs/reminders.md § Scheduling Engine`) as `BroadcastReceiver` components; this segment owns only the fact that they are declared, not what they do when triggered.
