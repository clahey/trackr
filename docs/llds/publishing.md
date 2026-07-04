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
| Release-signing password handling | Password kept in the developer's head; release builds signed **interactively via Android Studio's Generate Signed Bundle / APK wizard** — never written to disk or env. `app/build.gradle.kts` has no `release` `signingConfig`, so CLI `bundleRelease` yields an *unsigned* bundle by design | (a) In-Gradle interactive prompt (`System.console` + Swing fallback); (b) store it in a gitignored `keystore.properties`; (c) store it in an env var; (d) change it to something storable | The keystore password encrypts the private key *at rest*, so keeping it only in memory means a stolen `.jks` alone can't sign — the password stays a true second factor; storing it colocates both secrets, collapsing that protection to filesystem permissions. Option (a) was implemented then **abandoned**: Gradle detaches `System.console()` (null in both daemon and `--no-daemon` — it redirects the build's stdin) *and* forces `java.awt.headless=true` (defeating the Swing fallback even via `org.gradle.jvmargs`), and worse, a prompting `signingConfig` throws during *any* release configuration, which would break the wizard's own Gradle build. The Studio wizard is the tool built for interactive signing and satisfies the constraints with zero build-script fragility. Cost (signing is a manual GUI step, not CLI-automatable) is acceptable for a solo app |
| Google Play App Signing + automatic protection | **Enrolled** in Play App Signing (Google generates & holds the app-signing key; `trackr` is the resettable upload key). Google's **automatic tamper/redistribution protection** turned **off** | Keep automatic protection on (Google's default); provide a self-held app-signing key instead of a Google-generated one | Play App Signing makes the local `.jks` a resettable upload key and shifts app-signing-key custody/rotation to Google — low-stakes and the modern default (enrolled automatically on first upload, no prompt). Automatic protection injects an installer/integrity check into Google-signed binaries that nags when they're installed outside Play; that directly breaks the goal of moving freely between a Play install and a manual sideload of the same Google-signed APK. Trackr is FOSS with no paid tier/ads/license, so there is nothing to pirate and redistribution is a granted right, not a threat. Reversible — protection can be re-enabled for future versions if monetization is ever added (it protects forward-only, never retroactively) |
| Distribution channels | Both the Play Store **and** the official f-droid.org catalog | Play only; F-Droid only; self-hosted F-Droid repo (or direct APK) redistributing the Play-signed binary | Play reaches the mainstream; the official F-Droid catalog reaches the FOSS/de-Googled audience. The catalog builds from source and signs with **F-Droid's own key**, so the F-Droid build and the Play build carry **different signatures** for the same `applicationId`: a user switching sources must uninstall/reinstall (Android blocks signature-mismatched updates), and Android Auto Backup will **not** restore across the switch (restore is signature-checked; de-Googled devices lack Auto Backup entirely). Accepted as a rare case not worth complicating the setup for. The signature-unifying alternatives were rejected: a self-hosted repo/direct-APK carrying the Play-signed binary abandons the official catalog, and giving Play a self-held app-signing key is incompatible with the already-enrolled Google-generated key |
| Keeping the original developer account | Reinstated the account tied to `youraveragechris@gmail.com` (succeeded 2026-06-30) | Register fresh under a different email | The public email *is* the developer identity — it's the address shown on the developer page when users look up the publisher, and `youraveragechris@gmail.com` is public by design for exactly that reason. A fresh registration would surface a different, less intentional email. That outweighs the convenience of a guaranteed new registration |

## Open Questions & Future Decisions

### Active

*Signing and first-upload are complete (below); the durable decisions live in the Decisions table. What remains before a **production** release is the store-listing/compliance work under Deferred.*

1. **Keystore & signing — ✅ done.** `~/keystores/upload-keystore.jks` holds two per-app aliases: `golf-score` (the pre-existing 2024-11-15 `upload` key, renamed via `keytool -changealias` — a local relabel, the alias name isn't in the cert) and a new `trackr` alias (`keytool -genkeypair`, RSA 2048, `-validity 9125`; PKCS12, so key password = store password). Validity verified read-only (`keytool -list -v`, 2026-07-03): `golf-score` valid to 2049-11-09, `trackr` to 2051-06-27 — both 25-year windows, well past the ~2033 checkpoint. Release builds are signed with `trackr` via Android Studio's Generate Signed Bundle / APK wizard (see Decisions "Release-signing password handling"); `app/build.gradle.kts` deliberately has **no** `release` `signingConfig` (a comment there records why), so it neither repeats golf-score's `signingConfigs.debug` mistake nor tries (and fails) to prompt from Gradle — CLI `bundleRelease` yields an unsigned bundle.
2. **Play App Signing — ✅ enrolled.** Happened **automatically on first upload** (new apps auto-enroll — no prompt; the Console shows "Releases signed by Google Play"). Google now holds the app-signing key; `trackr` is the resettable **upload** key (SHA256 `6D:05:9C:0F:18:3E:71:31:E9:83:07:B2:91:91:E2:92:10:4E:AA:49:62:BD:35:DB:3F:59:AE:17:93:F7:56:38`). First signed bundle — `net.clahey.trackr` v0.1, versionCode 2 — uploaded clean to the **Internal testing** track (2026-07-04). (versionCode 1 was permanently consumed by Play during an aborted first attempt, hence 2.) Automatic protection deliberately **off** — see Decisions.

### Deferred

1. **Screenshots** (phone, optionally 7" tablet) — not yet produced.
2. **Short description / full description copy** for the store listing — not yet drafted (the slogan can anchor the short description, but it isn't the same text).
3. **Content rating questionnaire, data safety form, privacy policy text** — not yet completed; see Path to Publishing above for what each needs to say given Trackr's local-only + Auto-Backup-only data model.
4. **CLI interactive release signing** (deferred, not needed while the Studio wizard suffices). If a pure-CLI signed `bundleRelease` is ever wanted without storing the password, the viable route is reading from `/dev/tty` directly (bypasses Gradle's detached `System.console()` and its forced-headless AWT), which works only under `--no-daemon` (the daemon has no controlling terminal) and must be guarded so it never fires during the Studio wizard's build. A reversible 2-way door — revisit only if the manual GUI step becomes a real friction point.
5. **In-app export/import** (not this LLD's segment — flagged here because the Distribution-channels decision surfaces the need). It's the only migration path that survives a Play↔F-Droid signature switch, and the only backup at all for de-Googled devices (Android Auto Backup is signature-checked *and* requires Google Play services + a Google account). A real user-facing feature warranting its own LLD/EARS/tests cascade; recorded here as a pointer, not scoped here.

## References

- `docs/llds/app-shell.md § App Identity` — `applicationId = "net.clahey.trackr"`, the permanent Play Store identity (distinct from this LLD's concern, which is the listing/creative around that identity, not the identity itself)
- `docs/high-level-design.md § Goals` — "log any event in under three taps," echoed in the chosen slogan
- `app/src/main/res/drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml` — source of truth for the gradient and accent colors
