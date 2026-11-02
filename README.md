# Morningmeal

A minimal, fully offline calorie counter for Android, built with Kotlin, Jetpack Compose
and Room. The name comes from Old English *morgenmete*, "the morning meal."

No network permission, no accounts, no API calls.

## Open and run

1. Open this folder in Android Studio (Ladybug or newer).
2. Let Gradle sync, then run the `app` configuration on a device or emulator (Android 8.0+).

## What's in it

**Count** (landing page): one big number and an **Add food** button. Search, pick an amount,
and the number goes up. Tap the number to see, edit, or remove what's in it.
**Save to journal** files everything under a day, either as a named meal or as loose foods,
then clears the calculator.

**Journal**: every calendar day, newest first, scrolling back as far as you like. Each row
shows calories and macros; empty days are dimmed. Tap a day to see its meals and loose
foods; tap a food to change the amount or remove it; use a meal's ⋮ menu to rename or delete it.

**Settings**: daily goal (shown as a thin bar in the journal), theme, CSV export (to a file
you choose), clear calculator, delete journal data.

Can't find a food? Search for it and tap **Create "…"** to enter it from the label.
Long-press one of your own foods to delete it.

## The food database

`app/src/main/assets/foods.db` is built from **USDA SR Legacy** (7,793 foods, 14,278
household portions, about 1.8 MB). To rebuild or add more data types:

1. Download CSVs from https://fdc.nal.usda.gov/download-datasets: Foundation Foods,
   SR Legacy, and optionally FNDDS (mixed dishes) and Branded Foods (packaged products, large).
2. Unzip them and run:

   ```
   python3 tools/build_food_db.py ~/Downloads/FoodData_Central_* --out app/src/main/assets/foods.db
   ```

   Add `--no-branded` to keep the APK small. Python 3 standard library only.
3. Rebuild the app. The new database is copied over on first launch after install/update.

USDA FoodData Central data is public domain (CC0).

## Layout

```
app/src/main/java/app/morningmeal/
  data/          Nutrition math, bundled food DB (read-only SQLite + FTS4),
                 Room journal DB, settings (DataStore), Repository
  ui/calculator  Count page, item list, save-to-journal sheet
  ui/addfood     Search, amount picker, custom food form
  ui/journal     Infinite day list, day detail
  ui/settings    Settings
  ui/components  Shared rows, tab bar, formatting
tools/           build_food_db.py and the starter data it was built from
```

Journal entries store a snapshot of the food's per-100 g values, so rebuilding the food
database never changes your history.

## Ideas for next segments

- Barcode scanning (ML Kit's on-device scanner + the `gtin` column already in `foods.db`)
- Recent/favorite foods at the top of search
- Weekly averages in the journal
