#!/usr/bin/env python3
"""Dependency vulnerability review for this Gradle project.

Why this exists
---------------
The shared security preset resolves *lockfiles*. A Gradle project has no
``gradle.lockfile`` until a build runs, and the Android SDK is not available
here, so that scanner reports "incomplete / 0 packages".

This script closes most of that gap without Gradle: it resolves the dependency
graph from the *published POM metadata* of the coordinates pinned in
``gradle/libs.versions.toml``, then asks OSV about every resolved coordinate.

What it does
------------
1. Reads direct dependencies (and build plugins) from the version catalog.
2. Walks each POM's ``<dependencies>`` transitively, honouring
   ``dependencyManagement`` (including ``scope=import`` BOMs), POM properties and
   parent inheritance; skips ``test``/``provided``/``system`` scopes and
   ``optional`` edges.
3. Applies Gradle-like "highest version wins" per module and re-expands to a
   fixpoint, so the higher version's own dependencies are included.
4. Queries https://api.osv.dev/v1/querybatch for every resolved coordinate.

Known approximations (reported, not hidden)
-------------------------------------------
* Gradle's full conflict resolution, AGP variant/attribute selection and
  platform (BOM) alignment are approximated by highest-version-wins.
* AndroidX "constraint" style POMs may omit versions that only AGP supplies;
  such modules are reported as unresolved rather than dropped silently.
* This is evidence about published advisories for resolved coordinates. It is
  not a proof that the app is free of vulnerabilities.

Usage
-----
    python3 scripts/check_dependencies.py [--verbose]

Exit codes: 0 = no advisories, 1 = advisories found, 2 = resolution incomplete.
"""

from __future__ import annotations

import json
import re
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CATALOG = ROOT / "gradle" / "libs.versions.toml"
OSV_BATCH = "https://api.osv.dev/v1/querybatch"
OSV_VULN = "https://api.osv.dev/v1/vulns/{}"

CENTRAL = "https://repo1.maven.org/maven2"
GOOGLE = "https://dl.google.com/dl/android/maven2"

POM_NS = "{http://maven.apache.org/POM/4.0.0}"
MAX_DEPTH = 8
MAX_ITERATIONS = 4
SKIP_SCOPES = {"test", "provided", "system"}
SEVERITY_RANK = {"CRITICAL": 4, "HIGH": 3, "MODERATE": 2, "MEDIUM": 2, "LOW": 1}


def repo_order(group: str) -> list[str]:
    if group.startswith(("androidx.", "com.android.", "com.google.android.")):
        return [GOOGLE, CENTRAL]
    return [CENTRAL, GOOGLE]


def artifact_path(group: str, artifact: str, version: str) -> str:
    return f"{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.pom"


def fetch(url: str, timeout: int = 30) -> bytes | None:
    try:
        with urllib.request.urlopen(url, timeout=timeout) as resp:
            return resp.read()
    except urllib.error.HTTPError:
        return None
    except (urllib.error.URLError, TimeoutError):
        return None


class Pom:
    """A parsed POM with lazily-resolved properties."""

    def __init__(self, group: str, artifact: str, version: str, xml: bytes):
        self.group = group
        self.artifact = artifact
        self.version = version
        root = ET.fromstring(xml)
        self._root = root
        self.parent = None
        parent_el = root.find(f"{POM_NS}parent")
        if parent_el is not None:
            self.parent = (
                text(parent_el, "groupId"),
                text(parent_el, "artifactId"),
                text(parent_el, "version"),
            )
        self.properties = {}
        props_el = root.find(f"{POM_NS}properties")
        if props_el is not None:
            for prop in props_el:
                key = prop.tag.split("}")[-1]
                if prop.text:
                    self.properties[key] = prop.text.strip()

    def resolved_properties(self, parent_props: dict[str, str]) -> dict[str, str]:
        merged = dict(parent_props)
        merged.update(self.properties)
        merged.setdefault("project.version", self.version)
        merged.setdefault("project.groupId", self.group)
        merged.setdefault("project.artifactId", self.artifact)
        merged.setdefault("version", self.version)
        merged.setdefault("pom.version", self.version)
        return merged


def text(el, tag: str) -> str | None:
    found = el.find(f"{POM_NS}{tag}")
    if found is None or found.text is None:
        return None
    return found.text.strip()


def interpolate(value: str, props: dict[str, str]) -> str | None:
    """Expand ${...} using known properties; return None if any is unknown."""
    if value is None:
        return None
    unresolved = False

    def repl(match: re.Match[str]) -> str:
        nonlocal unresolved
        key = match.group(1)
        if key in props:
            return props[key]
        unresolved = True
        return match.group(0)

    result = re.sub(r"\$\{([^}]+)\}", repl, value)
    if unresolved or "${" in result:
        return None
    return result


class Resolver:
    def __init__(self) -> None:
        self._cache: dict[tuple[str, str, str], Pom | None] = {}
        self.unresolved: dict[str, str] = {}
        self.managed: dict[tuple[str, str], str] = {}

    def load(self, group: str, artifact: str, version: str) -> Pom | None:
        key = (group, artifact, version)
        if key in self._cache:
            return self._cache[key]
        xml = None
        for repo in repo_order(group):
            xml = fetch(f"{repo}/{artifact_path(group, artifact, version)}")
            if xml:
                break
        if not xml:
            self._cache[key] = None
            self.unresolved[f"{group}:{artifact}:{version}"] = "POM not found"
            return None
        try:
            pom = Pom(group, artifact, version, xml)
        except ET.ParseError:
            self._cache[key] = None
            self.unresolved[f"{group}:{artifact}:{version}"] = "unparsable POM"
            return None
        self._cache[key] = pom
        return pom

    def props_for(self, pom: Pom, depth: int = 0) -> dict[str, str]:
        parent_props: dict[str, str] = {}
        if pom.parent and depth < 3:
            pg, pa, pv = pom.parent
            parent = self.load(pg, pa, pv)
            if parent is not None:
                parent_props = self.props_for(parent, depth + 1)
        return pom.resolved_properties(parent_props)

    def collect_managed(self, pom: Pom, props: dict[str, str], depth: int = 0) -> None:
        """Record dependencyManagement versions, expanding imported BOMs."""
        dm = pom._root.find(f"{POM_NS}dependencyManagement")
        if dm is None:
            return
        for dep in dm.findall(f"{POM_NS}dependencies/{POM_NS}dependency"):
            group = interpolate(text(dep, "groupId") or "", props)
            artifact = interpolate(text(dep, "artifactId") or "", props)
            version = interpolate(text(dep, "version") or "", props)
            scope = text(dep, "scope")
            dep_type = text(dep, "type")
            if not group or not artifact:
                continue
            if scope == "import" and dep_type == "pom" and version and depth < 3:
                bom = self.load(group, artifact, version)
                if bom is not None:
                    self.collect_managed(bom, self.props_for(bom), depth + 1)
                continue
            if version:
                self.managed.setdefault((group, artifact), version)

    def collect_managed_full(self, pom: Pom, depth: int = 0) -> None:
        """Collect managed versions from this POM and its parent chain."""
        self.collect_managed(pom, self.props_for(pom), depth)
        if pom.parent and depth < 3:
            parent = self.load(*pom.parent)
            if parent is not None:
                self.collect_managed_full(parent, depth + 1)

    def dependencies(self, pom: Pom, props: dict[str, str]) -> list[tuple[str, str, str]]:
        result = []
        for dep in pom._root.findall(f"{POM_NS}dependencies/{POM_NS}dependency"):
            group = interpolate(text(dep, "groupId") or "", props)
            artifact = interpolate(text(dep, "artifactId") or "", props)
            version = interpolate(text(dep, "version") or "", props)
            scope = text(dep, "scope") or "compile"
            optional = (text(dep, "optional") or "false").lower() == "true"
            dep_type = text(dep, "type") or "jar"
            if not group or not artifact:
                continue
            if scope in SKIP_SCOPES or optional:
                continue
            if dep_type not in ("jar", "aar"):
                continue
            if version is None:
                version = self.managed.get((group, artifact))
            if version is None:
                self.unresolved[f"{group}:{artifact}:?"] = "no version in POM or BOM"
                continue
            concrete = normalize_version(version)
            if concrete is None:
                self.unresolved[f"{group}:{artifact}:{version}"] = (
                    "version range, not a concrete version"
                )
                continue
            result.append((group, artifact, concrete))
        return result


def normalize_version(version: str | None) -> str | None:
    """Map a POM version spec to a concrete version.

    Gradle/Maven exact pins look like ``[1.2.3]`` and mean exactly 1.2.3.
    Genuine ranges (``[1.0,2.0)``) cannot be resolved without metadata, so they
    are reported rather than guessed.
    """
    if version is None:
        return None
    value = version.strip()
    if value.startswith("[") and value.endswith("]"):
        inner = value[1:-1]
        if "," not in inner:
            return inner.strip()
        return None
    if "," in value or value.startswith("(") or value.endswith(")"):
        return None
    return value or None


def version_key(version: str) -> tuple:
    """Sort key approximating Maven version ordering."""
    parts = re.split(r"[.\-_]", version)
    key = []
    for part in parts:
        if part.isdigit():
            key.append((1, int(part), ""))
        else:
            key.append((0, 0, part))
    return tuple(key)


def resolve(
    direct: list[tuple[str, str, str]]
) -> tuple[dict[str, str], dict[str, str], Resolver]:
    """Resolve the graph, tracking which direct dependency introduced each module."""
    resolver = Resolver()
    chosen: dict[tuple[str, str], str] = {}
    origins: dict[str, str] = {}

    for group, artifact, version in direct:
        pom = resolver.load(group, artifact, version)
        if pom is not None:
            resolver.collect_managed_full(pom)

    frontier = list(direct)
    for _ in range(MAX_ITERATIONS):
        seen: set[tuple[str, str, str]] = set()
        queue = [(g, a, v, 0, f"{g}:{a}") for g, a, v in frontier]
        found: dict[tuple[str, str], str] = {}
        found_origins: dict[tuple[str, str], str] = {}
        while queue:
            group, artifact, version, depth, root = queue.pop(0)
            if depth > MAX_DEPTH:
                continue
            if (group, artifact, version) in seen:
                continue
            seen.add((group, artifact, version))
            pom = resolver.load(group, artifact, version)
            if pom is None:
                continue
            resolver.collect_managed_full(pom)
            props = resolver.props_for(pom)
            for cg, ca, cv in resolver.dependencies(pom, props):
                current = found.get((cg, ca))
                if current is None or version_key(cv) > version_key(current):
                    found[(cg, ca)] = cv
                    found_origins[(cg, ca)] = root
                queue.append((cg, ca, cv, depth + 1, root))

        merged = dict(chosen)
        for module, version in found.items():
            current = merged.get(module)
            if current is None or version_key(version) > version_key(current):
                merged[module] = version
                origins[f"{module[0]}:{module[1]}"] = found_origins.get(module, "")

        next_frontier = [(g, a, v) for (g, a), v in merged.items()]
        if set(next_frontier) == set(frontier):
            chosen = merged
            break
        chosen = merged
        frontier = next_frontier

    resolved = {f"{g}:{a}": v for (g, a), v in chosen.items()}
    return resolved, origins, resolver


def parse_catalog(path: Path) -> list[tuple[str, str, str]]:
    versions: dict[str, str] = {}
    deps: list[tuple[str, str, str]] = []
    section = None
    for raw in path.read_text().splitlines():
        line = raw.split("#", 1)[0].strip()
        if not line:
            continue
        if line.startswith("[") and line.endswith("]"):
            section = line[1:-1]
            continue
        if "=" not in line:
            continue
        key, value = (part.strip() for part in line.split("=", 1))
        if section == "versions":
            versions[key] = value.strip('"')
        elif section in ("libraries", "plugins"):
            module = re.search(r'module\s*=\s*"([^"]+)"', value)
            if not module:
                continue
            version_ref = re.search(r'version\.ref\s*=\s*"([^"]+)"', value)
            version = versions.get(version_ref.group(1)) if version_ref else None
            if version is None:
                version = (re.search(r'version\s*=\s*"([^"]+)"', value) or [None, None])[1]
            if version is None:
                continue
            group, artifact = module.group(1).split(":")
            deps.append((group, artifact, version))
    return deps


def scan_osv(coordinates: dict[str, str]) -> dict[str, list[dict]]:
    items = sorted(coordinates.items())
    findings: dict[str, list[dict]] = {}
    batch_size = 100
    for start in range(0, len(items), batch_size):
        chunk = items[start:start + batch_size]
        queries = [
            {"package": {"ecosystem": "Maven", "name": name}, "version": version}
            for name, version in chunk
        ]
        req = urllib.request.Request(
            OSV_BATCH,
            data=json.dumps({"queries": queries}).encode(),
            headers={"Content-Type": "application/json"},
        )
        with urllib.request.urlopen(req, timeout=90) as resp:
            results = json.load(resp)["results"]
        for (name, version), result in zip(chunk, results):
            vulns = result.get("vulns") or []
            if vulns:
                findings[f"{name}:{version}"] = vulns
    return findings


def severity_of(vuln: dict) -> str:
    db = vuln.get("database_specific") or {}
    if db.get("severity"):
        return str(db["severity"]).upper()
    for sev in vuln.get("severity") or []:
        if sev.get("type") == "CVSS_V3":
            return "CVSS:" + sev.get("score", "")
    return "UNKNOWN"


def main() -> int:
    verbose = "--verbose" in sys.argv
    direct = parse_catalog(CATALOG)
    print(f"Direct coordinates from {CATALOG.relative_to(ROOT)}: {len(direct)}")

    coordinates, origins, resolver = resolve(direct)
    print(f"Resolved modules (direct + transitive): {len(coordinates)}")

    if resolver.unresolved:
        print(f"\nUnresolved during resolution: {len(resolver.unresolved)}")
        for name, reason in sorted(resolver.unresolved.items()):
            print(f"  - {name} ({reason})")

    print("\nQuerying OSV...")
    findings = scan_osv(coordinates)

    if verbose:
        print("\nResolved coordinates:")
        for name, version in sorted(coordinates.items()):
            print(f"  {name}:{version}")

    if not findings:
        print(f"\nNo advisories for {len(coordinates)} resolved Maven coordinates.")
        return 0 if not resolver.unresolved else 2

    print(f"\nAdvisories found in {len(findings)} module(s):\n")
    worst = 0
    for coordinate, vulns in sorted(findings.items()):
        print(f"  {coordinate}")
        for vuln in vulns:
            severity = severity_of(vuln)
            rank = SEVERITY_RANK.get(severity.split(":")[0], 0)
            worst = max(worst, rank)
            aliases = ", ".join(vuln.get("aliases") or [])
            print(f"      - {vuln.get('id')} [{severity}] {vuln.get('summary', '')}")
            if aliases:
                print(f"        aliases: {aliases}")
    print()
    return 1


if __name__ == "__main__":
    sys.exit(main())
