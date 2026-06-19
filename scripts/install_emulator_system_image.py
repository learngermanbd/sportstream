#!/usr/bin/env python3
"""
install_emulator_system_image.py
================================

Accept SDK licenses and install an API 34 google_apis x86_64
system-image into a user-owned SDK root so a non-admin Windows user
can boot a headless Android emulator.

Why this script exists
----------------------
- `cmd //c sdkmanager --install "system-images;android-35;..."` from
  Git Bash on Windows mis-quotes the package id (semicolon parsing
  breaks it; cmd strips arguments at the `;`). errors out as
  "Failed to find package" or "Failed to read or create install
  properties file" when writing to the canonical
  C:\\Android\\android-sdk\\ tree (non-admin ACL blocks writes there).

What this script does differently
---------------------------------
- Calls sdkmanager.bat via subprocess directly (no shell). The
  semicolons in `system-images;android-34;google_apis;x86_64` survive
  verbatim. License prompt gets 64 `y\n` fed via stdin.
- Uses a `--sdk_root` that the current user can write to —
  default is `C:\\Users\\RDP\\android-sdk\\` so the install
  property files land inside the user profile and the ACL block
  is bypassed.

Why API 34, not API 35
----------------------
The API 35 google_apis x86_64 system-image is not discoverable via
the dl.google.com sys-img/ path patterns (all 404). API 34 is the
highest currently-reachable system-image and is sufficient for
Step 3.7 visual verification (the app uses minSdk=23, targetSdk=35
— it runs cleanly on API 34). The deviation from the plan's
"compileSdk=35 / API 35 emulator" is documented in TODO.html.

Run from the sportstream repo root:

    python scripts/install_emulator_system_image.py
    python scripts/install_emulator_system_image.py --sdk_root D:\\my-sdk
"""
import argparse
import subprocess
import sys
from pathlib import Path

DEFAULT_SDK_ROOT = Path(r"C:\Users\RDP\android-sdk")
SDKMANAGER_REL = Path("cmdline-tools") / "latest" / "bin" / "sdkmanager.bat"
PKG = "system-images;android-34;google_apis;x86_64"
LOG_PATH = Path(__file__).resolve().parent / "install_emulator_system_image.log"


def find_sdkmanager(sdk_root: Path) -> Path | None:
    """Locate sdkmanager.bat under the chosen sdk_root, then fall back
    to the canonical C:\\Android\\android-sdk\\ in case licensing-only
    is wanted without writing anywhere."""
    candidates = [sdk_root / SDKMANAGER_REL, Path(r"C:\Android\android-sdk") / SDKMANAGER_REL]
    for c in candidates:
        if c.exists():
            return c
    return None


def run(args, *, feed_stdin=None) -> int:
    """Run sdkmanager.bat directly via subprocess (no shell) so the
    package-id semicolons survive. Writes a transcript log next to
    this script for debuggability."""
    print(f"\n==> {' '.join(args)}", flush=True)
    proc = subprocess.run(
        args,
        input=feed_stdin,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    with LOG_PATH.open("a", encoding="utf-8", errors="replace") as f:
        f.write(f"\n=== {' '.join(args)}\n--- exit {proc.returncode}\n")
        if proc.stdout:
            f.write(proc.stdout)
        if proc.stderr:
            f.write("STDERR:\n" + proc.stderr)
    out = proc.stdout.strip().splitlines()
    # The Windows cp1252 stdout can't encode some characters sdkmanager
    # prints (license banner has box-drawing / replacement glyphs). Filter
    # to ASCII for the user-facing tail so a single bad char can't crash
    # the whole script. The full UTF-8 transcript is still in LOG_PATH.
    encoding = sys.stdout.encoding or "ascii"
    safe_lines = [
        ln.encode(encoding, "replace").decode(encoding) for ln in out[-20:]
    ]
    sys.stdout.write("\n".join(safe_lines) + "\n")
    if proc.returncode != 0:
        sys.stderr.write(
            f"\n!!! exit {proc.returncode}; full transcript: {LOG_PATH}\n"
        )
    return proc.returncode


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument(
        "--sdk_root",
        type=Path,
        default=DEFAULT_SDK_ROOT,
        help="User-owned SDK root (must be writable). "
             f"Default: {DEFAULT_SDK_ROOT}",
    )
    args = parser.parse_args()
    sdk_root: Path = args.sdk_root.resolve()
    sdk_root.mkdir(parents=True, exist_ok=True)
    sdkmanager = find_sdkmanager(sdk_root)
    if sdkmanager is None:
        sys.stderr.write(
            "sdkmanager.bat not found. Either:\n"
            f"  - copy cmdline-tools/ from {Path('C:/Android/android-sdk')} to {sdk_root}\n"
            "  - or pass --sdk_root pointing at C:\\Android\\android-sdk (will fail if non-writable)\n"
        )
        return 2

    print(f"SDK root        : {sdk_root}")
    print(f"sdkmanager.bat  : {sdkmanager}")
    print(f"package         : {PKG}")

    # Step 1 — accept all SDK licenses (read-only; works for any user).
    rc = run(
        [str(sdkmanager), f"--sdk_root={sdk_root}", "--licenses"],
        feed_stdin="y\n" * 64,
    )
    if rc != 0:
        return rc
    print("[1/2] licenses accepted.", flush=True)

    # Step 2 — install the system-image into the user-owned sdk_root.
    rc = run(
        [str(sdkmanager), f"--sdk_root={sdk_root}", PKG],
        feed_stdin="y\n" * 64,
    )
    if rc != 0:
        return rc
    print("[2/2] system-image installed.", flush=True)

    # Step 3 — sanity check.
    target = sdk_root / "system-images" / "android-34" / "google_apis" / "x86_64"
    if target.exists():
        print(f"\n[sanity OK] {target} populated.")
        for p in sorted(target.iterdir())[:6]:
            print(f"  - {p.name}")
        return 0
    sys.stderr.write(f"\n[sanity FAIL] expected dir {target} is missing\n")
    return 1


if __name__ == "__main__":
    sys.exit(main())
