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

`TrackrApplication` is annotated `@HiltAndroidApp` and declared in `AndroidManifest.xml` via `android:name=".TrackrApplication"`. Its `onCreate` calls `LocalTrackrRepository.onStartup()` for the orphan image scan (see LS-BE-040).

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

`AppScaffold` is a composable that wraps the `NavHost` in a `Scaffold` with a `BottomBar`. The bottom bar is shown only on the two top-level destinations (`timeline`, `categoryList`).

## Navigation Graph

Routes are `String` constants in a `Routes` object:

```kotlin
object Routes {
    const val TIMELINE       = "timeline"
    const val CATEGORY_LIST  = "categoryList"
    const val EVENT_EDIT     = "eventEdit/{eventId}?filterCategoryId={filterCategoryId}"
    const val CATEGORY_EDIT  = "categoryEdit?categoryId={categoryId}&parentId={parentId}"
    const val ABOUT          = "about"

    fun eventEdit(eventId: String, filterCategoryId: String? = null) = ...
    fun categoryEdit(categoryId: String?, parentId: String? = null) = buildString {
        append("categoryEdit")
        if (categoryId != null) append("?categoryId=$categoryId")
        if (parentId != null) append(if (categoryId != null) "&" else "?").also { append("parentId=$parentId") }
    }
}
```

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

The **About** screen (APP-UI-010, reached via an info action in the timeline *and* category-list top app bars, APP-NAV-010) is a static info destination: a branded hero (the launcher-icon foreground over the brand gradient, wordmark, and the "Log anything. Fast." slogan with the accent word in the brand yellow — palette sourced from the launcher icon per `publishing.md`), the positioning copy as icon-led points with brand-palette icon colors (fixed brand tones, not the runtime theme): "log fast" flips per mode — bright brand yellow on dark, a darker same-hue brand yellow on light (the bright yellow vanishes on white; `theme.md § Brand colors`); "on-device" = light blue (dark blue was tried but reads as black on white); "no account" = green. Icons are top-aligned, nudged 3dp down so each glyph's top sits at its title's cap (the row top aligns to the text's line box, whose ascent sits above the cap height); kept in sync with the store listing; a source-code link and a "Feedback & feature requests" link to the GitHub issues page (both opened via `LocalUriHandler`); the app version (read from `PackageInfo`); and an "Add starter categories" action (CAT-UI-090) that reports the created count via a snackbar. The hero and the point icons use fixed colors (intentional — brand hero, semantic points); the surrounding text uses theme colors so it adapts to Material You. It takes no nav arguments and, like the other detail destinations, shows no bottom bar.

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

All five ViewModels are annotated `@HiltViewModel`.

## Startup Sequence

`TrackrApplication.onCreate()` → `repository.onStartup()` (orphan image scan).

The repository is injected into `TrackrApplication` via field injection (`@Inject lateinit var repository: TrackrRepository`).

## Decisions & Alternatives

| Decision | Chosen | Alternatives Considered | Rationale |
|---|---|---|---|
| `applicationId` | `net.clahey.trackr` | `com.trackr.app` (initial placeholder) | Reverse domain under owner's control; placeholder could not be used for Play Store publish |
| Top-level navigation | Bottom nav bar, two tabs | Toolbar icon; navigation drawer | Two tabs is exactly the right count for bottom nav; always one tap away; no discoverability problem |
| Quick-log sheet | `ModalBottomSheet` inside timeline composable | Separate nav destination | Avoids nav animation jank; timeline state (scroll position, filter) stays alive beneath the sheet |
| Test tags as resource-ids | `testTagsAsResourceId = true` on the app root, unconditionally | Debug-only gating; no tags | Lets tooling (the screenshot script, uiautomator) target elements by stable id instead of coordinates/text. FOSS app — nothing to hide by exposing ids in release, so gating would add plumbing for no benefit |
| ViewModel arguments | `SavedStateHandle` | `@AssistedInject` | Idiomatic Hilt + Navigation pattern; survives process death and back-stack restoration automatically |
| Hilt module split | Three modules (Database, DataStore, Repository) | One monolithic module | Each module is independently testable; standard Android practice |
| DataStore placement | Top-level `preferencesDataStore` delegate on Application | Manual `DataStore` construction | Delegate is the recommended API; guarantees singleton; no manual scope management |

## Open Questions & Future Decisions

1. **Tab icons** — `Icons.Default.Home` / `Icons.Default.Label` are placeholders; final icons TBD.
2. **Deep links** — not needed in v1; `SavedStateHandle` approach is compatible if added later.
3. **Splash screen** — not implemented in v1; orphan scan is fast enough to be invisible.

## References

- `docs/llds/local-storage.md` — `TrackrRepository`, `ImageStore`, `onStartup`
- `docs/llds/event-logging.md` — event screen navigation
- `docs/llds/category-management.md` — category screen navigation
- `docs/llds/theme.md` — `TrackrTheme`
