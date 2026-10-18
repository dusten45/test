#!/usr/bin/env python3
"""Prepare pinned runtime, notices, and corresponding sources for Linux packaging."""
from __future__ import annotations

import argparse
import concurrent.futures
import importlib.util
import json
from pathlib import Path
import shutil
import tarfile
import tempfile
import zipfile

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("dependencies", HERE / "prepare-dependencies.py")
dependencies = importlib.util.module_from_spec(spec)
spec.loader.exec_module(dependencies)
JRE_ARCHIVE = "tools/OpenJDK21U-jre_x64_linux_hotspot_21.0.12.1_1.tar.gz"
JRE_DIRECTORY = "jdk-21.0.12.1+1-jre"


def collect(cache: Path, offline: bool) -> None:
    records = json.loads((HERE / "package-inputs.json").read_text())
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        list(pool.map(lambda item: dependencies.fetch(cache,
            (item["path"], item["url"], item["sha256"]), offline), records))
    # Start from the checked archive, preserving all original JRE legal files.
    runtime = cache / "runtime" / JRE_DIRECTORY
    runtime.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=runtime.parent) as temporary:
        with tarfile.open(cache / JRE_ARCHIVE) as archive:
            archive.extractall(temporary, filter="data")
        if runtime.exists():
            shutil.rmtree(runtime)
        shutil.move(str(Path(temporary) / JRE_DIRECTORY), runtime)
    notices = cache / "licenses"
    notices.mkdir(exist_ok=True)
    for library in sorted((cache / "lib").glob("*.jar")):
        with zipfile.ZipFile(library) as archive:
            for name in archive.namelist():
                if name.rsplit("/", 1)[-1].upper() in {"LICENSE", "NOTICE", "LICENSE.TXT", "NOTICE.TXT"}:
                    (notices / (library.stem + "-" + name.rsplit("/", 1)[-1])).write_bytes(archive.read(name))
    with zipfile.ZipFile(cache / "sources/xpp3-1.1.4c-sources.jar") as archive:
        source = archive.read("org/xmlpull/mxp1/MXParser.java").decode()
        offset = source.index("/*\n * Indiana University Extreme! Lab Software License")
        (notices / "XPP3-LICENSE.txt").write_text(source[offset:])
        qname = archive.read("javax/xml/namespace/QName.java").decode()
        (notices / "XPP3-QName-LICENSE.txt").write_text(qname[:qname.index("package javax.xml.namespace;")])
    with zipfile.ZipFile(cache / "sources/jlayer-1.0.1-sources.jar") as archive:
        source = archive.read("javazoom/jl/converter/Converter.java").decode()
        (notices / "JLayer-NOTICE.txt").write_text(source[:source.index("package javazoom.jl.converter;")])
    shutil.copyfile(notices / "OpenAL-COPYING", notices / "LGPL-2.0.txt")
    # LWJGL's source archive carries notices for its bundled native components.
    with tarfile.open(cache / "sources/lwjgl-3.3.6.tar.gz") as archive:
        for member in archive.getmembers():
            leaf = Path(member.name).name.lower()
            included_module = any(f"/modules/lwjgl/{module}/" in member.name
                                  for module in ("core", "glfw", "openal", "opengl", "stb"))
            is_project_license = member.name == "lwjgl3-3.3.6/LICENSE.md"
            if member.isfile() and (included_module or is_project_license) and any(word in leaf for word in ("license", "copying", "notice")):
                target = notices / "lwjgl-source" / Path(*Path(member.name).parts[1:])
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(archive.extractfile(member).read())
    shutil.copyfile(cache / "fonts/OFL.txt", notices / "Noto-OFL.txt")
    (notices / "SOURCE-INPUTS.json").write_text(json.dumps(records, indent=2) + "\n")
    (notices / "THIRD-PARTY.md").write_text("""# Runtime components

This package runs the original converted game on a regular JVM. It does not
contain an Android operating system, emulator image, Google Play services, or an
Android SDK installation. Android API definition classes are the Apache-licensed
Maven android artifact; Linux adapter implementations precede them on classpath.

* Eclipse Temurin 21.0.12.1+1 JRE: GPL v2 with Classpath Exception where specified;
  all original notices remain under runtime/legal and runtime/NOTICE.
* LWJGL 3.3.6: BSD 3-Clause; GLFW: zlib license; OpenAL Soft: LGPL 2 or later.
* JLayer 1.0.1: LGPL 2 or later; unchanged, separately replaceable lib JAR.
* Bouncy Castle 1.80: MIT-style license; original text provided.
* Android API/Khronos definitions and Apache HTTP/Commons: Apache licenses;
  texts and original jar NOTICE files provided.
* XPP3: Indiana University Extreme! Lab license (implementation), public domain
  XmlPull API, Apache 1.1 QName. Original source notices provided.
* JSON-java: public domain; Noto Sans CJK: SIL Open Font License 1.1.

This product includes software developed by the Indiana University Extreme! Lab.
For further information please visit http://www.extreme.indiana.edu/

The companion paladog-linux-x86_64-sources.tar.gz supplies the corresponding
Temurin JDK/JRE sources, exact OpenAL source commit recorded inside the native
jar, JLayer sources, LWJGL sources, and this port's adapter/build source.
SOURCE-INPUTS.json records upstream locations and hashes. Publish the companion
source archive beside the binary archive when distributing this package.
Unmodified third-party libraries remain separate files and can be replaced;
the launcher performs no signature check that would prevent replacement.

The game's original code and assets retain their existing ownership. This port
does not relicense them under any of the third-party library licenses above.
""")
    print(f"Prepared runtime, notices, and corresponding sources in {cache}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache", type=Path, default=dependencies.cache_path())
    parser.add_argument("--offline", action="store_true")
    arguments = parser.parse_args()
    collect(arguments.cache.resolve(), arguments.offline)
