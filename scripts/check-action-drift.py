#!/usr/bin/env python3
"""
Drift-Wächter für den Aktions-Katalog.

Der gleiche Befehl ist an drei Stellen definiert:

    1. Android   app/src/main/java/.../presentation/ui/common/{ActionType,ActionCatalog}.kt
    2. 3D-Konsole console3d/src/data/catalog.js
    3. Firmware  firmware/secureguard_esp32/secureguard_esp32.ino

Läuft eine Stelle weg, drückt eine Bedienerin in der Konsole auf einen Knopf,
den das Gerät nicht kennt – ohne dass irgendein Test rot wird. Dieses Skript
vergleicht die drei Quellen und bricht bei Abweichung ab.

Bewusst ohne Abhängigkeiten (nur Standardbibliothek), damit es in jeder
CI-Stufe vor dem eigentlichen Build laufen kann.

    python3 scripts/check-action-drift.py [--verbose]

Rückgabe: 0 = deckungsgleich, 1 = Drift gefunden.
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

KOTLIN_TYPE = ROOT / "app/src/main/java/com/secureguard/enterprise/presentation/ui/common/ActionType.kt"
KOTLIN_CATALOG = ROOT / "app/src/main/java/com/secureguard/enterprise/presentation/ui/common/ActionCatalog.kt"
WEB_CATALOG = ROOT / "console3d/src/data/catalog.js"
FIRMWARE = ROOT / "firmware/secureguard_esp32/secureguard_esp32.ino"

# Befehle, die nur das Gerät kennt (keine Bedienoberfläche dafür).
FIRMWARE_ONLY = {"CONFIG"}


def read(path: Path) -> str:
    if not path.exists():
        die(f"Quelle fehlt: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def die(msg: str) -> None:
    print(f"FEHLER: {msg}", file=sys.stderr)
    sys.exit(1)


# ----------------------------------------------------------------- Kotlin

def parse_kotlin_types() -> dict[str, str]:
    """ActionType-Enum → {Name: wireCommand}."""
    src = read(KOTLIN_TYPE)
    body = src[src.index("enum class ActionType"):]
    body = body[:body.index("}")]
    return dict(re.findall(r'(\w+)\("([^"]+)",\s*"[^"]*"\)', body))


def parse_kotlin_catalog() -> dict[str, dict]:
    """ActionCatalog.all → {Name: Eigenschaften}."""
    src = read(KOTLIN_CATALOG)
    # Erst ab `object ActionCatalog` auswerten – sonst greift das Muster auf
    # die Deklaration `data class ActionSpec(` und liest deren Feldnamen als
    # Eigenschaften der ersten Aktion.
    src = src[src.index("object ActionCatalog"):]
    specs: dict[str, dict] = {}
    for block in re.findall(r"ActionSpec\((.*?)\n        \)", src, re.S):
        name = re.search(r"type\s*=\s*ActionType\.(\w+)", block)
        if not name:
            continue
        title = re.search(r'title\s*=\s*"([^"]*)"', block)
        risk = re.search(r"risk\s*=\s*ActionRisk\.(\w+)", block)
        category = re.search(r"category\s*=\s*ActionCategory\.(\w+)", block)
        specs[name.group(1)] = {
            "title": title.group(1) if title else "",
            "category": category.group(1) if category else "",
            "risk": (risk.group(1) if risk else "SAFE").lower(),
            "requiresOnline": "requiresOnline = true" in block,
            # queueable ist im Kotlin-Datenmodell standardmäßig true
            "queueable": "queueable = false" not in block,
            "acceptsNote": "acceptsNote = true" in block,
            "confirm": "confirmTitle" in block,
        }
    return specs


# -------------------------------------------------------------------- Web

def parse_web_catalog() -> dict[str, dict]:
    src = read(WEB_CATALOG)
    start = src.index("export const ACTIONS")
    body = src[start:src.index("\n]", start)]
    specs: dict[str, dict] = {}
    for block in re.split(r"\n  \{", body)[1:]:
        ident = re.search(r"id:\s*'([^']+)'", block)
        if not ident:
            continue
        wire = re.search(r"wire:\s*'([^']+)'", block)
        title = re.search(r"title:\s*'([^']*)'", block)
        risk = re.search(r"risk:\s*'([^']+)'", block)
        category = re.search(r"category:\s*'([^']+)'", block)
        specs[ident.group(1)] = {
            "wire": wire.group(1) if wire else None,
            "title": title.group(1) if title else "",
            "category": category.group(1) if category else "",
            "risk": risk.group(1) if risk else "safe",
            "requiresOnline": "requiresOnline: true" in block,
            "queueable": "queueable: true" in block,
            "acceptsNote": "acceptsNote: true" in block,
            "confirm": "confirmTitle:" in block,
            "local": "local: true" in block,
        }
    return specs


# --------------------------------------------------------------- Firmware

def parse_firmware_commands() -> set[str]:
    """Alle Befehlsnamen, auf die die Firmware in handleCommand() reagiert."""
    src = read(FIRMWARE)
    return {
        m for m in re.findall(r'"([A-Z][A-Z_]{2,})"', src)
        if m not in {"OK", "ERROR", "ON", "OFF", "GET", "POST", "SET"}
    }


# ------------------------------------------------------------ Vergleiche

RISK_MAP = {"safe": "safe", "caution": "caution", "critical": "critical"}


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--verbose", "-v", action="store_true",
                    help="auch deckungsgleiche Aktionen auflisten")
    args = ap.parse_args()

    kt_types = parse_kotlin_types()
    kt_specs = parse_kotlin_catalog()
    web = parse_web_catalog()
    fw = parse_firmware_commands()

    if not kt_types:
        die("Konnte ActionType.kt nicht auswerten – Format geändert?")
    if not web:
        die("Konnte catalog.js nicht auswerten – Format geändert?")

    problems: list[str] = []

    # 1) ActionType ↔ ActionCatalog: jede Aktion muss beschrieben sein.
    for name in kt_types:
        if name not in kt_specs:
            problems.append(f"[Android] {name} fehlt in ActionCatalog.all")
    for name in kt_specs:
        if name not in kt_types:
            problems.append(f"[Android] ActionCatalog kennt {name}, ActionType nicht")

    # 2) Kotlin ↔ Web: Fernbefehle müssen beidseitig existieren.
    remote_web = {k: v for k, v in web.items() if not v["local"]}
    for name in kt_types:
        if name not in remote_web:
            problems.append(f"[Web] Aktion {name} fehlt in console3d/src/data/catalog.js")
    for name in remote_web:
        if name not in kt_types:
            problems.append(f"[Android] Web kennt {name}, ActionType nicht")

    # 3) Semantik je Aktion vergleichen.
    for name in sorted(set(kt_types) & set(remote_web)):
        k, w = kt_specs.get(name, {}), remote_web[name]
        wire = kt_types[name]
        if w["wire"] != wire:
            problems.append(f"[{name}] wire: Android '{wire}' ≠ Web '{w['wire']}'")
        if not k:
            continue
        if k["title"] != w["title"]:
            problems.append(f"[{name}] Titel: Android '{k['title']}' ≠ Web '{w['title']}'")
        if RISK_MAP.get(k["risk"]) != w["risk"]:
            problems.append(f"[{name}] Risiko: Android '{k['risk']}' ≠ Web '{w['risk']}'")
        for flag in ("requiresOnline", "queueable", "acceptsNote"):
            if k[flag] != w[flag]:
                problems.append(
                    f"[{name}] {flag}: Android {k[flag]} ≠ Web {w[flag]}")
        if k["confirm"] != w["confirm"]:
            problems.append(
                f"[{name}] Bestätigungsdialog: Android {k['confirm']} ≠ Web {w['confirm']}")
        # Kritische Aktionen ohne Rückfrage wären ein Sicherheitsmangel.
        if k["risk"] == "critical" and not (k["confirm"] and w["confirm"]):
            problems.append(f"[{name}] kritische Aktion ohne Bestätigungsdialog")

    # 4) Firmware: jeder Fernbefehl muss vom Gerät verstanden werden.
    for name, wire in sorted(kt_types.items()):
        if wire not in fw:
            problems.append(f"[Firmware] Befehl {wire} wird von der Firmware nicht behandelt")
    unused = fw - set(kt_types.values()) - FIRMWARE_ONLY
    for extra in sorted(unused):
        problems.append(f"[Firmware] {extra} wird behandelt, aber von keiner Oberfläche gesendet")

    # ------------------------------------------------------------- Bericht
    print(f"Aktionen  Android: {len(kt_types)}  Web gesamt: {len(web)} "
          f"(fern: {len(remote_web)}, lokal: {len(web) - len(remote_web)})  "
          f"Firmware: {len(fw)}")

    if args.verbose:
        for name in sorted(kt_types):
            w = remote_web.get(name, {})
            print(f"  ✓ {name:<10} wire={kt_types[name]:<10} "
                  f"risk={kt_specs.get(name, {}).get('risk', '?'):<8} "
                  f"web={'ja' if w else 'NEIN'} "
                  f"fw={'ja' if kt_types[name] in fw else 'NEIN'}")
        for name, spec in web.items():
            if spec["local"]:
                print(f"  • {name:<10} lokale Lagebild-Aktion (kein Funkbefehl)")

    if problems:
        print(f"\n{len(problems)} Abweichung(en) gefunden:", file=sys.stderr)
        for p in problems:
            print(f"  ✗ {p}", file=sys.stderr)
        return 1

    print("Kein Drift: Android, 3D-Konsole und Firmware sprechen denselben Befehlssatz.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
