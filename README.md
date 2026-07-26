# Bestiary

A card-collection layer for Old School RuneScape. Every monster you kill has a chance to be
**captured** as a collectible card with a rarity, rolled stats, and the monster's real
Hitpoints — building a personal bestiary you can browse, showcase, reroll and export.

## Features

- **Capture cards on kill** — each kill rolls a catch chance (by monster difficulty tier and your
  Capture Level) and, on success, a weighted **rarity** (Common → Mythic) plus an independent
  **shiny** roll.
- **Power Level** — a card's headline number: the average of its seven rolled stats plus the
  monster's factual Hitpoints (at a reduced weight), so bosses tower over trash mobs.
- **Album / Dex** — a grid of every catalogued monster, with search, difficulty filter, per-monster
  detail pages, and paginated capture views.
- **Collection views** — grouped by rarity or monster, or a flat individual list; favourites; and
  rich detail dialogs with per-stat bands and personal bests.
- **Progression** — a Capture Level (1–99, with virtual levels), XP from kills and captures, and
  achievements.
- **Shop (in progress)** — earn Bestiary Credits per capture and spend them on the **Card Reroller**
  (re-roll a card's stats/shiny), or **discard** cards for credits.
- **Dashboards & Session Recap** — breakdowns of kills, species, and top captures, plus a
  shareable per-session summary.
- **Card export** — save or copy any card as an image, with a unique ID and owner stamp.
- **Overlay & chat notifications** — an on-screen capture animation and configurable chat messages.

## Data & privacy

- Your collection is stored locally as JSON in `~/.runelite/bestiary/`.
- With **"Fetch NPC images from the Wiki"** enabled (default), the plugin downloads monster artwork
  from the OSRS Wiki (`oldschool.runescape.wiki`) — only the monster's name is requested, no account
  or personal data is sent, and images are cached to disk. Turn the option off to make no network
  requests at all.

## Building

A standard RuneLite external plugin, built with Gradle (targets Java 11 bytecode; a JDK 11+ is
required — the Gradle wrapper downloads Gradle itself).

```
./gradlew build        # compile + run tests
./gradlew run          # launch a from-source RuneLite client with the plugin (developer mode)
./gradlew shadowJar    # build a sideloadable jar in build/libs/
```

`./gradlew run` is the easiest way to try it: log in and open the Bestiary panel from the sidebar.
The `[DEV]` helper buttons and the Developer Tools config only appear in developer mode.

## Project layout

```
src/main/java/com/bestiary/
  BestiaryPlugin      — event wiring, kill → capture flow
  BestiaryConfig      — user-facing settings
  model/              — CapturedCreature, BestiaryCollection, CreatureRarity, MonsterRoster, ...
  service/            — CaptureService, ProgressionService, BestiaryDataService,
                        BestiaryStore (JSON persistence), WikiImageService, KillTracker
  ui/                 — Swing panels and dialogs (BestiaryPanel, CollectionTab, Album, InfoTab, ...)
  util/               — RarityRoller, OddsCalculator, XpTable, CardId, RegionNames
```

## Licence

BSD 2-Clause — see [LICENSE](LICENSE).
