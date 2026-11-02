#!/usr/bin/env python3
"""
Build app/src/main/assets/foods.db from USDA FoodData Central CSV downloads.

1. Download from https://fdc.nal.usda.gov/download-datasets (CSV format), e.g.
     - Foundation Foods
     - SR Legacy
     - FNDDS (survey foods, lots of mixed dishes like "Pizza, cheese")
     - Branded Foods  (large; adds barcode/GTIN for packaged products)
   or the single "Full Download of All Data Types".
2. Unzip each, then run:

     python3 tools/build_food_db.py ~/Downloads/FoodData_Central_* --out app/src/main/assets/foods.db

   Add --no-branded to leave out branded products and keep the app small.

All values in the output are per 100 g. The schema matches FoodDatabase.kt in the app:
  food(id, name, brand, gtin, kcal, protein, carbs, fat, rank)
  portion(food_id, label, grams)
  food_fts (FTS4 over name, brand)

USDA FoodData Central data is public domain (CC0 1.0).
Only the Python standard library is used.
"""

import argparse
import csv
import os
import re
import sqlite3
import sys
from pathlib import Path

csv.field_size_limit(min(sys.maxsize, 2**31 - 1))

# FoodData Central nutrient ids
ENERGY_KCAL = 1008          # "Energy" in kcal (SR Legacy, branded, FNDDS)
ENERGY_ATWATER_GEN = 2047   # Foundation foods often only have these two
ENERGY_ATWATER_SPEC = 2048
PROTEIN = 1003
FAT = 1004                  # Total lipid (fat)
CARBS = 1005                # Carbohydrate, by difference
WANTED = {ENERGY_KCAL, ENERGY_ATWATER_GEN, ENERGY_ATWATER_SPEC, PROTEIN, FAT, CARBS}

# Lower rank sorts first in search: whole/generic foods above mixed dishes above brands.
RANK = {
    "foundation_food": 0,
    "sr_legacy_food": 0,
    "survey_fndds_food": 1,
    "branded_food": 2,
}

SCHEMA = """
CREATE TABLE food(
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    brand TEXT,
    gtin TEXT,
    kcal REAL NOT NULL,
    protein REAL NOT NULL,
    carbs REAL NOT NULL,
    fat REAL NOT NULL,
    rank INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE portion(food_id INTEGER NOT NULL, label TEXT NOT NULL, grams REAL NOT NULL);
CREATE INDEX portion_food ON portion(food_id);
CREATE INDEX food_gtin ON food(gtin);
CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT);
CREATE VIRTUAL TABLE food_fts USING fts4(content="food", name, brand);
"""


def find(dirs, filename):
    """Each download unzips into its own folder; look for the file in any of them."""
    for d in dirs:
        for root, _, files in os.walk(d):
            if filename in files:
                yield Path(root) / filename


def read_rows(dirs, filename):
    for path in find(dirs, filename):
        with open(path, newline="", encoding="utf-8") as f:
            yield from csv.DictReader(f)


def to_float(s):
    try:
        return float(s)
    except (TypeError, ValueError):
        return None


SMALL_WORDS = {"and", "or", "of", "with", "in", "a", "the", "to", "for"}


def tidy_case(s):
    """Branded names are often ALL CAPS; make them readable. Leave mixed case alone."""
    s = re.sub(r"\s+", " ", s or "").strip()
    if not s or not s.isupper():
        return s
    words = s.lower().split(" ")
    return " ".join(
        w if (i > 0 and w in SMALL_WORDS) else w[:1].upper() + w[1:]
        for i, w in enumerate(words)
    )


def portion_label(row, units):
    desc = (row.get("portion_description") or "").strip()
    if desc and desc.lower() not in ("quantity not specified", "1 quantity not specified"):
        return desc
    amount = to_float(row.get("amount")) or 1.0
    unit = units.get(row.get("measure_unit_id"), "")
    if unit in ("undetermined", "unknown"):
        unit = ""
    modifier = (row.get("modifier") or "").strip()
    parts = [f"{amount:g}", unit, modifier]
    return " ".join(p for p in parts if p).strip()


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("dirs", nargs="+", help="Unzipped FoodData Central CSV folder(s)")
    ap.add_argument("--out", default="app/src/main/assets/foods.db")
    ap.add_argument("--no-branded", action="store_true", help="Skip branded products")
    ap.add_argument("--max-portions", type=int, default=6, help="Household portions kept per food")
    args = ap.parse_args()

    dirs = [Path(d) for d in args.dirs]
    for d in dirs:
        if not d.is_dir():
            sys.exit(f"Not a folder: {d}")

    # 1. Which foods, and of what type
    foods = {}  # fdc_id -> [name, data_type]
    for r in read_rows(dirs, "food.csv"):
        dt = r.get("data_type", "")
        if dt not in RANK or (args.no_branded and dt == "branded_food"):
            continue
        foods[r["fdc_id"]] = [r["description"].strip(), dt]
    if not foods:
        sys.exit("No food.csv rows found. Point this at the unzipped CSV download folder(s).")
    print(f"foods listed: {len(foods):,}")

    # 2. The four nutrients we keep (food_nutrient.csv is the big one; streamed)
    nutr = {}  # fdc_id -> {nutrient_id: amount}
    for r in read_rows(dirs, "food_nutrient.csv"):
        fid = r["fdc_id"]
        if fid not in foods:
            continue
        try:
            nid = int(r["nutrient_id"])
        except (KeyError, ValueError):
            continue
        if nid in WANTED:
            amt = to_float(r.get("amount"))
            if amt is not None:
                nutr.setdefault(fid, {})[nid] = amt

    # 3. Brand + barcode for branded products
    branded = {}
    if not args.no_branded:
        for r in read_rows(dirs, "branded_food.csv"):
            fid = r["fdc_id"]
            if fid in foods:
                brand = r.get("brand_name") or r.get("brand_owner") or ""
                gtin = re.sub(r"\D", "", r.get("gtin_upc") or "") or None
                serving = None
                if (r.get("serving_size_unit") or "").lower() in ("g", "grm"):
                    grams = to_float(r.get("serving_size"))
                    if grams and grams > 0:
                        label = (r.get("household_serving_fulltext") or "").strip() or "1 serving"
                        serving = (label, grams)
                branded[fid] = (tidy_case(brand), gtin, serving)

    # 4. Household portions ("1 cup, chopped" = 91 g)
    units = {r["id"]: r["name"] for r in read_rows(dirs, "measure_unit.csv")}
    portions = {}
    for r in read_rows(dirs, "food_portion.csv"):
        fid = r["fdc_id"]
        if fid not in foods:
            continue
        grams = to_float(r.get("gram_weight"))
        if not grams or grams <= 0:
            continue
        label = portion_label(r, units)
        if label:
            portions.setdefault(fid, []).append((label, grams))

    # 5. Write
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    if out.exists():
        out.unlink()
    db = sqlite3.connect(out)
    db.executescript(SCHEMA)

    kept = skipped = 0
    seen_gtin = set()
    food_rows, portion_rows = [], []
    for fid, (name, dt) in foods.items():
        n = nutr.get(fid, {})
        kcal = n.get(ENERGY_KCAL, n.get(ENERGY_ATWATER_GEN, n.get(ENERGY_ATWATER_SPEC)))
        if kcal is None or kcal < 0 or kcal > 950:
            skipped += 1
            continue
        brand, gtin, serving = branded.get(fid, (None, None, None))
        if gtin:
            if gtin in seen_gtin:  # branded data repeats products across releases
                skipped += 1
                continue
            seen_gtin.add(gtin)
        food_rows.append((
            int(fid),
            tidy_case(name) if dt == "branded_food" else name,
            brand or None,
            gtin,
            round(kcal, 1),
            round(n.get(PROTEIN, 0.0), 2),
            round(n.get(CARBS, 0.0), 2),
            round(n.get(FAT, 0.0), 2),
            RANK[dt],
        ))
        # A "medium" portion (1 medium banana) makes the best default amount in the app.
        plist = sorted(portions.get(fid, []), key=lambda p: 0 if "medium" in p[0].lower() else 1)
        if serving:
            plist = [serving] + plist
        unique = []
        for label, grams in plist:
            if all(label != u[0] for u in unique):
                unique.append((label, grams))
        for label, grams in unique[: args.max_portions]:
            portion_rows.append((int(fid), label, round(grams, 1)))
        kept += 1

    db.executemany("INSERT INTO food VALUES (?,?,?,?,?,?,?,?,?)", food_rows)
    db.executemany("INSERT INTO portion VALUES (?,?,?)", portion_rows)
    db.execute("INSERT INTO food_fts(docid, name, brand) SELECT id, name, coalesce(brand, '') FROM food")
    db.execute("INSERT INTO food_fts(food_fts) VALUES ('optimize')")
    db.execute("INSERT INTO meta VALUES ('source', 'USDA FoodData Central (CC0 1.0)')")
    db.execute("INSERT INTO meta VALUES ('built_from', ?)", (", ".join(d.name for d in dirs),))
    db.commit()
    db.execute("VACUUM")
    db.close()

    size_mb = out.stat().st_size / 1e6
    print(f"kept {kept:,} foods, {len(portion_rows):,} portions; skipped {skipped:,}")
    print(f"wrote {out} ({size_mb:.1f} MB)")


if __name__ == "__main__":
    main()
