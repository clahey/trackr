#!/usr/bin/env python3
"""
Capture the Play Store screenshots from a DEBUG build of Trackr.

Navigation is fully self-driving: the app exposes Compose testTags as
resource-ids (`testTagsAsResourceId = true` on the app root), so this taps
elements by id — no hardcoded pixels, no text/emoji ambiguity.

The target device serial (from `adb devices`) comes from `-s/--serial` or
`$ANDROID_SERIAL`. Run once per device, with a filename prefix per device
(AVD names are local to each machine, so the script never hardcodes them):

    ./screenshots.py -s emulator-5554 capture shot-      # 1080x1920 phone
    ./screenshots.py -s emulator-5556 capture tablet7-   # 7" tablet
    # or: ANDROID_SERIAL=emulator-5554 ./screenshots.py capture shot-

Shots land in docs/store-listing/screenshots/ (override with OUT_DIR=...).

Primitives are also exposed for manual/one-off use (all honor -s / $ANDROID_SERIAL):
    ./screenshots.py -s <serial> seed
    ./screenshots.py -s <serial> demo on|off
    ./screenshots.py -s <serial> launch
    ./screenshots.py -s <serial> shot <name.png>
    ./screenshots.py -s <serial> tap <resource-id>     # debug helper
"""

from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PKG: str = "net.clahey.trackr"
SCRIPT_DIR: Path = Path(__file__).resolve().parent
REPO_ROOT: Path = SCRIPT_DIR.parents[2]  # .claude/skills/demo-data -> repo root
SEED_SQL: Path = SCRIPT_DIR / "seed.sql"
DB_TMP: Path = Path("/tmp/trackr-demo.db")
OUT_DIR: Path = Path(os.environ.get("OUT_DIR") or REPO_ROOT / "docs/store-listing/screenshots")

_BOUNDS_RE: re.Pattern[str] = re.compile(r"\[(\d+),(\d+)]\[(\d+),(\d+)]")


class CaptureError(Exception):
    """A capture operation failed (device unreachable, element not found, ...)."""


class Adb:
    """A thin adb transport bound to one device serial.

    The adb binary is resolved once per execution and cached on the class, so
    even multiple devices share a single lookup.
    """

    _bin: str | None = None  # cached path to the adb executable

    def __init__(self, serial: str) -> None:
        self.serial = serial

    @classmethod
    def binary(cls) -> str:
        if cls._bin is None:
            found = shutil.which("adb")
            if not found:  # SDK need not be on PATH
                for cand in (
                    Path(os.environ.get("ANDROID_HOME", "")) / "platform-tools" / "adb",
                    Path.home() / "Android/Sdk/platform-tools/adb",
                    Path.home() / "Library/Android/sdk/platform-tools/adb",
                ):
                    if os.access(cand, os.X_OK):
                        found = str(cand)
                        break
            if not found:
                raise FileNotFoundError("adb not found on PATH or in a common SDK location")
            cls._bin = found
        return cls._bin

    def run(self, *args: str, check: bool = False, capture: bool = False) -> subprocess.CompletedProcess[bytes]:
        return subprocess.run([self.binary(), "-s", self.serial, *args],
                              check=check, capture_output=capture)

    def out(self, *args: str) -> bytes:
        """Raw stdout bytes of an adb call."""
        return self.run(*args, capture=True).stdout

    def shell(self, *args: str, check: bool = False, capture: bool = False) -> subprocess.CompletedProcess[bytes]:
        return self.run("shell", *args, check=check, capture=capture)

    def require(self) -> None:
        if self.run("get-state", capture=True).returncode != 0:
            raise CaptureError(f"no device at '{self.serial}' (see `adb devices`)")

    def tap(self, x: int, y: int) -> None:
        self.shell("input", "tap", str(x), str(y))

    def back(self) -> None:
        self.shell("input", "keyevent", "4")
        time.sleep(1)

    def find(self, rid: str) -> tuple[int, int] | None:
        """Center (x, y) of the node with this resource-id, or None."""
        self.shell("uiautomator", "dump", "/sdcard/ui.xml", capture=True)
        try:
            root = ET.fromstring(self.out("exec-out", "cat", "/sdcard/ui.xml"))
        except ET.ParseError:
            return None
        for node in root.iter("node"):
            if node.get("resource-id") == rid:
                m = _BOUNDS_RE.fullmatch(node.get("bounds", ""))
                if m:
                    x1, y1, x2, y2 = (int(v) for v in m.groups())
                    return (x1 + x2) // 2, (y1 + y2) // 2
        return None

    def wait_id(self, rid: str, tries: int = 15) -> tuple[int, int]:
        """Block until the element appears (one dump per second), then return its center."""
        for _ in range(tries):
            xy = self.find(rid)
            if xy is not None:
                return xy
            time.sleep(1)
        raise CaptureError(f"element '{rid}' not found on {self.serial}")

    def tap_id(self, rid: str) -> None:
        xy = self.wait_id(rid)
        time.sleep(1)  # element is in the tree but may still be animating in (e.g. a nav
        self.tap(*xy)  # transition); let it finish drawing so the tap isn't dropped


def seed(adb: Adb) -> None:
    if not SEED_SQL.exists():
        raise FileNotFoundError(f"missing {SEED_SQL}")
    if shutil.which("sqlite3") is None:
        raise FileNotFoundError("sqlite3 not on PATH")
    adb.require()
    print("Building demo database...")
    if DB_TMP.exists():
        DB_TMP.unlink()
    with SEED_SQL.open("rb") as f:
        subprocess.run(["sqlite3", str(DB_TMP)], stdin=f, check=True)
    print(f"Loading onto {adb.serial}...")
    # The app must not be running while its database is swapped. Each run-as is
    # its own command (never `sh -c '...'`, which the local shell would mangle).
    adb.shell("am", "force-stop", PKG, check=True)
    adb.run("push", str(DB_TMP), "/data/local/tmp/trackr-demo.db", check=True, capture=True)
    adb.shell("run-as", PKG, "mkdir", "-p", "databases", check=True)
    adb.shell("run-as", PKG, "cp", "/data/local/tmp/trackr-demo.db", "databases/trackr.db", check=True)
    adb.shell("run-as", PKG, "rm", "-f", "databases/trackr.db-wal", "databases/trackr.db-shm")
    adb.shell("rm", "/data/local/tmp/trackr-demo.db")
    print("Seeded.")


def demo(adb: Adb, state: str) -> None:
    adb.require()
    b = ("am", "broadcast", "-a", "com.android.systemui.demo")
    if state == "on":
        adb.shell("settings", "put", "global", "sysui_demo_allowed", "1")
        adb.shell(*b, "-e", "command", "enter", capture=True)
        adb.shell(*b, "-e", "command", "clock", "-e", "hhmm", "1000", capture=True)
        adb.shell(*b, "-e", "command", "battery", "-e", "level", "100",
                  "-e", "plugged", "false", capture=True)
        adb.shell(*b, "-e", "command", "network", "-e", "wifi", "show",
                  "-e", "level", "4", capture=True)
        adb.shell(*b, "-e", "command", "notifications", "-e", "visible", "false", capture=True)
        print("Demo mode on.")
    else:
        adb.shell(*b, "-e", "command", "exit", capture=True)
        print("Demo mode off.")


def launch(adb: Adb) -> None:
    adb.require()
    # -W blocks until the first frame is actually displayed. Without it a cold start
    # returns immediately and an early screencap catches an unpainted (blank) frame.
    adb.shell("am", "start", "-W", "-n", f"{PKG}/.MainActivity", capture=True)
    print(f"Launched {PKG}.")


def shot(adb: Adb, name: str) -> None:
    adb.require()
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    (OUT_DIR / name).write_bytes(adb.out("exec-out", "screencap", "-p"))
    print(f"saved -> {OUT_DIR / name}")


def capture(adb: Adb, prefix: str) -> None:
    """The whole shot set for one device, driven entirely by resource-ids."""
    print(f"== capturing on {adb.serial} (prefix '{prefix}') ==")
    seed(adb)
    demo(adb, "on")
    launch(adb)
    adb.wait_id("filter_all")  # the category filter row has rendered, i.e. the seeded data is loaded
    time.sleep(1)              # brief settle for the event list to draw
    shot(adb, f"{prefix}01-timeline.png")

    # Shots 03-05 are all reachable from the timeline with no intervening navigation,
    # so their taps land on a settled screen. Categories (02) needs a nav, so it goes
    # last — a FAB tap immediately after a nav transition gets dropped mid-animation.
    adb.tap_id("log_event_fab")
    time.sleep(2)
    shot(adb, f"{prefix}03-quicklog.png")
    adb.back()

    adb.tap_id("filter_chip_cat-mood")
    time.sleep(2)
    shot(adb, f"{prefix}04-filtered.png")

    adb.tap_id("about_action")
    time.sleep(2)
    shot(adb, f"{prefix}05-about.png")
    adb.back()

    adb.tap_id("nav_categories")
    time.sleep(2)
    shot(adb, f"{prefix}02-categories.png")

    demo(adb, "off")


def main() -> None:
    p = argparse.ArgumentParser(description="Trackr Play Store screenshot capture.")
    p.add_argument("-s", "--serial", default=os.environ.get("ANDROID_SERIAL"),
                   help="device serial from `adb devices` (default: $ANDROID_SERIAL)")
    sub = p.add_subparsers(dest="cmd", required=True)
    sub.add_parser("seed")
    sub.add_parser("launch")
    sub.add_parser("demo").add_argument("state", choices=["on", "off"])
    sub.add_parser("shot").add_argument("name")
    sub.add_parser("tap").add_argument("id")
    sub.add_parser("capture").add_argument("prefix")
    a = p.parse_args()

    if not a.serial:
        p.error("no device serial: pass --serial/-s or set ANDROID_SERIAL")

    adb = Adb(a.serial)
    actions = {
        "seed": lambda: seed(adb),
        "launch": lambda: launch(adb),
        "demo": lambda: demo(adb, a.state),
        "shot": lambda: shot(adb, a.name),
        "tap": lambda: adb.tap_id(a.id),
        "capture": lambda: capture(adb, a.prefix),
    }
    try:
        actions[a.cmd]()
    except (CaptureError, FileNotFoundError, subprocess.CalledProcessError) as e:
        print(f"error: {e}", file=sys.stderr)
        raise SystemExit(1)


if __name__ == "__main__":
    main()
