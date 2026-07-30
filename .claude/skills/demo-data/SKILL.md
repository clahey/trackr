---
name: demo-data
description: Load a demo dataset (sample categories + a recent event timeline) into a debug build of Trackr for Play Store screenshots or manual UI review. Use when the user asks to populate demo/sample/screenshot data, reset the demo data, or set up for taking store screenshots.
---

# Trackr demo data

Builds a realistic sample dataset — 7 categories (one per value type) and a
~15-event timeline across the last few days — and loads it into a **debug**
build of Trackr. Purpose: Play Store screenshots and manual UI review without
hand-entering data.

The dataset is authored as SQL (`seed.sql` in this directory) and turned into a
Room-openable `trackr.db` with the `sqlite3` CLI. Nothing is compiled; no new
language runtime is added to the project.

## Why debug builds only

Loading works via `adb ... run-as`, which requires a `debuggable` app. Debug
builds are debuggable; **release builds are not**, so this cannot target a
release/Play/F-Droid install. That's fine — screenshots come from a debug build.
(Moving data across the release/F-Droid signature boundary is what the deferred
in-app export/import feature is for; this is only the dev/screenshot tool.)

Use the **emulator**, not a physical device — cleaner, consistent screenshot
frames, and physical devices are off-limits for this kind of work here.

## Prerequisites

- `sqlite3` on the PATH (`apt install sqlite3` — a dev-machine tool, not a
  project dependency).
- `adb` — but you do **not** need it on `PATH`. `screenshots.py` resolves it
  itself: `PATH`, then `$ANDROID_HOME/platform-tools`, then `~/Android/Sdk`
  (Linux) / `~/Library/Android/sdk` (macOS). Only export `ANDROID_HOME` if your
  SDK lives somewhere non-standard. (The raw `adb` snippets elsewhere in this
  doc do assume it's on PATH — prefix them if it isn't.)
- A running emulator with the **debug** build installed (`net.clahey.trackr`,
  no debug suffix).

## Build the database

From this directory:

```
sqlite3 /tmp/trackr-demo.db < seed.sql
```

This produces a complete `trackr.db` at `/tmp/trackr-demo.db` — schema, the
`room_master_table` identity hash, and the demo rows. Event timestamps are
relative to build time, so the timeline always reads as "recent"; rebuild right
before taking screenshots.

## Load it onto the emulator

The app must not be running while its database is swapped.

```
adb shell am force-stop net.clahey.trackr
adb push /tmp/trackr-demo.db /data/local/tmp/trackr-demo.db
adb shell run-as net.clahey.trackr mkdir -p databases
adb shell run-as net.clahey.trackr cp /data/local/tmp/trackr-demo.db databases/trackr.db
adb shell run-as net.clahey.trackr rm -f databases/trackr.db-wal databases/trackr.db-shm
adb shell rm /data/local/tmp/trackr-demo.db
```

Run each `run-as` as its own command — do **not** wrap them in `sh -c '...'`.
Your local shell strips the single quotes before `adb` forwards the string, so
the device's shell receives an unquoted blob and `sh -c` breaks (`mkdir: Needs 1
argument`). One `run-as` per line sidesteps the quoting entirely.

Then launch the app — the timeline shows the demo data. If it's a brand-new
install and `run-as ... mkdir` can't find the app's data dir, launch the app
once first (so it creates `databases/`), force-stop, then re-run the load.

## Capture Play Store screenshots

Play requires phone screenshots at **16:9 or 9:16**, each side 320–3840 px, and
wants **≥4 at ≥1080 px per side** for promotion eligibility. The clean target is
**1080×1920 portrait** (exactly 9:16, meets the 1080 minimum).

Use a **natively 16:9** emulator so no cropping is needed. Modern phones (Pixel
5 = 1080×2340 = 19.5:9) are the *wrong* ratio — a raw screenshot would be
rejected or need cropping. Create a **custom 1080×1920 phone** rather than
reusing a named device: Tools → Device Manager → Create Virtual Device → New
Hardware Profile, set the resolution to 1080×1920 (16:9), save, then pick a
recent system image. (The `Screenshot_Phone` AVD is exactly this.)

The `screenshots.py` script in this directory drives the whole capture itself.
The app exposes its Compose testTags as resource-ids (`testTagsAsResourceId` is
set on the app root), so the script navigates by tapping elements by **id** —
no hardcoded pixels, no text/emoji ambiguity.

Run it **once per device**, selecting the device by adb serial (from
`adb devices`) via `-s/--serial` or `$ANDROID_SERIAL`, plus a filename prefix.
It never hardcodes AVD names, which are local to each machine:

```
./screenshots.py -s <phone-serial>  capture shot-       # 1080x1920 phone
./screenshots.py -s <tablet-serial> capture tablet7-    # 7" tablet
# or: ANDROID_SERIAL=<serial> ./screenshots.py capture shot-
```

Each `capture` run seeds the demo DB, turns on the clean status bar, launches
the app, takes the five shots by resource-id, then restores the status bar.
Shots land in `docs/store-listing/screenshots/` (override with `OUT_DIR=...`).

The shots (the demo data is built to show these off): timeline (hero), the
About screen (branded hero), quick-log picker (colorful tiles + "+ New
category"), filtered-by-chip timeline, and the category list.

Primitives are also exposed for manual/one-off use (all take `-s <serial>` or
`$ANDROID_SERIAL`): `seed`, `demo on|off`, `launch`, `shot <name>`, and `tap
<resource-id>` (handy for finding a new id — run `adb -s <serial> exec-out
uiautomator dump /dev/tty` to see the ids).

## Snapshot an existing install (reverse direction)

To capture whatever is currently on the emulator (e.g. after hand-tuning the
data) into a file you can restore later:

```
adb shell am force-stop net.clahey.trackr
adb exec-out run-as net.clahey.trackr sh -c \
  'cd /data/data/net.clahey.trackr && tar cf - databases/trackr.db* files/images files/datastore' \
  > /tmp/trackr-snapshot.tar
```

Restore with the mirror `tar xf` under `run-as`. (Photos live in `files/images`
and settings in `files/datastore`; the SQL seed doesn't populate those.)

## Maintaining the dataset

- Edit rows in `seed.sql` and rebuild. `value` encodings and the column formats
  are documented at the top of `seed.sql`.
- If the Room DB version changes, re-copy the schema `CREATE` statements, the
  `PRAGMA user_version`, and the `room_master_table` identity hash from the new
  `app/schemas/net.clahey.trackr.data.local.TrackrDatabase/<version>.json`.
  Room fails loudly on open if either the version or the hash is stale.
- The built `.db` is disposable (`/tmp`) and never committed; `seed.sql` is the
  source of truth.
