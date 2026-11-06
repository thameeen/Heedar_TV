#!/usr/bin/env python3
"""Structural checks for this Android project.

AppJet cannot run the Android toolchain, so this script verifies the wiring that
a Gradle build would otherwise catch first: resource references, view-binding ids,
package/directory agreement and manifest class resolution.

Usage: python3 scripts/check_project.py
Exit code 0 = all checks passed.
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"
JAVA = ROOT / "app" / "src" / "main" / "java"
MANIFEST = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"

errors: list[str] = []


def fail(message: str) -> None:
    errors.append(message)


def kebab_to_camel(name: str) -> str:
    return "".join(part.capitalize() for part in name.split("_"))


VALUE_TAGS = {
    "string": "string",
    "color": "color",
    "dimen": "dimen",
    "style": "style",
    "string-array": "array",
    "integer-array": "array",
    "array": "array",
    "plurals": "plurals",
    "bool": "bool",
    "integer": "integer",
}

FOLDER_KINDS = {
    "drawable": "drawable",
    "color": "color",
    "xml": "xml",
    "mipmap": "mipmap",
    "font": "font",
    "menu": "menu",
    "raw": "raw",
    "anim": "anim",
}


def is_layout_folder(folder: str) -> bool:
    return folder == "layout" or folder.startswith("layout-")


def collect_resources() -> dict[str, set[str]]:
    resources: dict[str, set[str]] = {
        "string": set(),
        "color": set(),
        "dimen": set(),
        "style": set(),
        "drawable": set(),
        "layout": set(),
        "xml": set(),
        "mipmap": set(),
        "id": set(),
        "array": set(),
        "plurals": set(),
        "font": set(),
        "menu": set(),
        "raw": set(),
        "anim": set(),
        "bool": set(),
        "integer": set(),
    }

    for path in RES.rglob("*.xml"):
        folder = path.parent.name
        # Qualified variants (layout-sw600dp, drawable-v24, ...) are real resource
        # folders too. Matching only the bare names hid every TV layout from the
        # id/binding checks, so a broken sw600dp layout could only fail in CI.
        if is_layout_folder(folder):
            resources["layout"].add(path.stem)
            continue
        for prefix, kind in FOLDER_KINDS.items():
            if folder == prefix or folder.startswith(prefix + "-"):
                resources[kind].add(path.stem)
                break

    for values_dir in sorted(p for p in RES.glob("values*") if p.is_dir()):
        for values_file in values_dir.glob("*.xml"):
            tree = ET.parse(values_file)
            for child in tree.getroot():
                name = child.get("name")
                if not name:
                    continue
                kind = VALUE_TAGS.get(child.tag)
                if kind:
                    resources[kind].add(name)

    for layout in RES.rglob("*.xml"):
        if not is_layout_folder(layout.parent.name):
            continue
        tree = ET.parse(layout)
        for element in tree.iter():
            raw_id = element.get(f"{ANDROID_NS}id")
            if raw_id and raw_id.startswith("@+id/"):
                resources["id"].add(raw_id[len("@+id/"):])

    return resources


REFERENCE = re.compile(
    r"^@(?:\+)?(string|color|dimen|style|drawable|layout|xml|mipmap|id|array|plurals|"
    r"font|menu|raw|anim|bool|integer)/([A-Za-z0-9_.]+)$"
)


def check_xml_files(resources: dict[str, set[str]]) -> None:
    targets = list(RES.rglob("*.xml")) + [MANIFEST]
    for path in targets:
        try:
            tree = ET.parse(path)
        except ET.ParseError as exc:
            fail(f"{path.relative_to(ROOT)}: malformed XML ({exc})")
            continue

        for element in tree.iter():
            for attribute, value in element.attrib.items():
                if attribute.startswith("{"):
                    attribute = attribute.split("}", 1)[1]
                if not isinstance(value, str) or not value.startswith("@"):
                    continue
                if value.startswith("@android:") or value.startswith("@"):
                    match = REFERENCE.match(value)
                    if not match:
                        continue
                    kind, name = match.group(1), match.group(2)
                    if kind == "style" and name.startswith("Theme."):
                        pass
                    if name not in resources[kind]:
                        fail(
                            f"{path.relative_to(ROOT)}: <{element.tag} {attribute}=\"{value}\"> "
                            f"does not resolve to an existing {kind} resource"
                        )


def check_kotlin(resources: dict[str, set[str]]) -> None:
    binding_ids: dict[str, set[str]] = {}
    # Merge ids across configuration variants (layout/ and layout-sw600dp/ share
    # one generated binding class name). Checking only res/layout meant a property
    # present solely in a TV variant was reported missing, and a typo inside a
    # sw600dp layout was never inspected at all.
    for layout in RES.rglob("*.xml"):
        if not is_layout_folder(layout.parent.name):
            continue
        tree = ET.parse(layout)
        ids = set()
        for element in tree.iter():
            raw_id = element.get(f"{ANDROID_NS}id")
            if raw_id and raw_id.startswith("@+id/"):
                ids.add(raw_id[len("@+id/"):])
        binding_ids.setdefault(kebab_to_camel(layout.stem) + "Binding", set()).update(ids)

    r_reference = re.compile(
        r"\bR\.(string|color|dimen|style|drawable|layout|xml|mipmap|id|array|plurals|"
        r"font|menu|raw|anim|bool|integer)\.([A-Za-z0-9_]+)"
    )
    binding_usage = re.compile(r"\b(?:binding|itemBinding|viewBinding)\.([A-Za-z0-9_]+)")
    binding_class = re.compile(r"\b([A-Za-z0-9]+Binding)\b")

    for path in JAVA.rglob("*.kt"):
        source = path.read_text(encoding="utf-8")
        relative = path.relative_to(ROOT)

        # package must match the directory layout
        package_match = re.search(r"^package\s+([\w.]+)", source, re.MULTILINE)
        expected_package = ".".join(path.relative_to(JAVA).parent.parts)
        if not package_match:
            fail(f"{relative}: missing package declaration")
        elif package_match.group(1) != expected_package:
            fail(
                f"{relative}: package '{package_match.group(1)}' does not match "
                f"directory '{expected_package}'"
            )

        for match in r_reference.finditer(source):
            kind, name = match.group(1), match.group(2)
            if name == "x":
                continue
            if name not in resources[kind]:
                fail(f"{relative}: R.{kind}.{name} does not exist")

        # view binding property accesses must match ids in the inflated layout
        used_bindings = {
            name for name in binding_class.findall(source) if name in binding_ids
        }
        if used_bindings:
            known = set()
            for name in used_bindings:
                known |= binding_ids[name]
            for match in binding_usage.finditer(source):
                prop = match.group(1)
                # "root" is generated by ViewBinding for the root view, not an id.
                if prop == "root":
                    continue
                if prop not in known:
                    fail(
                        f"{relative}: view-binding property '{prop}' is not an id in "
                        f"{', '.join(sorted(used_bindings))}"
                    )


DECLARATION = re.compile(
    r"^[ \t]*"
    r"(?:@[A-Za-z0-9_.]+(?:\([^)]*\))?[ \t]*)*"
    r"(?:public |internal |private |protected |abstract |open |sealed |data |enum |"
    r"annotation |inline |value |const |override |suspend |operator |infix |"
    r"tailrec |external |lateinit |companion )*"
    r"(class|interface|object|fun|val|var|typealias)[ \t]+"
    # Extension declarations put a receiver type before the name
    # (`fun Context.gridSpanCount()`, `fun Movie.toCard()`). Without this the
    # declared name is missed, so every import of an extension function looks
    # unresolvable. Both parts stay non-capturing so group 2 remains the name.
    r"(?:<[^>\n]*>[ \t]*)?"
    r"(?:[A-Za-z_][A-Za-z0-9_.]*(?:<[^>\n]*>)?\??[ \t]*\.[ \t]*)?"
    r"([A-Za-z_][A-Za-z0-9_]*)",
    re.MULTILINE,
)

PROJECT_ROOT_PACKAGE = "com.heedar.tv"

# Manifest components declared by libraries rather than this repository.
LIBRARY_CLASS_PREFIXES = (
    "android.",
    "androidx.",
    "com.google.android.",
    "kotlin.",
    "java.",
    "javax.",
)

# Generated by the Android Gradle Plugin — no source file exists for these.
GENERATED_IMPORT_PREFIXES = (
    f"{PROJECT_ROOT_PACKAGE}.R.",
    f"{PROJECT_ROOT_PACKAGE}.BuildConfig",
    f"{PROJECT_ROOT_PACKAGE}.databinding.",
)


def is_generated_import(target: str) -> bool:
    return target == f"{PROJECT_ROOT_PACKAGE}.R" or any(
        target.startswith(prefix) for prefix in GENERATED_IMPORT_PREFIXES
    )


def collect_declarations() -> tuple[dict[str, dict[str, Path]], dict[Path, set[str]]]:
    """Map package -> declared top-level name -> file, plus per-file names (for nesting)."""
    by_package: dict[str, dict[str, Path]] = {}
    by_file: dict[Path, set[str]] = {}

    for path in JAVA.rglob("*.kt"):
        source = path.read_text(encoding="utf-8")
        package_match = re.search(r"^package\s+([\w.]+)", source, re.MULTILINE)
        if not package_match:
            continue
        package = package_match.group(1)
        names = {match.group(2) for match in DECLARATION.finditer(source)}
        by_file[path] = names
        bucket = by_package.setdefault(package, {})
        for name in names:
            bucket.setdefault(name, path)

    return by_package, by_file


def check_imports() -> int:
    """Every com.heedar.tv import must resolve to a real declaration in that package."""
    by_package, by_file = collect_declarations()
    packages = sorted(by_package, key=len, reverse=True)
    import_pattern = re.compile(
        r"^[ \t]*import[ \t]+(" + re.escape(PROJECT_ROOT_PACKAGE) + r"\.[\w.]+)", re.MULTILINE
    )
    resolved = 0

    for path in JAVA.rglob("*.kt"):
        source = path.read_text(encoding="utf-8")
        relative = path.relative_to(ROOT)

        for match in import_pattern.finditer(source):
            target = match.group(1)
            if is_generated_import(target):
                resolved += 1
                continue
            package = next(
                (candidate for candidate in packages if target == candidate or target.startswith(candidate + ".")),
                None,
            )
            if package is None:
                fail(f"{relative}: import '{target}' is not in a known package")
                continue

            remainder = target[len(package):].lstrip(".")
            if not remainder:
                continue

            segments = remainder.split(".")
            declaring_file = by_package[package].get(segments[0])
            if declaring_file is None:
                fail(
                    f"{relative}: import '{target}' does not resolve — "
                    f"'{segments[0]}' is not declared in package '{package}'"
                )
                continue

            # Nested declarations (e.g. XtreamResult.Success) must exist in the same file.
            nested = by_file.get(declaring_file, set())
            missing = [segment for segment in segments[1:] if segment not in nested]
            if missing:
                fail(
                    f"{relative}: import '{target}' does not resolve — "
                    f"'{missing[0]}' is not nested in {declaring_file.name}"
                )
                continue

            resolved += 1

    return resolved


def check_manifest_classes() -> None:
    tree = ET.parse(MANIFEST)
    application = tree.getroot().find("application")
    if application is None:
        fail("AndroidManifest.xml: missing <application>")
        return

    for element in application:
        tag = element.tag.split("}")[-1]
        if tag not in {"activity", "service", "receiver", "provider"}:
            continue
        class_name = element.get(f"{ANDROID_NS}name")
        if not class_name or class_name.startswith("."):
            class_name = "com.heedar.tv" + (class_name or "")
        # Components contributed by libraries (e.g. appcompat's
        # AppLocalesMetadataHolderService, which backs per-app locales) have no
        # file in this repository, so only project-owned classes are checked.
        if class_name.startswith(LIBRARY_CLASS_PREFIXES):
            continue
        relative = Path(class_name.replace(".", "/") + ".kt")
        if not (JAVA / relative).exists():
            fail(f"AndroidManifest.xml: {class_name} has no source file at {relative}")


def check_translations() -> None:
    default = RES / "values" / "strings.xml"
    if not default.exists():
        fail("missing default strings.xml")
        return
    default_keys = {
        element.get("name") for element in ET.parse(default).getroot() if element.tag == "string"
    }
    for localized in RES.glob("values-*/strings.xml"):
        keys = {
            element.get("name")
            for element in ET.parse(localized).getroot()
            if element.tag == "string"
        }
        missing = default_keys - keys
        extra = keys - default_keys
        if missing:
            fail(f"{localized.relative_to(ROOT)}: missing keys {sorted(missing)}")
        if extra:
            fail(f"{localized.relative_to(ROOT)}: unknown keys {sorted(extra)}")


def main() -> int:
    resources = collect_resources()
    check_xml_files(resources)
    check_kotlin(resources)
    imports = check_imports()
    check_manifest_classes()
    check_translations()

    if errors:
        print(f"FAILED with {len(errors)} problem(s):")
        for error in errors:
            print(f"  - {error}")
        return 1

    print(
        "OK: {strings} strings, {layouts} layouts, {drawables} drawables, "
        "{ids} ids verified".format(
            strings=len(resources["string"]),
            layouts=len(resources["layout"]),
            drawables=len(resources["drawable"]),
            ids=len(resources["id"]),
        )
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
