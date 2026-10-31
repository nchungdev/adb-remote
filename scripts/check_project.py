#!/usr/bin/env python3
"""Architecture & repository hygiene gate for adb-agent.

Verifies:
1. Required documentation & root governance files exist.
2. Every handwritten .kt / .py file is <= 350 lines.
3. Feature-First structure: each feature under features/<name>/ has domain, application, data, presentation.
4. Clean Architecture boundaries:
   - domain/ never imports android.*, androidx.*, okhttp3.*, or presentation/data packages.
   - application/ never imports android.* or androidx.compose.* or presentation/data packages.
5. MVI contracts exist in presentation/ (*UiState, *UiIntent).
"""

from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MAX_LINES = 350

REQUIRED_FILES = [
    "AGENTS.md",
    "LICENSE",
    "README.md",
    "CONTRIBUTING.md",
    "docs/README.md",
    "docs/product/PRODUCT.md",
    "docs/product/UX.md",
    "docs/engineering/ARCHITECTURE.md",
    "docs/engineering/PROJECT_STRUCTURE.md",
    "docs/engineering/CODING_RULES.md",
    "docs/engineering/CONVENTIONS.md",
    "docs/engineering/TESTING.md",
]

DOMAIN_FORBIDDEN_IMPORTS = (
    "import android.",
    "import androidx.",
    "import okhttp3.",
    "import java.net.Socket",
)

APPLICATION_FORBIDDEN_IMPORTS = (
    "import android.",
    "import androidx.compose.",
    "import okhttp3.",
)


def check_required_files(errors: list[str]) -> None:
    for rel in REQUIRED_FILES:
        if not (ROOT / rel).is_file():
            errors.append(f"Missing required governance/doc file: {rel}")


def iter_source_files() -> list[pathlib.Path]:
    results: list[pathlib.Path] = []
    for ext in ("*.kt", "*.py"):
        for path in ROOT.rglob(ext):
            parts = set(path.relative_to(ROOT).parts)
            if parts & {".gradle", "build", ".git", ".idea", ".kotlin"}:
                continue
            results.append(path)
    return sorted(results)


def check_line_limits_and_boundaries(errors: list[str]) -> None:
    for path in iter_source_files():
        rel = path.relative_to(ROOT)
        lines = path.read_text(encoding="utf-8").splitlines()
        if len(lines) > MAX_LINES:
            errors.append(f"File exceeds {MAX_LINES} lines ({len(lines)}): {rel}")

        if path.suffix != ".kt" or "src/main/" not in rel.as_posix():
            continue

        rel_posix = rel.as_posix()
        in_domain = "/domain/" in rel_posix
        in_application = "/application/" in rel_posix

        for idx, line in enumerate(lines, start=1):
            stripped = line.strip()
            if in_domain:
                if any(stripped.startswith(prefix) for prefix in DOMAIN_FORBIDDEN_IMPORTS):
                    errors.append(f"Domain layer forbidden import at {rel}:{idx} -> {stripped}")
                if re.search(r"import\s+app\.duinch\.adbagent\.features\.[a-z_]+\.(data|presentation)\b", stripped):
                    errors.append(f"Domain layer depends on outer layer at {rel}:{idx} -> {stripped}")
            if in_application:
                if any(stripped.startswith(prefix) for prefix in APPLICATION_FORBIDDEN_IMPORTS):
                    errors.append(f"Application layer forbidden import at {rel}:{idx} -> {stripped}")
                if re.search(r"import\s+app\.duinch\.adbagent\.features\.[a-z_]+\.(data|presentation)\b", stripped):
                    errors.append(f"Application layer depends on outer layer at {rel}:{idx} -> {stripped}")


def check_feature_slices(errors: list[str]) -> None:
    features_root = ROOT / "app/src/main/kotlin/app/duinch/adbagent/features"
    if not features_root.is_dir():
        errors.append("Missing features root directory: app/src/main/kotlin/app/duinch/adbagent/features")
        return

    feature_dirs = [d for d in sorted(features_root.iterdir()) if d.is_dir()]
    if not feature_dirs:
        errors.append("No feature slices found under features/")
        return

    for feat in feature_dirs:
        for layer in ("domain", "application", "data", "presentation"):
            layer_dir = feat / layer
            if not layer_dir.is_dir() or not list(layer_dir.glob("*.kt")):
                errors.append(f"Feature '{feat.name}' is missing non-empty '{layer}/' layer")

        pres_text = "\n".join(
            p.read_text(encoding="utf-8") for p in sorted((feat / "presentation").glob("*.kt"))
        ) if (feat / "presentation").is_dir() else ""
        if "UiState" not in pres_text or "UiIntent" not in pres_text:
            errors.append(f"Feature '{feat.name}' presentation layer must define MVI *UiState and *UiIntent")


def main() -> int:
    errors: list[str] = []
    check_required_files(errors)
    check_line_limits_and_boundaries(errors)
    check_feature_slices(errors)

    if errors:
        print("check_project.py FAILED:")
        for err in errors:
            print(f"  - {err}")
        return 1

    print("check_project.py PASSED: Clean Architecture + Feature-First + MVI boundaries & <=350 line limits verified.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
