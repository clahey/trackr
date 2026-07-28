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

`TrackrApplication` is annotated `@HiltAndroidApp` and declared in `AndroidManifest.xml` via `android:name=".TrackrApplication"`. Its `onCreate` launches two independent fire-and-forget coroutines: `LocalTrackrRepository.onStartup()` for the orphan image scan (see LS-BE-040), and `ReminderScheduler.reconcileOnStartup()` (see `docs/llds/reminders.md § Scheduling Engine`) to re-arm any enabled reminder whose alarm is missing or stale. The two are launched separately, not composed into one call — re-arming alarms needs `AlarmManager` access, which sits outside `local-storage`'s persistence-only seam (see `docs/llds/local-storage.md § LocalTrackrRepository`), so it can't live inside `LocalTrackrRepository.onStartup()` regardless of what that method currently does (LS-BE-041).

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

**Notification deep link.** A reminder notification's `PendingIntent` (see `docs/llds/reminders.md § Scheduling Engine`) targets `MainActivity` with a `categoryId` extra. On a cold start, `MainActivity` reads the launching `Intent`'s extra and passes it into the nav graph's start-destination route (`Routes.timeline(quickLogCategoryId = ...)`, see § Navigation Graph) instead of always starting bare at `"timeline"`. On a warm start (app already running), `MainActivity` overrides `onNewIntent` and forwards the extra the same way — `singleTop` launch semantics keep this from spawning a second Activity instance. This resolves the "Deep links" open question below: no platform `NavDeepLink`/intent-filter URI scheme is used, since the existing route-query-arg + `SavedStateHandle` pattern (already used for `EVENT_EDIT`/`CATEGORY_EDIT`) already covers passing a target into a composable on arrival, and adding a second, platform-native deep-link mechanism alongside it would be redundant.

`AppScaffold` is a composable that wraps the `NavHost` in a `Scaffold` with a `BottomBar`. The bottom bar is shown only on the two top-level destinations (`timeline`, `categoryList`).

## Navigation Graph

Routes are `String` constants in a `Routes` object:

```kotlin
object Routes {
    const val TIMELINE       = "timeline?quickLogCategoryId={quickLogCategoryId}"
    const val CATEGORY_LIST  = "categoryList"
    const val EVENT_EDIT     = "eventEdit/{eventId}?filterCategoryId={filterCategoryId}"
    const val CATEGORY_EDIT  = "categoryEdit?categoryId={categoryId}&parentId={parentId}"

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
│       └── [tap event row]  → eventEdit/{eventId}?filterCategoryId={filterCategoryId}
├── eventEdit/{eventId}?filterCategoryId={filterCategoryId}
│       ├── [save]           → popBackStack
│       ├── [delete]         → popBackStack
│       └── [back]           → popBackStack
├── categoryList
│       ├── [FAB]            → categoryEdit (no arg)
│       └── [tap row]        → categoryEdit/{categoryId}
└── categoryEdit?categoryId={categoryId}&parentId={parentId}
        ├── [save]           → popBackStack
        ├── [delete]         → popBackStack
        └── [back]           → popBackStack
```

The quick-log sheet is a `ModalBottomSheet` managed inside the `timeline` composable rather than a separate nav destination — this avoids animation jank and keeps the timeline state alive beneath the sheet.

## Bottom Navigation Bar

Two `NavigationBarItem`s:

| Tab | Route | Icon (Material Icons) |
|---|---|---|
| Timeline | `timeline` | `Icons.Default.Home` |
| Categories | `categoryList` | `Icons.Default.Label` |

Visibility: shown when `currentDestination` matches `timeline` or `categoryList`; hidden otherwise.

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

`TrackrApplication.onCreate()` → `repository.onStartup()` (orphan image scan) and, separately, `reminderScheduler.reconcileOnStartup()` (re-arm missing/stale reminder alarms; see `docs/llds/reminders.md § Scheduling Engine`).

The repository and `ReminderScheduler` are injected into `TrackrApplication` via field injection (`@Inject lateinit var repository: TrackrRepository`, `@Inject lateinit var reminderScheduler: ReminderScheduler`).

## Decisions & Alternatives

| Decision | Chosen | Alternatives Considered | Rationale |
|---|---|---|---|
| `applicationId` | `net.clahey.trackr` | `com.trackr.app` (initial placeholder) | Reverse domain under owner's control; placeholder could not be used for Play Store publish |
| Top-level navigation | Bottom nav bar, two tabs | Toolbar icon; navigation drawer | Two tabs is exactly the right count for bottom nav; always one tap away; no discoverability problem |
| Quick-log sheet | `ModalBottomSheet` inside timeline composable | Separate nav destination | Avoids nav animation jank; timeline state (scroll position, filter) stays alive beneath the sheet |
| ViewModel arguments | `SavedStateHandle` | `@AssistedInject` | Idiomatic Hilt + Navigation pattern; survives process death and back-stack restoration automatically |
| Hilt module split | Three modules (Database, DataStore, Repository) | One monolithic module | Each module is independently testable; standard Android practice |
| DataStore placement | Top-level `preferencesDataStore` delegate on Application | Manual `DataStore` construction | Delegate is the recommended API; guarantees singleton; no manual scope management |
| Notification deep-link mechanism | Extend the existing route-query-arg + `SavedStateHandle` pattern (`quickLogCategoryId` on `Routes.TIMELINE`) | Platform `NavDeepLink` / manifest intent-filter URI scheme | The app already has a working, simple mechanism for "arrive at a composable with an argument set" (`EVENT_EDIT`/`CATEGORY_EDIT`); a second, platform-native deep-link mechanism alongside it for exactly one more argument would be redundant complexity, not added capability |

## Open Questions & Future Decisions

1. **Tab icons** — `Icons.Default.Home` / `Icons.Default.Label` are placeholders; final icons TBD.
2. ~~**Deep links**~~ — resolved: needed as of the `reminders` segment (notification tap → timeline with `quickLogCategoryId`), handled via the existing `SavedStateHandle` route-arg pattern rather than a platform deep-link mechanism. See § MainActivity and § Navigation Graph.
3. **Splash screen** — not implemented in v1; orphan scan is fast enough to be invisible.

## References

- `docs/llds/local-storage.md` — `TrackrRepository`, `ImageStore`, `onStartup`
- `docs/llds/event-logging.md` — event screen navigation, `HomeViewModel`'s `quickLogCategoryId` handling
- `docs/llds/category-management.md` — category screen navigation
- `docs/llds/theme.md` — `TrackrTheme`
- `docs/llds/reminders.md` — `ReminderScheduler.reconcileOnStartup()`, the two `BroadcastReceiver`s, the notification `PendingIntent` this segment's deep-link handling responds to
