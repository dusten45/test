#!/usr/bin/env python3
"""Translate the verified offline APK's DEX to JVM classes, preserving game logic."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile

APK_SHA256 = "c8211db68b9b093da2ae9a349365063241fd18da286738c563b081fc24c15bfe"
HERE = Path(__file__).resolve().parents[1]


def digest(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def convert(apk: Path, cache: Path, output: Path) -> None:
    actual = digest(apk)
    if actual != APK_SHA256:
        raise SystemExit(f"Wrong APK: expected {APK_SHA256}, got {actual}")
    output.mkdir(parents=True, exist_ok=True)
    tool_dir = cache / "tools/dex-tools-v2.4"
    if not tool_dir.is_dir():
        raise SystemExit("Run prepare-dependencies.py before converting the game")
    # dex2jar -n propagates conversion failures instead of inserting placeholder
    # methods which would silently replace game code with a runtime exception.
    with tempfile.TemporaryDirectory(prefix="convert-", dir=output) as temporary:
        translated = Path(temporary) / "translated.jar"
        subprocess.run([
            os.environ.get("JAVA", "java"), "-Xmx2g", "-cp", str(tool_dir / "lib/*"),
            "com.googlecode.dex2jar.tools.Dex2jarCmd", "-n", "-f", "-o",
            str(translated), str(apk),
        ], cwd=temporary, check=True)
        with zipfile.ZipFile(translated) as source, zipfile.ZipFile(output / "game.jar", "w", zipfile.ZIP_DEFLATED, compresslevel=9) as target:
            classes = 0
            for name in sorted(source.namelist()):
                if name.endswith("/"):
                    continue
                info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o644 << 16
                target.writestr(info, source.read(name))
                classes += name.endswith(".class")
    assets = []
    with zipfile.ZipFile(apk) as source:
        dex_sha256 = hashlib.sha256(source.read("classes.dex")).hexdigest()
        for info in source.infolist():
            if not info.filename.startswith(("assets/", "res/")) or info.is_dir():
                continue
            target = output / "apk" / info.filename
            if not target.resolve().is_relative_to((output / "apk").resolve()):
                raise ValueError(f"Unsafe APK resource: {info.filename}")
            payload = source.read(info)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(payload)
            assets.append({"path": info.filename, "sha256": hashlib.sha256(payload).hexdigest()})
    record = {
        "input_apk_sha256": actual,
        "classes_dex_sha256": dex_sha256,
        "converter": "dex2jar 2.4",
        "converter_failure_mode": "-n: fail on errors; no exception placeholders",
        "game_classes": classes,
        "game_jar_sha256": digest(output / "game.jar"),
        "resource_files": len(assets),
        "resources": sorted(assets, key=lambda item: item["path"]),
        "game_bytecode_changes": "DEX-to-JVM translation only; no game logic patches",
    }
    (output / "conversion.json").write_text(json.dumps(record, indent=2) + "\n")
    print(f"Converted {classes} classes; extracted {len(assets)} unchanged resources")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, default=HERE.parent / "paladog-work/output/Paladog-2.2.0-modern-offline.apk")
    parser.add_argument("--cache", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=HERE / "build")
    args = parser.parse_args()
    convert(args.apk.resolve(), args.cache.resolve(), args.output.resolve())
