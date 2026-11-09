#!/usr/bin/env python3
"""Trims the TypeScript and Pyright runtime bundles to what the daemons load.

Usage: scripts/trim-bundles.py [typescript|python ...]   (default: both)

Each bundle is an `npm install` of the compiler/type checker, zipped with a top-level
`node_modules/`. After upgrading one, rebuild its zip from a fresh install, e.g.

    npm install --prefix <dir> typescript@<version>      # or pyright@<version>
    (cd <dir> && zip -qr bundle.zip node_modules)
    cp <dir>/bundle.zip src/main/resources/<typescript|python>/<name>-bundle.zip

then run this script to trim the zip in place. The result is deterministic: entries sorted
by name, a fixed timestamp, deflate level 9, so re-running it on a trimmed zip is a no-op.

What each daemon loads (keep these):
  typescript/daemon.js  require("typescript") -> package.json main -> lib/typescript.js, and
                        every lib/lib.*.d.ts through ts.getDefaultLibFilePath.
  python/daemon.js      dist/pyright-internal.js, dist/vendor.js, dist/pyright.js (read for its
                        module table) and typeshed-fallback/stdlib. Pyright tolerates a
                        missing typeshed-fallback/stubs directory.
Everything else (CLIs, language/typings servers, localized messages, the full compiler used by
tsc, source maps, third-party typeshed stubs) is dropped.
"""
import os
import re
import sys
import zipfile

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
FIXED_TIME = (2020, 1, 1, 0, 0, 0)

TS = "node_modules/typescript/"
PY = "node_modules/pyright/"

BUNDLES = {
    "typescript": {
        "zip": "typescript/typescript-bundle.zip",
        "drop": [
            r"^node_modules/\.bin/",
            re.escape(TS) + r"bin/",
            re.escape(TS) + r"(README\.md|SECURITY\.md)$",
            re.escape(TS) + r"lib/_tsc\.js$",
            re.escape(TS) + r"lib/_tsserver\.js$",
            re.escape(TS) + r"lib/_typingsInstaller\.js$",
            re.escape(TS) + r"lib/(tsc|tsserver|typingsInstaller|watchGuard)\.js$",
            re.escape(TS) + r"lib/tsserverlibrary\..*",
            re.escape(TS) + r"lib/typescript\.d\.ts$",
            re.escape(TS) + r"lib/typesMap\.json$",
            re.escape(TS) + r"lib/[^/]+/diagnosticMessages\.generated\.json$",
        ],
    },
    "python": {
        "zip": "python/pyright-bundle.zip",
        "drop": [
            r"^node_modules/\.bin/",
            re.escape(PY) + r"README\.md$",
            re.escape(PY) + r"(index|langserver\.index)\.js$",
            re.escape(PY) + r"dist/pyright-langserver\.js$",
            re.escape(PY) + r".*\.js\.map$",
            re.escape(PY) + r"dist/typeshed-fallback/stubs/",
        ],
    },
}


def trim(name):
    spec = BUNDLES[name]
    path = os.path.normpath(os.path.join(ROOT, spec["zip"]))
    drop = [re.compile(p) for p in spec["drop"]]
    kept = []
    with zipfile.ZipFile(path) as src:
        for info in src.infolist():
            if info.is_dir() or any(p.search(info.filename) for p in drop):
                continue
            kept.append((info.filename, src.read(info), info.external_attr))
    kept.sort(key=lambda e: e[0])
    tmp = path + ".tmp"
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as out:
        for filename, data, attr in kept:
            zi = zipfile.ZipInfo(filename, FIXED_TIME)
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.external_attr = attr
            zi.create_system = 3
            out.writestr(zi, data, compresslevel=9)
    os.replace(tmp, path)
    print("%s: %d entries, %d bytes" % (spec["zip"], len(kept), os.path.getsize(path)))


if __name__ == "__main__":
    for bundle in sys.argv[1:] or list(BUNDLES):
        trim(bundle)
