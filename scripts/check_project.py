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
    }

    for path in RES.rglob("*.xml"):
        folder = path.parent.name
        if folder == "layout":
            resources["layout"].add(path.stem)
        elif folder == "drawable":
            resources["drawable"].add(path.stem)
        elif folder == "xml":
            resources["xml"].add(path.stem)
        elif folder.startswith("mipmap"):
            resources["mipmap"].add(path.stem)

    for values_file in (RES / "values").glob("*.xml"):
        tree = ET.parse(values_file)
        for child in tree.getroot():
            tag = child.tag
            name = child.get("name")
            if not name:
                continue
            if tag == "string":
                resources["string"].add(name)
            elif tag == "color":
                resources["color"].add(name)
            elif tag == "dimen":
                resources["dimen"].add(name)
            elif tag == "style":
                resources["style"].add(name)

    for layout in (RES / "layout").glob("*.xml"):
        tree = ET.parse(layout)
        for element in tree.iter():
            raw_id = element.get(f"{ANDROID_NS}id")
            if raw_id and raw_id.startswith("@+id/"):
                resources["id"].add(raw_id[len("@+id/"):])

    return resources


REFERENCE = re.compile(r"^@(?:\+)?(string|color|dimen|style|drawable|layout|xml|mipmap|id)/([A-Za-z0-9_.]+)$")


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
    for layout in (RES / "layout").glob("*.xml"):
        tree = ET.parse(layout)
        ids = set()
        for element in tree.iter():
            raw_id = element.get(f"{ANDROID_NS}id")
            if raw_id and raw_id.startswith("@+id/"):
                ids.add(raw_id[len("@+id/"):])
        binding_ids[kebab_to_camel(layout.stem) + "Binding"] = ids

    r_reference = re.compile(r"\bR\.(string|color|dimen|style|drawable|layout|xml|mipmap|id)\.([A-Za-z0-9_]+)")
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
                if prop not in known:
                    fail(
                        f"{relative}: view-binding property '{prop}' is not an id in "
                        f"{', '.join(sorted(used_bindings))}"
                    )


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
