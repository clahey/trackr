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
- [x] **APP-NAV-010**: The timeline and category-list top app bars shall each provide an About action that navigates to the About screen; the About screen shall provide back navigation and, like other detail destinations, shall not show the bottom navigation bar.

## Bottom Navigation

- [x] **APP-UI-001**: The app shall display a bottom navigation bar with exactly two items: Timeline and Categories.
- [x] **APP-UI-002**: The bottom navigation bar shall be visible only when the current destination is the timeline or the category list; it shall be hidden on the event edit, category edit, and quick-log destinations.
- [x] **APP-UI-003**: Tapping the Timeline bottom nav item shall navigate to the timeline screen.
- [x] **APP-UI-004**: Tapping the Categories bottom nav item shall navigate to the category list screen.
- [x] **APP-UI-005**: Tapping the currently active bottom nav item shall be a no-op — the screen shall not re-navigate or reset its state.

## About Screen

- [x] **APP-UI-010**: The About screen shall display a branded hero (app icon, wordmark, and slogan with an accent word) over the brand gradient, the app's positioning as labelled points (log fast; on-device first; no account required — ever), a link to the source code, a link to report a bug or request a feature, the app version, and an "Add starter categories" action that reports how many categories it created.

## Startup

- [x] **APP-PROC-001**: On application startup, the system shall invoke `repository.onStartup()` to run the orphan image scan before any screen is shown.
