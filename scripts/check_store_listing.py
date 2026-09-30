#!/usr/bin/env python3
"""Validate fastlane/metadata/android against Google Play's listing rules.

The tree is pushed to Play on every release (and harvested by F-Droid), so a
field over its cap, an image Play rejects, or a locale Play doesn't know would
fail the upload after the release has shipped everywhere else.

  - every locale directory is listed in fastlane/store-locales.txt, and every
    app language (res/values-*) has an entry there and a directory
  - title / short / full description exist, are non-empty and within Play's caps,
    and the full description isn't hard-wrapped
  - icon: 512x512 32-bit PNG; featureGraphic: 1024x500 JPEG or 24-bit PNG
  - screenshots: JPEG or 24-bit PNG, sides 320..3840, long edge <= 2x short,
    2..8 per type
"""
import re
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
META = ROOT / "fastlane/metadata/android"
MAPPING = ROOT / "fastlane/store-locales.txt"
RES = ROOT / "app/src/main/res"

TEXT_LIMITS = {"title.txt": 30, "short_description.txt": 80, "full_description.txt": 4000}
LISTING_ENTRIES = set(TEXT_LIMITS) | {"changelogs", "images", "video.txt"}
SCREENSHOT_DIRS = {"phoneScreenshots", "sevenInchScreenshots", "tenInchScreenshots",
                   "tvScreenshots", "wearScreenshots"}
IMAGE_EXTS = {".png", ".jpg", ".jpeg"}

errors = []


def error(msg):
    errors.append(msg)


def rel(path):
    return path.relative_to(ROOT)


def image_info(path):
    """(width, height, has_alpha, bits_per_pixel) for a PNG or baseline JPEG."""
    data = path.read_bytes()
    if data[:8] == b"\x89PNG\r\n\x1a\n":
        width, height, depth, color_type = struct.unpack(">IIBB", data[16:26])
        channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}.get(color_type, 0)
        has_alpha = color_type in (4, 6) or b"tRNS" in data[:data.find(b"IDAT")]
        return width, height, has_alpha, depth * channels
    if data[:2] == b"\xff\xd8":
        i = 2
        while i + 9 < len(data):
            if data[i] != 0xFF:
                i += 1
                continue
            marker = data[i + 1]
            length = struct.unpack(">H", data[i + 2:i + 4])[0]
            if 0xC0 <= marker <= 0xCF and marker not in (0xC4, 0xC8, 0xCC):
                height, width = struct.unpack(">HH", data[i + 5:i + 9])
                return width, height, False, 24
            i += 2 + length
    return None


def check_mapping():
    mapping = {}
    for line in MAPPING.read_text(encoding="utf-8").splitlines():
        line = line.split("#", 1)[0].strip()
        if line:
            qualifier, store = line.split()
            mapping[qualifier] = store

    app_langs = {"default"} | {
        d.name[len("values-"):] for d in RES.glob("values-*")
        if re.fullmatch(r"values-[a-z]{2,3}(-r[A-Z]{2})?", d.name)
    }
    for q in sorted(app_langs - mapping.keys()):
        error(f"app ships values-{q} but {rel(MAPPING)} has no store locale for it")
    for q in sorted(mapping.keys() - app_langs):
        error(f"{rel(MAPPING)} maps '{q}' but the app has no values-{q}")

    stores = set(mapping.values())
    dirs = {d.name for d in META.iterdir() if d.is_dir()}
    for d in sorted(dirs - stores):
        error(f"{rel(META / d)} is not a mapped store locale (see {rel(MAPPING)})")
    for d in sorted(stores - dirs):
        error(f"no listing for {d} — create {rel(META / d)}")
    return sorted(dirs & stores)


def check_text(locale_dir):
    for name, limit in TEXT_LIMITS.items():
        f = locale_dir / name
        if not f.is_file():
            error(f"{rel(f)} is missing")
            continue
        text = f.read_text(encoding="utf-8").strip()
        if not text:
            error(f"{rel(f)} is empty")
        elif len(text) > limit:
            error(f"{rel(f)} is {len(text)} chars (Play limit {limit})")
        if name == "full_description.txt" and re.search(r"[^\n]\n(?![\n•*-])", text):
            error(f"{rel(f)} is hard-wrapped; Play shows every newline, so keep a paragraph on one line")
    for entry in locale_dir.iterdir():
        if entry.name not in LISTING_ENTRIES:
            error(f"{rel(entry)} is not a listing file supply knows")


def check_image(f, kind):
    info = image_info(f)
    if f.suffix.lower() not in IMAGE_EXTS or info is None:
        error(f"{rel(f)} is not a PNG or JPEG")
        return
    width, height, has_alpha, bpp = info
    if kind == "icon":
        if (width, height) != (512, 512) or bpp != 32 or f.suffix.lower() != ".png":
            error(f"{rel(f)} is {width}x{height} {bpp}-bit; Play wants a 512x512 32-bit PNG")
    elif kind == "featureGraphic":
        if (width, height) != (1024, 500):
            error(f"{rel(f)} is {width}x{height}; Play wants exactly 1024x500")
        if has_alpha:
            error(f"{rel(f)} has an alpha channel; Play wants JPEG or 24-bit PNG")
    else:
        short, long = sorted((width, height))
        if short < 320 or long > 3840:
            error(f"{rel(f)} is {width}x{height}; each side must be 320..3840")
        if long > 2 * short:
            error(f"{rel(f)} is {width}x{height}; the long edge may be at most 2x the short edge")
        if has_alpha:
            error(f"{rel(f)} has an alpha channel; Play wants JPEG or 24-bit PNG")


def check_images(locale_dir):
    images = locale_dir / "images"
    if not images.is_dir():
        return
    for entry in sorted(images.iterdir()):
        if entry.is_dir():
            if entry.name not in SCREENSHOT_DIRS:
                error(f"{rel(entry)} is not a screenshot type supply knows")
                continue
            shots = sorted(p for p in entry.iterdir() if p.is_file())
            if not 2 <= len(shots) <= 8:
                error(f"{rel(entry)} has {len(shots)} images; Play takes 2..8")
            for shot in shots:
                check_image(shot, "screenshot")
        elif entry.stem in ("icon", "featureGraphic"):
            check_image(entry, entry.stem)
        else:
            error(f"{rel(entry)} is not an image supply knows (icon, featureGraphic, *Screenshots/)")


def main():
    for locale in check_mapping():
        check_text(META / locale)
        check_images(META / locale)
    if errors:
        for e in errors:
            print(f"ERROR: {e}", file=sys.stderr)
        print(f"\n{len(errors)} store listing problem(s).", file=sys.stderr)
        return 1
    print("Store listing OK.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
