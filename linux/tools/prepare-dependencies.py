#!/usr/bin/env python3
"""Fetch the Linux port's pinned tool/runtime inputs, verifying every SHA-256.

Downloads use Python's normal HTTPS certificate and proxy configuration. Existing
cache files are verified too. No Android runtime, VM, SDK install, or root access
is involved. ECJ compiles the adapter with an ordinary Java 17+ runtime.
"""
from __future__ import annotations

import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import tempfile
import urllib.request
import zipfile

MAVEN = "https://repo.maven.apache.org/maven2/"
DEPENDENCIES = [
    ("tools/dex-tools-v2.4.zip", "https://github.com/pxb1988/dex2jar/releases/download/v2.4/dex-tools-v2.4.zip", "ee7c45eb3c1d2474a6145d8d447e651a736a22d9664b6d3d3be5a5a817dda23a"),
    ("tools/ecj-3.40.0.jar", MAVEN + "org/eclipse/jdt/ecj/3.40.0/ecj-3.40.0.jar", "05cc22a24e7982970f63a405fc6c820bc80b806f27f3c5a6236fc475f8f7152b"),
    ("lib/android-4.1.1.4.jar", MAVEN + "com/google/android/android/4.1.1.4/android-4.1.1.4.jar", "84072541cbb711eff89f7277100ff854929a446dba7ceb1b195c340e0b4fd3cb"),
    ("lib/opengl-api-gl1.1-android-2.1_r1.jar", MAVEN + "org/khronos/opengl-api/gl1.1-android-2.1_r1/opengl-api-gl1.1-android-2.1_r1.jar", "cc08393220f706ef9299b453cd88f741382cee49dbeea2c667b4be1435768145"),
    ("lib/xpp3-1.1.4c.jar", MAVEN + "xpp3/xpp3/1.1.4c/xpp3-1.1.4c.jar", "0341395a481bb887803957145a6a37879853dd625e9244c2ea2509d9bb7531b9"),
    ("lib/json-20240303.jar", MAVEN + "org/json/json/20240303/json-20240303.jar", "3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed"),
    ("lib/jlayer-1.0.1.jar", MAVEN + "javazoom/jlayer/1.0.1/jlayer-1.0.1.jar", "850508c837454a1b06017c32a36876fae516de1e89a829f725fee1e6dcc52000"),
    ("lib/bcprov-jdk18on-1.80.jar", MAVEN + "org/bouncycastle/bcprov-jdk18on/1.80/bcprov-jdk18on-1.80.jar", "e8ad209f8c58d291a37ca9750e9e9fac60596956c983e49dd8282381dd8b3249"),
    ("lib/httpclient-4.2.6.jar", MAVEN + "org/apache/httpcomponents/httpclient/4.2.6/httpclient-4.2.6.jar", "362e9324ee7c697e21279e20077b52737ddef3f1b2c1a7abe5ad34b465145550"),
    ("lib/httpcore-4.2.5.jar", MAVEN + "org/apache/httpcomponents/httpcore/4.2.5/httpcore-4.2.5.jar", "e5e82da4cc66c8d917bbf743e3c0752efe8522735e7fc9dbddb65bccea81cfe9"),
    ("lib/commons-codec-1.6.jar", MAVEN + "commons-codec/commons-codec/1.6/commons-codec-1.6.jar", "54b34e941b8e1414bd3e40d736efd3481772dc26db3296f6aa45cec9f6203d86"),
    ("lib/commons-logging-1.1.3.jar", MAVEN + "commons-logging/commons-logging/1.1.3/commons-logging-1.1.3.jar", "70903f6fc82e9908c8da9f20443f61d90f0870a312642991fe8462a0b9391784"),
    ("fonts/NotoSansCJKkr-Regular.otf", "https://raw.githubusercontent.com/notofonts/noto-cjk/Sans2.004/Sans/OTF/Korean/NotoSansCJKkr-Regular.otf", "6bcb2a0703aa137e874fc2dffa85f6c21ba9a67fa329e81b8c801663af7e992a"),
    ("fonts/OFL.txt", "https://raw.githubusercontent.com/notofonts/noto-cjk/Sans2.004/LICENSE", "6a73f9541c2de74158c0e7cf6b0a58ef774f5a780bf191f2d7ec9cc53efe2bf2"),
]
LWJGL = {
    "lwjgl": ("b00e2781b74cc829db9d39fb68746b25bb7b94ce61d46293457dbccddabd999c", "2f0e65d6985d602c0e5e5aab6576db113a9a378a36f8144ec93b77a4fde5876c"),
    "lwjgl-glfw": ("b29c938ecc4997ce256a831aeca2033dab9829b5c21e72ebeb64aecd9e08450c", "a1b60014597bc0e45bf39089f4d838c3aa87fd668f6fe4e7326aa314d2ec87c0"),
    "lwjgl-opengl": ("bb0430e0a5fd7b7b5770ae517960b2ea9887bb08d04f3a0cb1aae145332e1310", "8c0b5c081a7872a3cdb02b6448921da5ae5c23ab49656f299edc7a09b7e99b74"),
    "lwjgl-openal": ("5d8669ee52325080f500cf0060785a13135a10c602286cefc8ba285805fa8468", "d30c407c1182f507c70ad12e21bac98189c767bf5c7a592dbfaa4f11d139545c"),
    "lwjgl-stb": ("ae658e15933e691b2ddba940a6e24b41a38ad6f02fd458e5e3b3ea91e222a6ab", "6a57b3b14d55194e6a326228fd7d24d1ee0c9b2b969f2cad7b3d503844961dd2"),
}
for module, checksums in LWJGL.items():
    for suffix, checksum in zip(("", "-natives-linux"), checksums):
        name = f"{module}-3.3.6{suffix}.jar"
        DEPENDENCIES.append(("lib/" + name, MAVEN + f"org/lwjgl/{module}/3.3.6/{name}", checksum))


def sha256(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def cache_path() -> Path:
    return Path(os.environ.get("PALADOG_CACHE", str(Path(os.environ.get("XDG_CACHE_HOME", str(Path.home() / ".cache"))) / "paladog-linux"))).expanduser().resolve()


def fetch(cache: Path, item: tuple[str, str, str], offline: bool) -> dict:
    name, url, expected = item
    target = cache / name
    if target.exists() and sha256(target) == expected:
        return {"path": name, "url": url, "sha256": expected, "size": target.stat().st_size}
    if offline:
        raise SystemExit(f"Missing or invalid cached dependency in offline mode: {target}")
    target.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=target.parent, delete=False) as stream:
        temporary = Path(stream.name)
        try:
            with urllib.request.urlopen(url, timeout=120) as source:
                while block := source.read(1024 * 1024):
                    stream.write(block)
            stream.close()
            actual = sha256(temporary)
            if actual != expected:
                raise ValueError(f"SHA-256 mismatch for {name}: expected {expected}, got {actual}")
            temporary.replace(target)
        finally:
            temporary.unlink(missing_ok=True)
    print(f"Verified {name}", flush=True)
    return {"path": name, "url": url, "sha256": expected, "size": target.stat().st_size}


def prepare(cache: Path, offline: bool = False) -> list[dict]:
    cache.mkdir(parents=True, exist_ok=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        records = list(pool.map(lambda item: fetch(cache, item, offline), DEPENDENCIES))
    # Re-extract from the verified archive, so modified extracted tools cannot be
    # reused just because the archive's hash still matches.
    with zipfile.ZipFile(cache / "tools/dex-tools-v2.4.zip") as archive:
        for info in archive.infolist():
            target = cache / "tools" / info.filename
            if not target.resolve().is_relative_to((cache / "tools").resolve()):
                raise ValueError(f"Unsafe archive entry: {info.filename}")
            if info.is_dir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                payload = archive.read(info)
                if not target.exists() or target.read_bytes() != payload:
                    target.write_bytes(payload)
    (cache / "dependencies.json").write_text(json.dumps(records, indent=2) + "\n")
    return records


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache", type=Path, default=cache_path())
    parser.add_argument("--offline", action="store_true", help="verify cache without downloading")
    args = parser.parse_args()
    records = prepare(args.cache.resolve(), args.offline)
    print(f"Verified {len(records)} dependencies: {args.cache.resolve()}")
