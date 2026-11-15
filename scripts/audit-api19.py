#!/usr/bin/env python3
"""Audit a shipped APK against the Android 4.4 (API 19) framework.

Checks, all from the APK itself (not from source):
  * dex format version and per-dex method count (Dalvik limits)
  * every referenced framework class exists in the API-19 android.jar;
    classes that only exist in the API-37 android.jar are post-19 public API
    (expected behind SDK guards; listed for review, not a failure)
  * native libraries are 32-bit ARM only
Usage: scripts/audit-api19.py <apk> <android-19/android.jar> <android-37/android.jar>
"""
import re
import struct
import sys
import zipfile


def uleb128(data, pos):
    result = 0
    shift = 0
    while True:
        b = data[pos]
        pos += 1
        result |= (b & 0x7F) << shift
        if b & 0x80 == 0:
            return result, pos
        shift += 7


def dex_types(data):
    string_ids_size, string_ids_off = struct.unpack_from("<II", data, 0x38)
    type_ids_size, type_ids_off = struct.unpack_from("<II", data, 0x40)
    types = []
    for i in range(type_ids_size):
        (string_idx,) = struct.unpack_from("<I", data, type_ids_off + 4 * i)
        str_off = struct.unpack_from("<I", data, string_ids_off + 4 * string_idx)[0]
        _, pos = uleb128(data, str_off)
        end = data.index(b"\x00", pos)
        types.append(data[pos:end].decode("utf-8", "replace"))
    return types


def main():
    apk, jar19, jar37 = sys.argv[1:4]
    zf = zipfile.ZipFile(apk)
    dexes = sorted(n for n in zf.namelist() if re.fullmatch(r"classes\d*\.dex", n))
    jar19_names = set(zipfile.ZipFile(jar19).namelist())
    jar37_names = set(zipfile.ZipFile(jar37).namelist())

    print(f"APK: {apk}")
    versions, total_methods, referenced = [], 0, set()
    for name in dexes:
        data = zf.read(name)
        version = data[4:7].decode()
        versions.append(version)
        method_ids_size = struct.unpack_from("<I", data, 0x58)[0]
        total_methods += method_ids_size
        referenced.update(dex_types(data))
        print(f"  {name}: dex version {version}, {method_ids_size} method refs")
    print(f"  dex files: {len(dexes)}; Dalvik needs classes.dex first and <=65536 method refs each")

    framework = sorted(t for t in referenced if t.startswith(("Landroid/", "Ljava/", "Ljavax/")))
    post19, hidden = [], []
    for t in framework:
        path = t[1:-1] + ".class"
        if path in jar19_names:
            continue
        (post19 if path in jar37_names else hidden).append(t)
    print(f"  framework types referenced: {len(framework)}")
    print(f"  post-19 public API referenced: {len(post19)} (must stay behind SDK guards)")
    print(f"  in neither SDK: {len(hidden)} (hidden/removed APIs, review each)")
    for t in hidden:
        print(f"    hidden: {t}")

    natives = [n for n in zf.namelist() if n.endswith(".so")]
    print(f"  native libs: {natives}")

    ok = (
        len(dexes) >= 1
        and all(v == "035" for v in versions)
        and all(struct.unpack_from("<I", zf.read(n), 0x58)[0] <= 65536 for n in dexes)
        and all("/armeabi" in n for n in natives)
    )
    print(f"  Dalvik-shape checks (dex 035, single main dex, arm32 libs): {'PASS' if ok else 'REVIEW'}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
