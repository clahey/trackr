# App Shell Specs

## Dependency Injection

- [ ] **APP-DI-001**: The application class shall be annotated `@HiltAndroidApp`; `MainActivity` shall be annotated `@AndroidEntryPoint`; all ViewModels shall be annotated `@HiltViewModel`.
- [ ] **APP-DI-002**: A Hilt module shall provide `TrackrDatabase`, `CategoryDao`, and `EventDao` as singletons built with `Room.databaseBuilder`.
- [ ] **APP-DI-003**: A Hilt module shall provide a singleton `DataStore<Preferences>` via the `preferencesDataStore` delegate.
- [ ] **APP-DI-004**: A Hilt module shall bind `LocalTrackrRepository` as `TrackrRepository` and `LocalImageStore` as `ImageStore`, both as singletons.

## Navigation

- [ ] **APP-NAV-001**: The app shall use a single-Activity architecture with a Compose `NavHost` as the sole navigation host.
- [ ] **APP-NAV-002**: The start destination shall be the timeline screen.
- [ ] **APP-NAV-003**: `EventEditViewModel` shall read its required `eventId` argument from `SavedStateHandle` under the key `"eventId"`.
- [ ] **APP-NAV-004**: `CategoryEditViewModel` shall read its optional `categoryId` argument from `SavedStateHandle` under the key `"categoryId"`; a null value indicates create mode.

## Bottom Navigation

- [ ] **APP-UI-001**: The app shall display a bottom navigation bar with exactly two items: Timeline and Categories.
- [ ] **APP-UI-002**: The bottom navigation bar shall be visible only when the current destination is the timeline or the category list; it shall be hidden on the event edit, category edit, and quick-log destinations.
- [ ] **APP-UI-003**: Tapping the Timeline bottom nav item shall navigate to the timeline screen.
- [ ] **APP-UI-004**: Tapping the Categories bottom nav item shall navigate to the category list screen.
- [ ] **APP-UI-005**: Tapping the currently active bottom nav item shall be a no-op — the screen shall not re-navigate or reset its state.

## Startup

- [ ] **APP-PROC-001**: On application startup, the system shall invoke `repository.onStartup()` to run the orphan image scan before any screen is shown.
