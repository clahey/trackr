# Publishing

## Context and Design Philosophy

This segment covers everything needed to get Trackr onto the Play Store and keep it there — signing, compliance forms, release builds, and store-listing creative (icon, feature graphic, screenshots, descriptions, slogan). None of it is app behavior: there's nothing here for a user to exercise at runtime, so unlike every other LLD in this project, this one has **no corresponding EARS specs and no tests** — the arrow terminates at this level by design, not by omission. It exists as an LLD anyway because it carries real decisions with rationale (why this feature-graphic approach over the alternatives, why this slogan) that are exactly as easy to lose between sessions as any code decision, and exactly as expensive to reconstruct from scratch.

## Path to Publishing

Roughly in order:

1. **Keystore** — one keystore *file* holding a **distinct key alias per app** (see Decisions). Reuse the existing `~/keystores/upload-keystore.jks`, adding a new Trackr-specific alias rather than a new file. Store it somewhere safe, not in git. Losing it means the app can never be updated on Play again — mitigated by enrolling in Google Play App Signing (Google holds the real app-signing key; the local `.jks` is then just the resettable *upload* key).
2. **Target API compliance** — `targetSdk` must meet Play's current minimum (API 34+ as of this writing).
3. **App icon** — launcher icon at all densities, adaptive icon XML (already in place — see Store Listing Assets below).
4. **Google Play Console account** — **active.** The original account, under `youraveragechris@gmail.com`, was reinstated 2026-06-30 after an inactivity closure (see "Keeping the original developer account" in Decisions). No longer a blocker.
5. **Store listing** — short description, full description, screenshots (phone + 7" tablet optional), feature graphic (1024×500).
6. **Privacy policy** — required even for local-only apps. Since Trackr collects nothing and sends nothing to any Trackr-controlled server, this can be a one-liner. Auto Backup goes to the user's own Google account, so that's covered by Google's policy, not a separate one of ours.
7. **Data safety form** — declare what's collected/shared. Local-only + Auto Backup to the user's own account is the simplest possible declaration: no data shared with third parties.
8. **Content rating** — IARC questionnaire, a few minutes.
9. **Release build** — `./gradlew bundleRelease`, signed with the keystore from step 1.
10. **Internal testing track first** — upload to internal testing, install through Play to verify the whole pipeline before production.

The long poles are usually Play Console account activation and store-listing assets (screenshots take real time to produce well). The data safety form catches people off guard — worth reading the questions before starting it.

## Store Listing Assets

**App icon.** Already built (`res/drawable/ic_launcher_*.xml`, adaptive icon): a white EKG/heartbeat line on a diagonal gradient background, with a yellow dot at the peak.

- Background gradient: `#47AADC` (light blue, top-left) → `#04325C` (dark navy, bottom-right), 135°.
- Foreground: white EKG stroke; yellow dot (`#FCD214`) at the heartbeat's peak.

**Feature graphic** (1024×500, shown above the screenshots on the Play Store listing page, and on some featured placements — matters less for a personal/utility app than a consumer app chasing featured placement, but it can render without the screenshots on some devices, so it has to communicate what the app is on its own).

Chosen approach: extend the icon's own visual language rather than a text-only graphic or a screenshot composite (see Decisions below).

- Same background gradient as the launcher icon (`#47AADC` → `#04325C`, 135°).
- Foreground: the heartbeat/EKG line fades into the background, repeated several times for depth/motion — the navy end of the gradient naturally swallows the fainter repeats — built in Inkscape as an SVG.
- Wordmark "Trackr" in white, clean sans-serif, alongside the icon art.
- Slogan **"Log anything. Fast."** underneath the wordmark.
- Yellow accent (`#FCD214`, matching the icon's dot) as a small detail — e.g. echoed in the slogan's final period — not a dominant color.
- Export: exactly 1024×500px, **PNG** (Play does not accept SVG), RGB (not CMYK), fully opaque (Play rejects transparency in this asset).

**Slogan: "Log anything. Fast."** Used on the feature graphic and available for the short description / store-listing copy generally.

## Decisions & Alternatives

| Decision | Chosen | Alternatives Considered | Rationale |
|---|---|---|---|
| Feature graphic approach | Icon + wordmark, extending the launcher icon's gradient/EKG visual language | Text-forward (app name/slogan on a plain or gradient field, no icon art); screenshot composite (phone mockup showing the timeline UI) | Trackr's Material You aesthetic and the icon's existing gradient + white + yellow color language reads as cohesive without needing a designer; a screenshot composite reads busier than fits a personal health-logging utility, and pure text forgoes the brand recognition the icon already carries |
| Slogan | "Log anything. Fast." | "Your personal health log." | Shorter, leads with the core value prop (logging *anything*, not a narrow health-only framing) and the speed promise that's also an HLD goal ("log any event in under three taps") |
| Keystore structure | One keystore *file*, a distinct key alias per app (reuse `~/keystores/upload-keystore.jks`, add a new Trackr alias) | A single shared key for all apps; a separate keystore file per app; a fresh key/file just for Trackr | One file addresses the catastrophic-loss surface (a signing key can never be rotated away, so fewer files to safeguard is safer), while per-app keys keep a compromise isolated to one app and leave each app independently transferable — the shared-key downside without the shared-key risk. Google supports reusing keys across your own apps, so a single shared key would also be valid; per-app aliases are the low-cost hedge. Play App Signing further lowers the stakes by making the local key a resettable upload key |
| Keeping the original developer account | Reinstated the account tied to `youraveragechris@gmail.com` (succeeded 2026-06-30) | Register fresh under a different email | The public email *is* the developer identity — it's the address shown on the developer page when users look up the publisher, and `youraveragechris@gmail.com` is public by design for exactly that reason. A fresh registration would surface a different, less intentional email. That outweighs the convenience of a guaranteed new registration |

## Open Questions & Future Decisions

### Active

1. **Keystore execution** (structure decided — see Decisions; this is the remaining work). On the critical path to a signed release build (step 9):
   - Add a new Trackr-specific key alias to `~/keystores/upload-keystore.jks` (the file currently holds only the now-defunct golf-score alias). Needs the keystore password.
   - Verify the file's key validity extends well past ~2033 (Studio-wizard keys default to 25 years and are fine; a `keytool`-default 90-day key would not be) — a read-only `keytool -list -v` confirms alias, fingerprint, and validity window.
   - Wire a real `release` `signingConfig` into `app/build.gradle.kts` using that alias (the golf-score `build.gradle` signed `release` with `signingConfigs.debug`; Trackr must not repeat that).
   - Enroll Trackr in Google Play App Signing on first upload, so the local `.jks` is the resettable upload key rather than the sole app-signing key.

### Deferred

1. **Screenshots** (phone, optionally 7" tablet) — not yet produced.
2. **Short description / full description copy** for the store listing — not yet drafted (the slogan can anchor the short description, but it isn't the same text).
3. **Content rating questionnaire, data safety form, privacy policy text** — not yet completed; see Path to Publishing above for what each needs to say given Trackr's local-only + Auto-Backup-only data model.

## References

- `docs/llds/app-shell.md § App Identity` — `applicationId = "net.clahey.trackr"`, the permanent Play Store identity (distinct from this LLD's concern, which is the listing/creative around that identity, not the identity itself)
- `docs/high-level-design.md § Goals` — "log any event in under three taps," echoed in the chosen slogan
- `app/src/main/res/drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml` — source of truth for the gradient and accent colors
