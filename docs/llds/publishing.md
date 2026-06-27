# Publishing

## Context and Design Philosophy

This segment covers everything needed to get Trackr onto the Play Store and keep it there — signing, compliance forms, release builds, and store-listing creative (icon, feature graphic, screenshots, descriptions, slogan). None of it is app behavior: there's nothing here for a user to exercise at runtime, so unlike every other LLD in this project, this one has **no corresponding EARS specs and no tests** — the arrow terminates at this level by design, not by omission. It exists as an LLD anyway because it carries real decisions with rationale (why this feature-graphic approach over the alternatives, why this slogan) that are exactly as easy to lose between sessions as any code decision, and exactly as expensive to reconstruct from scratch.

## Path to Publishing

Roughly in order:

1. **Keystore** — generate a signing key, store it somewhere safe, not in git. Losing it means the app can never be updated on Play again. Consider Google Play App Signing (Google holds the upload key) as a safety net.
2. **Target API compliance** — `targetSdk` must meet Play's current minimum (API 34+ as of this writing).
3. **App icon** — launcher icon at all densities, adaptive icon XML (already in place — see Store Listing Assets below).
4. **Google Play Console account** — $25 one-time fee; activation can take a day or two.
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
| Keeping the original developer account | Reinstate the account tied to `youraveragechris@gmail.com` (fall back to a new email only if denied) | Register fresh under a different email | The public email *is* the developer identity — it's the address shown on the developer page when users look up the publisher, and `youraveragechris@gmail.com` is public by design for exactly that reason. A fresh registration would surface a different, less intentional email. That outweighs the convenience of a guaranteed new registration |

## Open Questions & Future Decisions

### Blocked

1. **Google Play Console developer account is closed (inactivity); resolution in progress (as of 2026-06-26).** The prior account — email `youraveragechris@gmail.com`, under which `net.clahey.golfscore` (a separate app, [`clahey/golf-score`](https://github.com/clahey/golf-score)) was being prepared — was closed by Google for inactivity. Google Play Developer Support has confirmed that **a closed account's email cannot be reused** for a new registration. Two paths offered: (a) reinstate the original account via emailed business justification (not guaranteed), or (b) register a new account under a *different* email. **Chosen path: attempt reinstatement first**, falling back to a new-email registration if denied — see "Keeping the original developer account" in Decisions below for why the original account matters. A reinstatement reply with business justification (active development of Trackr, ending the inactivity) is drafted and ready to send. This blocks step 4 of Path to Publishing (and everything after it) until a usable account exists.

### Deferred

1. **Screenshots** (phone, optionally 7" tablet) — not yet produced.
2. **Short description / full description copy** for the store listing — not yet drafted (the slogan can anchor the short description, but it isn't the same text).
3. **Content rating questionnaire, data safety form, privacy policy text** — not yet completed; see Path to Publishing above for what each needs to say given Trackr's local-only + Auto-Backup-only data model.
4. **Keystore generation and storage** — not yet done for Trackr specifically. Note: an existing upload keystore from prior GolfScore publishing prep is sitting at `~/keystores/upload-keystore.jks` (generated Nov 2024, never wired into the [`clahey/golf-score`](https://github.com/clahey/golf-score) `build.gradle` — its `release` build type currently signs with `signingConfigs.debug`, not a real release key). Whether to reuse that key for Trackr or generate a fresh one is an open decision, independent of the developer-account question above.

## References

- `docs/llds/app-shell.md § App Identity` — `applicationId = "net.clahey.trackr"`, the permanent Play Store identity (distinct from this LLD's concern, which is the listing/creative around that identity, not the identity itself)
- `docs/high-level-design.md § Goals` — "log any event in under three taps," echoed in the chosen slogan
- `app/src/main/res/drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml` — source of truth for the gradient and accent colors
