#!/usr/bin/env python3
"""Builds app/src/main/assets/platforms.csv: the platforms at each station, from OpenStreetMap.

The Live Departure Board API can't list a station's platforms, so the app starts from this list and adds any
others it sees on live boards. Data (c) OpenStreetMap contributors, ODbL 1.0.

    python3 tools/platforms/build_platforms.py            # query Overpass and write the CSV
    python3 tools/platforms/build_platforms.py osm.json   # use a saved Overpass response instead

A station's platforms come from its OSM stop_area relations (a station can have more than one). Stations with no
stop_area fall back to platforms within 300 m whose nearest CRS-coded station is this one. Underground, tram and
light rail platforms are left out. No other dependencies than Python 3.
"""
import csv
import json
import math
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
QUERY = Path(__file__).with_name("platforms.ql")
STATIONS = ROOT / "app/src/main/assets/stations.csv"
OUT = ROOT / "app/src/main/assets/platforms.csv"
SERVERS = ["https://overpass-api.de/api/interpreter", "https://overpass.private.coffee/api/interpreter"]
USER_AGENT = "Dotboard-platform-list/1.0 (+https://github.com/InputBiscuit/dotboard)"

PLATFORM = re.compile(r"^(\d{1,2}[A-Z]?|[A-Z])$")
NOT_MAINLINE_NETWORK = re.compile(r"underground|dlr|docklands|metrolink|tram|metro|subway", re.I)
TUBE_NAME = re.compile(r"\b(north|south|east|west)bound\b|\bline\b", re.I)
FALLBACK_METRES = 300
MAX_PLATFORMS = 40  # anything above this is a mapping mistake


def overpass() -> dict:
    data = urllib.parse.urlencode({"data": QUERY.read_text()}).encode()
    wait = 10
    for attempt in range(6):
        for server in SERVERS:
            try:
                req = urllib.request.Request(server, data=data, headers={"User-Agent": USER_AGENT})
                with urllib.request.urlopen(req, timeout=900) as r:
                    return json.load(r)
            except Exception as e:  # busy servers answer 429/502/504 or plain text
                print(f"attempt {attempt + 1} at {server} failed: {e}", file=sys.stderr)
        time.sleep(wait)
        wait *= 2
    sys.exit("Overpass didn't answer; try again later")


def platform_refs(tags: dict) -> set[str]:
    """Platform names from an OSM platform's ref ("1", "10A", "3;4", "Platform B"), or none if not mainline."""
    if any(tags.get(k) == "yes" for k in ("subway", "tram", "light_rail")):
        return set()
    if NOT_MAINLINE_NETWORK.search(tags.get("network", "")) or TUBE_NAME.search(tags.get("name", "")):
        return set()
    if "disused" in tags or tags.get("railway") == "disused" or tags.get("disused:railway"):
        return set()
    ref = tags.get("ref") or tags.get("local_ref") or ""
    refs = set()
    for part in re.split(r"[;,/&]| and ", ref):
        part = re.sub(r"^platforms?\s*", "", part.strip(), flags=re.I).upper()
        pieces = part.split("-") if "-" in part else [part]  # "1-2" is often an island platform
        if all(PLATFORM.match(p.strip()) for p in pieces):
            refs.update(p.strip() for p in pieces)
    return refs


def order(p: str):
    m = re.match(r"^(\d+)(.*)$", p)
    return (0, int(m.group(1)), m.group(2)) if m else (1, 0, p)


def distance(a, b) -> float:
    (lat1, lon1), (lat2, lon2) = a, b
    x = math.radians(lon2 - lon1) * math.cos(math.radians((lat1 + lat2) / 2))
    y = math.radians(lat2 - lat1)
    return 6_371_000 * math.hypot(x, y)


def where(e):
    if "center" in e:
        return e["center"]["lat"], e["center"]["lon"]
    if "lat" in e:
        return e["lat"], e["lon"]
    return None


def build(osm: dict) -> dict[str, list[str]]:
    with STATIONS.open() as f:
        known = {row["crs"] for row in csv.DictReader(f)}
    els = osm["elements"]
    by_id = {(e["type"], e["id"]): e for e in els}

    stations = {}  # (type, id) -> crs
    station_points = []  # (crs, (lat, lon))
    for e in els:
        t = e.get("tags", {})
        crs = t.get("ref:crs", "").strip().upper()
        if t.get("railway") in ("station", "halt") and crs in known:
            stations[(e["type"], e["id"])] = crs
            if where(e):
                station_points.append((crs, where(e)))

    result: dict[str, set[str]] = {}
    for sa in els:
        if sa["type"] != "relation" or sa.get("tags", {}).get("public_transport") != "stop_area":
            continue
        members = [(m["type"], m["ref"]) for m in sa.get("members", [])]
        codes = {stations[k] for k in members if k in stations}
        if len(codes) != 1:  # no station, or several stations sharing one area: can't tell whose platforms
            continue
        plats = set()
        for k in members:
            e = by_id.get(k)
            if e and k not in stations:
                plats |= platform_refs(e.get("tags", {}))
        if plats:
            result.setdefault(codes.pop(), set()).update(plats)

    # Stations with no stop_area: nearby platforms closest to this station rather than another.
    fallback: dict[str, set[str]] = {}
    for e in els:
        t = e.get("tags", {})
        if t.get("railway") not in ("platform", "platform_edge") or not where(e):
            continue
        refs = platform_refs(t)
        if not refs or not station_points:
            continue
        crs, d = min(((c, distance(where(e), p)) for c, p in station_points), key=lambda x: x[1])
        if d <= FALLBACK_METRES:
            fallback.setdefault(crs, set()).update(refs)
    for crs, plats in fallback.items():
        result.setdefault(crs, plats)

    # OSM often maps parts of a platform ("11A", "11B", "6N"); live boards may just say "11". Keep the number only:
    # in the app, choosing "11" also matches "11A" and "11B".
    collapsed = {crs: {re.sub(r"^(\d+)[A-Z]$", r"\1", p) for p in plats} for crs, plats in result.items()}
    return {crs: sorted(p, key=order) for crs, p in collapsed.items() if 0 < len(p) <= MAX_PLATFORMS}


def main():
    osm = json.loads(Path(sys.argv[1]).read_text()) if len(sys.argv) > 1 else overpass()
    platforms = build(osm)
    with OUT.open("w", newline="") as f:
        f.write("# Platforms at each station, from OpenStreetMap (c) OpenStreetMap contributors, ODbL 1.0.\n")
        f.write("# Built by tools/platforms/build_platforms.py. Space-separated, in platform order.\n")
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["crs", "platforms"])
        for crs in sorted(platforms):
            w.writerow([crs, " ".join(platforms[crs])])
    print(f"{len(platforms)} stations written to {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
