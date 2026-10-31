#!/usr/bin/env python3
"""Publish built APK to GitHub Releases (`nchungdev/adb-agent`)."""

from __future__ import annotations

import hashlib
import json
import pathlib
import shutil
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DEFAULT_LOCAL_DEST = pathlib.Path.home() / ".local/share/adb-device-manager/downloads"


def read_version_props() -> tuple[int, str]:
    props: dict[str, str] = {}
    prop_path = ROOT / "app/version.properties" if (ROOT / "app/version.properties").is_file() else ROOT / "version.properties"
    for line in prop_path.read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.strip().startswith("#"):
            k, v = line.split("=", 1)
            props[k.strip()] = v.strip()
    code = int(props.get("versionCode", "1"))
    major = props.get("versionMajor", "0")
    minor = props.get("versionMinor", "1")
    patch = props.get("versionPatch", "0")
    cls = props.get("versionClassifier", "")
    name = f"{major}.{minor}.{patch}-{cls}" if cls else f"{major}.{minor}.{patch}"
    return code, name


def sha256_file(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def find_apk_src() -> pathlib.Path | None:
    candidates = [
        ROOT / "app/build/outputs/apk/debug/adb-agent-debug.apk",
        ROOT / "app/build/outputs/apk/debug/app-debug.apk",
        ROOT / "build/outputs/apk/debug/adb-agent-debug.apk",
        ROOT / "build/outputs/apk/debug/app-debug.apk",
    ]
    for c in candidates:
        if c.is_file():
            return c
    return None


def main() -> int:
    changelog = sys.argv[1] if len(sys.argv) > 1 else "Cập nhật tính năng & tối ưu kết nối."
    apk_src = find_apk_src()
    if not apk_src:
        print("APK not found. Run ./gradlew assembleDebug first.", file=sys.stderr)
        return 1

    version_code, version_name = read_version_props()
    tag = f"v{version_name}"
    title = f"ADB Agent {tag}"
    sha = sha256_file(apk_src)
    release_notes = f"{changelog}\n\nSHA-256: `{sha}`\n(versionCode: {version_code})"

    cmd = [
        "gh", "release", "create", tag,
        str(apk_src),
        "--title", title,
        "--notes", release_notes,
    ]
    print(f"Publishing GitHub Release {tag}...")
    res = subprocess.run(cmd)
    if res.returncode != 0:
        print(f"Note: Release {tag} may already exist, uploading asset with clobber...")
        subprocess.run(["gh", "release", "upload", tag, str(apk_src), "--clobber"])

    try:
        DEFAULT_LOCAL_DEST.mkdir(parents=True, exist_ok=True)
        apk_dst = DEFAULT_LOCAL_DEST / "adb-agent.apk"
        shutil.copy2(apk_src, apk_dst)
        manifest = {
            "versionCode": version_code,
            "versionName": version_name,
            "downloadUrl": f"https://github.com/nchungdev/adb-agent/releases/download/{tag}/{apk_src.name}",
            "sha256": sha,
            "changelog": changelog,
        }
        (DEFAULT_LOCAL_DEST / "version.json").write_text(
            json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
    except Exception as e:
        print(f"Warning: Could not sync local cache: {e}", file=sys.stderr)

    print(f"Published Release {tag} (code={version_code}) to GitHub Releases!")
    return 0


if __name__ == "__main__":
    sys.exit(main())
