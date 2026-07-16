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
- `adb`, and a running emulator with the **debug** build installed
  (`net.clahey.trackr`, no debug suffix).

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

The `screenshots.sh` script in this directory exposes the reliable steps as
subcommands, so the caller does the UI navigation and invokes a primitive at
each screen:

```
./screenshots.sh seed                        # (re)build + load the demo DB
./screenshots.sh demo on                     # clean status bar
./screenshots.sh launch                      # start the app
./screenshots.sh shot shot-01-timeline.png   # on the timeline
#   ...navigate to Categories...
./screenshots.sh shot shot-02-categories.png
#   ...open the quick-log sheet...
./screenshots.sh shot shot-03-quicklog.png
#   ...back to timeline, tap a filter chip...
./screenshots.sh shot shot-04-filtered.png
#   ...back to timeline, tap the About (info) icon in the top bar...
./screenshots.sh shot shot-05-about.png
./screenshots.sh demo off
```

Shots land in `docs/store-listing/screenshots/`. Navigation between calls is
manual on purpose — scripting it would need hardcoded `input tap` coordinates,
which shift with AVD density. Read `screenshots.sh` for the exact adb commands
each subcommand runs. (Tip for driving the UI: `adb shell uiautomator dump` then
tap the element's bounds center — but note the filter chips render as
"<emoji> <name>", so match the chip by that, not the bare category name which
also appears on event rows.)

Suggested shots (the demo data is built to show these off): timeline (hero),
quick-log sheet (shows the colorful picker + "+ New category" tile), category
list, filtered-by-chip timeline, the About screen (branded hero), and optionally
an event detail/edit and the category value-type picker.

Repeat the whole pass per device — the phone (`Screenshot_Phone`, `shot-*.png`)
and the 7" tablet (`7_inch_Tablet_API_30`, `tablet7-*.png`) — via `SERIAL=...`.

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
