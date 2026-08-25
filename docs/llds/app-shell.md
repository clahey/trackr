# App Shell

## Context and Design Philosophy

This segment is the connective tissue of the app. It owns no domain logic — it wires the Hilt DI graph, hosts the Compose `NavHost`, renders the bottom navigation bar, and triggers startup work. Every other segment depends on it compiling correctly, but none depends on it for behavior.

## App Identity

| Field | Value | Notes |
|---|---|---|
| `applicationId` | `net.clahey.trackr` | Permanent Play Store identity; **cannot be changed after first publish** |
| `namespace` | `net.clahey.trackr` | Kotlin package root; matches `applicationId` by convention |
| FileProvider authority | `${applicationId}.fileprovider` | Resolved to `net.clahey.trackr.fileprovider` at build time; used for camera capture URIs |
| Auto Backup key | tied to `applicationId` | Backup/restore is scoped to this identity; changing `applicationId` severs continuity with existing backups |

## Application Class

`TrackrApplication` is annotated `@HiltAndroidApp` and declared in `AndroidManifest.xml` via `android:name=".TrackrApplication"`. Its `onCreate` launches one fire-and-forget coroutine: `ReminderScheduler.reconcileOnStartup()` (see `docs/llds/reminders.md § Scheduling Engine`), which re-arms any enabled reminder whose alarm is missing or stale. This runs on *every* process start, including one Android creates solely to deliver an alarm broadcast.

The orphan image scan (`LocalTrackrRepository.onStartup()`, LS-BE-040) is not launched here — `MainActivity` triggers it instead. See § Startup Sequence.

`AndroidManifest.xml` also registers two `reminders`-owned `BroadcastReceiver`s (`ReminderReceiver`, `ReminderRearmReceiver`) — their triggers and behavior are specced in `docs/llds/reminders.md § Scheduling Engine`; this segment only owns the fact that they're declared, not what they do.

## Hilt Modules

All modules live in `net.clahey.trackr.di`.

### DatabaseModule (`@Module`, `@InstallIn(SingletonComponent::class)`)

| Binding | Type | How |
|---|---|---|
| `TrackrDatabase` | `@Singleton` | `Room.databaseBuilder(context, TrackrDatabase::class.java, "trackr.db").build()` |
| `CategoryDao` | — | `database.categoryDao()` |
| `EventDao` | — | `database.eventDao()` |

### DataStoreModule (`@Module`, `@InstallIn(SingletonComponent::class)`)

| Binding | Type | How |
|---|---|---|
| `DataStore<Preferences>` | `@Singleton` | `context.dataStore` via `preferencesDataStore(name = "trackr_prefs")` top-level property on `TrackrApplication` |

### RepositoryModule (`@Module`, `@InstallIn(SingletonComponent::class)`)

| Binding | Type | How |
|---|---|---|
| `TrackrRepository` | `@Singleton` | `@Binds` `LocalTrackrRepository` |
| `ImageStore` | `@Singleton` | `@Binds` `LocalImageStore` |

### CoroutineModule (`@Module`, `@InstallIn(SingletonComponent::class)`)

| Binding | Type | How |
|---|---|---|
| `CoroutineScope` (`@ApplicationScope`) | `@Singleton` | `CoroutineScope(SupervisorJob() + Dispatchers.IO)` |

The qualifier keeps this from being an ambiguous binding for a type as general as `CoroutineScope`. Both consumers — `TrackrApplication`'s reminder reconcile and `UiStartupWork`'s orphan scan — need work that outlives whichever component started it.

### ClockModule (`@Module`, `@InstallIn(SingletonComponent::class)`)

| Binding | Type | How |
|---|---|---|
| `java.time.Clock` | `@Singleton` | `Clock.systemDefaultZone()` |

A testability seam, not a feature: `QuickLogViewModel` reads "now" through it for the sheet's opening timestamp, its `createdAt` stamp, and its reset, so `QuickLogViewModelTest` can substitute `Clock.fixed(...)` and assert on exact instants instead of racing the wall clock (APP-DI-006, `docs/llds/event-logging.md § Quick-Log Sheet`).

It is the only consumer. `ReminderScheduler` needs the same thing and solves it differently — every entry point takes `now`/`firedAt` as a parameter defaulted to `Instant.now()`, which its tests pass explicitly (`docs/llds/reminders.md § Scheduling Engine`). Two seams for one problem; neither is wrong, and nothing forces a choice, so both stand.

## MainActivity

`MainActivity` is annotated `@AndroidEntryPoint`. It is the single Activity.

```
setContent {
    TrackrTheme {
        val navController = rememberNavController()
        AppScaffold(navController)
    }
}
```

**Notification deep link.** A reminder notification's `PendingIntent` (see `docs/llds/reminders.md § Scheduling Engine`) targets `MainActivity` with a `categoryId` extra. On a cold start, `MainActivity` reads the launching `Intent`'s extra and passes it into the nav graph's start-destination route (`Routes.timeline(quickLogCategoryId = ...)`, see § Navigation Graph) instead of always starting bare at `"timeline"`. On a warm start (app already running), `MainActivity` overrides `onNewIntent` and forwards the extra the same way — `singleTop` launch semantics keep this from spawning a second Activity instance. No platform `NavDeepLink`/intent-filter URI scheme is used: the existing route-query-arg + `SavedStateHandle` pattern (already used for `EVENT_EDIT`/`CATEGORY_EDIT`) already covers passing a target into a composable on arrival, and adding a second, platform-native deep-link mechanism alongside it would be redundant.

**Startup work.** `onCreate` calls `UiStartupWork.runOnce()` after `setContent` — this is what triggers the orphan image scan. See § Startup Sequence.

`AppScaffold` is a composable that wraps the `NavHost` in a `Scaffold` with a `BottomBar`. The bottom bar is shown only on the two top-level destinations (`timeline`, `categoryList`).

## Navigation Graph

Routes are `String` constants in a `Routes` object:

```kotlin
object Routes {
    const val TIMELINE       = "timeline?quickLogCategoryId={quickLogCategoryId}"
    const val CATEGORY_LIST  = "categoryList"
    const val EVENT_EDIT     = "eventEdit/{eventId}?filterCategoryId={filterCategoryId}"
    const val CATEGORY_EDIT  = "categoryEdit?categoryId={categoryId}&parentId={parentId}"
    const val ABOUT          = "about"

    fun timeline(quickLogCategoryId: String? = null) = buildString {
        append("timeline")
        if (quickLogCategoryId != null) append("?quickLogCategoryId=$quickLogCategoryId")
    }
    fun eventEdit(eventId: String, filterCategoryId: String? = null) = ...
    fun categoryEdit(categoryId: String?, parentId: String? = null) = buildString {
        append("categoryEdit")
        if (categoryId != null) append("?categoryId=$categoryId")
        if (parentId != null) append(if (categoryId != null) "&" else "?").also { append("parentId=$parentId") }
    }
}
```

`quickLogCategoryId` is null on every ordinary in-app navigation to `timeline` (bottom-nav tab switch, back-navigation) — it's populated only by `MainActivity`'s notification deep-link handling above.

Full graph:

```
AppNavHost (startDestination = timeline)
├── timeline
│       ├── [FAB]            → quickLog (bottom sheet overlay)
│       ├── [About action]   → about
│       └── [tap event row]  → eventEdit/{eventId}?filterCategoryId={filterCategoryId}
├── eventEdit/{eventId}?filterCategoryId={filterCategoryId}
│       ├── [save]           → popBackStack
│       ├── [delete]         → popBackStack
│       └── [back]           → popBackStack
├── categoryList
│       ├── [FAB]            → categoryEdit (no arg)
│       └── [tap row]        → categoryEdit/{categoryId}
├── categoryEdit?categoryId={categoryId}&parentId={parentId}
│       ├── [save]           → popBackStack
│       ├── [delete]         → popBackStack
│       └── [back]           → popBackStack
└── about
        └── [back]           → popBackStack
```

The **About** screen (APP-UI-010, reached via an info action in the timeline *and* category-list top app bars, APP-NAV-010) is mostly a static info destination: a branded hero (the launcher-icon foreground over the brand gradient, wordmark, and the "Log anything. Fast." slogan with the accent word in the brand yellow — brand palette per `docs/brand.md`), the positioning copy as icon-led points with brand-palette icon colors (fixed brand tones, not the runtime theme): "log fast" flips per mode — bright brand yellow on dark, a darker same-hue brand yellow on light (the bright yellow vanishes on white; brand darker yellow per `docs/brand.md`); "on-device" = light blue (dark blue was tried but reads as black on white); "no account" = green. Icons are top-aligned, nudged 3dp down so each glyph's top sits at its title's cap (the row top aligns to the text's line box, whose ascent sits above the cap height); the slogan and positioning points are kept in sync with the Play Store listing copy (`publishing.md`, source of truth `docs/store-listing/listing-copy.md`) — the same positioning lives in both, so a change to one must update the other; a source-code link and a "Feedback & feature requests" link to the GitHub issues page (both opened via `LocalUriHandler`); the app version (read from `PackageInfo`); and an "Add starter categories" action (CAT-UI-090) that reports the created count via a snackbar. The hero and the point icons use fixed colors (intentional — brand hero, semantic points); the surrounding text uses theme colors so it adapts to Material You. It takes no nav arguments and, like the other detail destinations, shows no bottom bar.

The quick-log sheet is a `ModalBottomSheet` managed inside the `timeline` composable rather than a separate nav destination — this avoids animation jank and keeps the timeline state alive beneath the sheet.

## Bottom Navigation Bar

Two `NavigationBarItem`s:

| Tab | Route | Icon (Material Icons) |
|---|---|---|
| Timeline | `timeline` | `Icons.Default.Home` |
| Categories | `categoryList` | `Icons.Default.Label` |

Visibility: shown when `currentDestination` matches `timeline` or `categoryList`; hidden otherwise. The bar is wrapped in `AnimatedVisibility` (slide + shrink) so it eases in/out rather than appearing/vanishing instantly. Without this, toggling it snaps the `Scaffold`'s bottom inset from bar-height to 0 the instant the route changes — which reflows the still-visible outgoing screen downward mid-transition, most noticeably a vertically-centered timeline empty state (EL-UI-092/093/094). Animating the bar's height eases that inset change instead of jolting it (APP-UI-002).

Selecting an already-active tab is a no-op (no re-navigation).

## ViewModel Arguments

ViewModels that need navigation arguments inject `SavedStateHandle`:

| ViewModel | Key | Type | Default |
|---|---|---|---|
| `EventEditViewModel` | `"eventId"` | `String` | (required) |
| `EventEditViewModel` | `"filterCategoryId"` | `String?` | null (= no filter, show all events) |
| `CategoryEditViewModel` | `"categoryId"` | `String?` | null (= create mode) |
| `HomeViewModel` | `"quickLogCategoryId"` | `String?` | null (= no notification deep link); behavior on non-null documented in `docs/llds/event-logging.md § Quick-Log Sheet` |

All five ViewModels are annotated `@HiltViewModel`.

## Startup Sequence

Two pieces of startup work, two triggers.

```
TrackrApplication.onCreate() → reminderScheduler.reconcileOnStartup()   every process start
MainActivity.onCreate()      → uiStartupWork.runOnce()
                                   → repository.onStartup()             once per process
```

`UiStartupWork` is an `@Singleton` holding an `AtomicBoolean`. `runOnce()` launches the orphan image scan on the injected application scope the first time it is called in a process, and does nothing on every call after. The guard is required because `MainActivity.onCreate` runs again on configuration change and on any relaunch within a live process. The application scope rather than `lifecycleScope` is what makes it safe to launch from an Activity: a rotation part-way through must not cancel the scan, because the guard is already set and no later call would retry it.

`runOnce()` is called after `setContent`, so the scan's reads queue behind the first frame's rather than alongside them. Nothing awaits it either way — the ordering is about contention, not correctness.

Tapping a reminder notification opens `MainActivity` (§ MainActivity, notification deep link), so it triggers the scan like any other launch. A device whose owner only ever acts on notifications and never opens the app from the launcher still collects orphans on every tap.

`ReminderScheduler` is injected into `TrackrApplication` and `UiStartupWork` into `MainActivity`, both by field injection. `TrackrApplication` no longer injects the repository at all.

## Decisions & Alternatives

| Decision | Chosen | Alternatives Considered | Rationale |
|---|---|---|---|
| `applicationId` | `net.clahey.trackr` | `com.trackr.app` (initial placeholder) | Reverse domain under owner's control; placeholder could not be used for Play Store publish |
| Top-level navigation | Bottom nav bar, two tabs | Toolbar icon; navigation drawer | Two tabs is exactly the right count for bottom nav; always one tap away; no discoverability problem |
| Quick-log sheet | `ModalBottomSheet` inside timeline composable | Separate nav destination | Avoids nav animation jank; timeline state (scroll position, filter) stays alive beneath the sheet |
| Test tags as resource-ids | `testTagsAsResourceId = true` on the app root, unconditionally | Debug-only gating; no tags | Lets tooling (the screenshot script, uiautomator) target elements by stable id instead of coordinates/text. FOSS app — nothing to hide by exposing ids in release, so gating would add plumbing for no benefit |
| ViewModel arguments | `SavedStateHandle` | `@AssistedInject` | Idiomatic Hilt + Navigation pattern; survives process death and back-stack restoration automatically |
| Hilt module split | One module per concern — Database, DataStore, Repository, Coroutine here, plus modules other segments own | One monolithic module | Each module is independently testable; standard Android practice |
| DataStore placement | Top-level `preferencesDataStore` delegate on Application | Manual `DataStore` construction | Delegate is the recommended API; guarantees singleton; no manual scope management |
| Orphan-scan trigger | `MainActivity.onCreate`, once per process | `Application.onCreate`, alongside the reminder reconcile | Only UI activity produces orphans, so the uncollected set cannot grow while the app is merely being woken to deliver alarms. Triggering on process start charged a whole-event-table read and an image-directory listing to every alarm delivery — work with no deadline, competing with a broadcast that has seconds of budget — to collect a set that delivery cannot have added to |
| Reminder-reconcile trigger | `Application.onCreate`, every process start including alarm wakes | Move it to `MainActivity` too, alongside the orphan scan | An alarm wake is a good moment to notice that *other* reminders were dropped by a reboot or a Doze eviction, and the exact-alarm upgrade check (REM-SCHED-019) wants to run often rather than only when someone opens the app. The pass is cheap — one DataStore read and a bounded walk of enabled reminders. It does mean the pass can run concurrently with the delivery it woke alongside; `docs/llds/reminders.md § Decisions & Alternatives` ("Startup reconciliation staleness threshold") is where the buffer that makes that overlap harmless is specified |
| Once-per-process guard | `UiStartupWork`, an injectable `@Singleton` | An `AtomicBoolean` and a method on `TrackrApplication` | A separate injectable takes a fake repository in a JVM unit test, so the guarantee is actually covered. The Application-hosted version adds no new type but needs an instrumented test that cannot easily substitute the repository |
| Application coroutine scope | Hilt-provided `@ApplicationScope CoroutineScope` | Hand-rolled `CoroutineScope` field on `TrackrApplication` | Two collaborators now need a scope that outlives any Activity; injecting it also lets a test substitute a `TestScope` |
| Notification deep-link mechanism | Extend the existing route-query-arg + `SavedStateHandle` pattern (`quickLogCategoryId` on `Routes.TIMELINE`) | Platform `NavDeepLink` / manifest intent-filter URI scheme | The app already has a working, simple mechanism for "arrive at a composable with an argument set" (`EVENT_EDIT`/`CATEGORY_EDIT`); a second, platform-native deep-link mechanism alongside it for exactly one more argument would be redundant complexity, not added capability |

## Open Questions & Future Decisions

1. **Tab icons** — `Icons.Default.Home` / `Icons.Default.Label` are placeholders; final icons TBD.
2. **Splash screen** — not implemented in v1; orphan scan is fast enough to be invisible.
3. **Route/SavedStateHandle argument-name duplication** — argument key strings (`"categoryId"`, `"parentId"`, `"eventId"`, `"filterCategoryId"`, `"quickLogCategoryId"`) are hardcoded independently in the `Routes` object (route templates, builder functions, `navArgument(...)` declarations) and in each consuming ViewModel's `SavedStateHandle` lookup (§ ViewModel Arguments), rather than sharing a constant. `"categoryId"` and `"parentId"` each repeat across 4 call sites spanning `AppNavHost.kt` and `CategoryEditViewModel.kt`. Not yet consolidated — deferred.

## References

- `docs/llds/local-storage.md` — `TrackrRepository`, `ImageStore`, `onStartup`
- `docs/llds/event-logging.md` — event screen navigation, `HomeViewModel`'s `quickLogCategoryId` handling
- `docs/llds/category-management.md` — category screen navigation
- `docs/llds/theme.md` — `TrackrTheme`
- `docs/llds/reminders.md` — `ReminderScheduler.reconcileOnStartup()`, the two `BroadcastReceiver`s, the notification `PendingIntent` this segment's deep-link handling responds to
- `docs/llds/publishing.md` — the Play Store listing copy the About positioning (slogan + "log fast / on-device first / no account required" points) must stay in sync with
- `docs/brand.md` — the brand palette the About hero and point icons draw from (they carry literal hex per `docs/brand.md`)
