#!/usr/bin/env python3
"""Download and package Termux aarch64 `adb` + transitive shared libraries into jniLibs.

Why jniLibs + ELF SONAME patching:
- Android 10+ (targetSdk 35) enforces W^X SELinux rules preventing execution from filesDir.
- Binaries placed in `app/src/main/jniLibs/arm64-v8a/lib*.so` with `useLegacyPackaging = true`
  are extracted by PackageManager to `nativeLibraryDir` with executable SELinux labels.
- PackageManager only extracts files matching `lib*.so`, so versioned SONAMEs (`libz.so.1`,
  `libzstd.so.1`) are patched in-place inside `.dynstr` to exact-same-length `.so` names
  (`libz_t.so`, `libzstd_t.so`) and `RUNPATH` is patched to `$ORIGIN`.
"""

from __future__ import annotations

import os
import pathlib
import shutil
import struct
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
JNI_ARM64 = ROOT / "app/src/main/jniLibs/arm64-v8a"
TERMUX_MIRROR = "https://packages-cf.termux.dev/apt/termux-main"

PACKAGES = [
    ("android-tools", "pool/main/a/android-tools/android-tools_37.0.0p1-3_aarch64.deb"),
    ("abseil-cpp", "pool/main/a/abseil-cpp/abseil-cpp_20260817.0_aarch64.deb"),
    ("brotli", "pool/main/b/brotli/brotli_1.2.1_aarch64.deb"),
    ("fmt", "pool/main/f/fmt/fmt_1:12.2.0_aarch64.deb"),
    ("libc++", "pool/main/libc/libc++/libc++_30_aarch64.deb"),
    ("liblz4", "pool/main/libl/liblz4/liblz4_1.10.0-1_aarch64.deb"),
    ("libprotobuf", "pool/main/libp/libprotobuf/libprotobuf_2:36.2-1_aarch64.deb"),
    ("zlib", "pool/main/z/zlib/zlib_1.3.2_aarch64.deb"),
    ("zstd", "pool/main/z/zstd/zstd_1.5.7-1_aarch64.deb"),
]

SYSTEM_LIBS = {"libc.so", "libm.so", "libdl.so", "liblog.so"}
SONAME_REMAP = {
    b"libz.so.1": b"libz_t.so",
    b"libzstd.so.1": b"libzstd_t.so",
}
OLD_RUNPATH = b"/data/data/com.termux/files/usr/lib"
NEW_RUNPATH = b"$ORIGIN" + b"\x00" * (len(OLD_RUNPATH) - 7)


def parse_elf64_dynamic_needed_and_patch(elf_bytes: bytearray) -> list[str]:
    if elf_bytes[:4] != b"\x7fELF" or elf_bytes[4] != 2 or elf_bytes[5] != 1:
        raise ValueError("Expected little-endian ELF64 binary")

    e_shoff = struct.unpack_from("<Q", elf_bytes, 40)[0]
    e_shentsize = struct.unpack_from("<H", elf_bytes, 58)[0]
    e_shnum = struct.unpack_from("<H", elf_bytes, 60)[0]

    sections = []
    for i in range(e_shnum):
        off = e_shoff + i * e_shentsize
        sh_type = struct.unpack_from("<I", elf_bytes, off + 4)[0]
        sh_offset = struct.unpack_from("<Q", elf_bytes, off + 24)[0]
        sh_size = struct.unpack_from("<Q", elf_bytes, off + 32)[0]
        sh_link = struct.unpack_from("<I", elf_bytes, off + 40)[0]
        sh_entsize = struct.unpack_from("<Q", elf_bytes, off + 56)[0]
        sections.append((sh_type, sh_offset, sh_size, sh_link, sh_entsize))

    needed: list[str] = []
    for sh_type, sh_offset, sh_size, sh_link, sh_entsize in sections:
        if sh_type != 6:  # SHT_DYNAMIC
            continue
        _, str_offset, str_size, _, _ = sections[sh_link]
        step = sh_entsize or 16
        for pos in range(sh_offset, sh_offset + sh_size, step):
            d_tag, d_val = struct.unpack_from("<qQ", elf_bytes, pos)
            if d_tag == 0:  # DT_NULL
                break
            if d_tag in (1, 14, 15, 29):  # NEEDED, SONAME, RPATH, RUNPATH
                abs_str = str_offset + d_val
                end = elf_bytes.index(0, abs_str, str_offset + str_size)
                raw = bytes(elf_bytes[abs_str:end])
                if d_tag == 1:
                    needed.append(SONAME_REMAP.get(raw, raw).decode("ascii"))
                if raw in SONAME_REMAP:
                    replacement = SONAME_REMAP[raw]
                    assert len(replacement) == len(raw)
                    elf_bytes[abs_str:end] = replacement
                elif d_tag in (15, 29) and raw == OLD_RUNPATH:
                    elf_bytes[abs_str:end] = NEW_RUNPATH
    return needed


def extract_deb(deb_path: pathlib.Path, extract_root: pathlib.Path) -> None:
    with tempfile.TemporaryDirectory() as tmp:
        tmp_dir = pathlib.Path(tmp)
        try:
            subprocess.run(["tar", "-xf", str(deb_path), "-C", str(tmp_dir)], check=True)
        except subprocess.CalledProcessError:
            subprocess.run(["ar", "x", str(deb_path)], cwd=str(tmp_dir), check=True)
        data_archives = list(tmp_dir.glob("data.tar*"))
        if not data_archives:
            raise RuntimeError(f"No data.tar* found in {deb_path}")
        subprocess.run(["tar", "-xf", str(data_archives[0]), "-C", str(extract_root)], check=True)


def main() -> None:
    if (JNI_ARM64 / "libadb.so").is_file() and len(list(JNI_ARM64.glob("lib*.so"))) >= 50:
        print(f"Termux ADB bundle already present in {JNI_ARM64} ({len(list(JNI_ARM64.glob('lib*.so')))} files).")
        return

    with tempfile.TemporaryDirectory() as work:
        work_dir = pathlib.Path(work)
        rootfs = work_dir / "rootfs"
        rootfs.mkdir()

        for name, rel_url in PACKAGES:
            deb_file = work_dir / f"{name}.deb"
            url = f"{TERMUX_MIRROR}/{rel_url}"
            print(f"Downloading {name}...")
            subprocess.run(["curl", "-fsSL", "-o", str(deb_file), url], check=True)
            extract_deb(deb_file, rootfs)

        usr_dir = rootfs / "data/data/com.termux/files/usr"
        adb_bin = usr_dir / "bin/adb"
        lib_dir = usr_dir / "lib"

        if JNI_ARM64.exists():
            shutil.rmtree(JNI_ARM64)
        JNI_ARM64.mkdir(parents=True, exist_ok=True)

        reverse_remap = {v.decode("ascii"): k.decode("ascii") for k, v in SONAME_REMAP.items()}
        queue: list[tuple[str, pathlib.Path]] = [("libadb.so", adb_bin)]
        packaged: set[str] = set()

        while queue:
            out_name, src_path = queue.pop(0)
            if out_name in packaged:
                continue
            real_src = src_path.resolve()
            data = bytearray(real_src.read_bytes())
            deps = parse_elf64_dynamic_needed_and_patch(data)

            dst = JNI_ARM64 / out_name
            dst.write_bytes(data)
            os.chmod(dst, 0o755)
            packaged.add(out_name)

            for dep in deps:
                if dep in SYSTEM_LIBS or dep in packaged:
                    continue
                orig_name = reverse_remap.get(dep, dep)
                candidate = lib_dir / orig_name
                if not candidate.exists():
                    raise FileNotFoundError(f"Missing required Termux shared library: {orig_name}")
                queue.append((dep, candidate))

        print(f"Packaged {len(packaged)} ARM64 binaries into {JNI_ARM64}.")


if __name__ == "__main__":
    main()
