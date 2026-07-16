#!/usr/bin/env bash
#
# Primitives for capturing the Play Store phone screenshots from a DEBUG build
# of Trackr. Each subcommand does one reliable-to-automate step; the caller
# (agent, skill, or you) handles navigation in between and calls `shot` at each
# screen. Navigation is deliberately NOT scripted — that would need hardcoded
# `input tap` pixel coordinates, which are fragile and shift with AVD density.
#
# Prereqs (see SKILL.md for the full workflow):
#   - a 1080x1920 @ 420dpi emulator running (the Screenshot_Phone AVD)
#   - the DEBUG build installed (net.clahey.trackr; run-as needs debuggable)
#   - adb on PATH, plus sqlite3 for `seed`
#
# Subcommands:
#   seed              (re)build the demo DB and load it onto the device
#   launch            start the app at the launcher activity
#   demo on|off       clean status bar (fixed clock, full battery/wifi) / restore
#   shot <name.png>   capture the current screen into the screenshots dir
#
# A full pass, driving the UI by hand between calls:
#   ./screenshots.sh seed
#   ./screenshots.sh demo on
#   ./screenshots.sh launch
#   ./screenshots.sh shot shot-01-timeline.png      # on the timeline
#   #   ...navigate to Categories...
#   ./screenshots.sh shot shot-02-categories.png
#   #   ...open the quick-log sheet...
#   ./screenshots.sh shot shot-03-quicklog.png
#   #   ...back to timeline, tap a filter chip...
#   ./screenshots.sh shot shot-04-filtered.png
#   #   ...back to timeline, tap the About (info) icon in the top bar...
#   ./screenshots.sh shot shot-05-about.png
#   ./screenshots.sh demo off
#
# Env overrides:  SERIAL=emulator-5554   OUT_DIR=...

set -euo pipefail

PKG=net.clahey.trackr
SERIAL="${SERIAL:-emulator-5554}"

SCRIPT_DIR="$(dirname "$(realpath "$0")")"
REPO_ROOT="$(realpath "$SCRIPT_DIR/../../..")"
SEED_SQL="$SCRIPT_DIR/seed.sql"
DB_TMP=/tmp/trackr-demo.db
OUT_DIR="${OUT_DIR:-$REPO_ROOT/docs/store-listing/screenshots}"

# All adb calls go through the chosen device.
adb() { command adb -s "$SERIAL" "$@"; }

require_device() {
  command -v adb >/dev/null || { echo "adb not on PATH" >&2; exit 1; }
  adb get-state >/dev/null 2>&1 || {
    echo "No device at $SERIAL. Start the emulator (or set SERIAL=...)." >&2
    exit 1
  }
}

# ---------------------------------------------------------------------------
# seed: build the demo DB (timestamps relative to now, so the timeline always
# reads as "recent") and swap it in under the app's private storage.
# ---------------------------------------------------------------------------
cmd_seed() {
  command -v sqlite3 >/dev/null || { echo "sqlite3 not on PATH" >&2; exit 1; }
  [[ -f "$SEED_SQL" ]] || { echo "missing $SEED_SQL" >&2; exit 1; }
  require_device

  echo "Building demo database..."
  rm -f "$DB_TMP"
  sqlite3 "$DB_TMP" < "$SEED_SQL"

  echo "Loading it onto $SERIAL..."
  adb shell am force-stop "$PKG"
  adb push "$DB_TMP" /data/local/tmp/trackr-demo.db
  adb shell run-as "$PKG" mkdir -p databases
  adb shell run-as "$PKG" cp /data/local/tmp/trackr-demo.db databases/trackr.db
  adb shell run-as "$PKG" rm -f databases/trackr.db-wal databases/trackr.db-shm
  adb shell rm /data/local/tmp/trackr-demo.db
  echo "Seeded."
}

# ---------------------------------------------------------------------------
# launch: start the app at its launcher activity.
# ---------------------------------------------------------------------------
cmd_launch() {
  require_device
  adb shell am start -n "$PKG/.MainActivity" >/dev/null
  echo "Launched $PKG."
}

# ---------------------------------------------------------------------------
# demo on|off: SystemUI demo mode for a clean status bar. A SystemUI restart
# (e.g. a density change / cold boot) clears it, so re-run `demo on` after boot.
# ---------------------------------------------------------------------------
cmd_demo() {
  require_device
  local d="am broadcast -a com.android.systemui.demo"
  case "${1:-}" in
    on)
      adb shell settings put global sysui_demo_allowed 1
      adb shell $d -e command enter >/dev/null
      adb shell $d -e command clock -e hhmm 1000 >/dev/null
      adb shell $d -e command battery -e level 100 -e plugged false >/dev/null
      adb shell $d -e command network -e wifi show -e level 4 >/dev/null
      adb shell $d -e command notifications -e visible false >/dev/null
      echo "Demo mode on."
      ;;
    off)
      adb shell $d -e command exit >/dev/null
      echo "Demo mode off."
      ;;
    *)
      echo "usage: $0 demo on|off" >&2; exit 1
      ;;
  esac
}

# ---------------------------------------------------------------------------
# shot <name>: capture the current screen at native resolution, no post-proc.
# ---------------------------------------------------------------------------
cmd_shot() {
  local name="${1:?usage: $0 shot <filename.png>}"
  require_device
  mkdir -p "$OUT_DIR"
  adb exec-out screencap -p > "$OUT_DIR/$name"
  echo "saved -> $OUT_DIR/$name"
}

usage() {
  sed -n '3,32p' "$0"
}

case "${1:-}" in
  seed)   shift; cmd_seed   "$@" ;;
  launch) shift; cmd_launch "$@" ;;
  demo)   shift; cmd_demo   "$@" ;;
  shot)   shift; cmd_shot   "$@" ;;
  ""|-h|--help|help) usage ;;
  *) echo "unknown command: $1" >&2; usage; exit 1 ;;
esac
