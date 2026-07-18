# RuneLite Bestiary Plugin

A Pokémon-style creature collection plugin for Old School RuneScape. Every NPC you kill has a chance to be **captured** — logged to your Bestiary with a rarity tier, quality score, location, and date.

---

## Prerequisites

| Tool | Version | Path |
|------|---------|------|
| Microsoft OpenJDK | 21 | `C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot` |
| Apache Maven | 3.9+ | `C:\tools\apache-maven-3.9.9` |

> **Important:** RuneLite's bundled JRE is 11. Maven must use JDK 21 or the build fails. Always set `JAVA_HOME` explicitly before building.

---

## Building

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot"
C:\tools\apache-maven-3.9.9\bin\mvn.cmd clean compile -P dev -f F:\repos\Bestiary\pom.xml
```

Expected output: `BUILD SUCCESS` (a few `WARNING: system modules path` lines are normal).

---

## Running in dev mode

The `dev` Maven profile launches the RuneLite test client with all local plugins loaded:

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot"
C:\tools\apache-maven-3.9.9\bin\mvn.cmd exec:java -P dev -f F:\repos\Bestiary\pom.xml
```

Then in the RuneLite client: open the Plugin Hub → enable **Bestiary**.

---

## Quick testing

1. Set **Base Capture Rate = 100%** in the plugin config (guarantees a capture on every kill).
2. Kill any NPC.
3. Confirm a card appears in the **Collection** tab under the correct rarity header.
4. Click the card → verify the Detail dialog shows quality, region name, date.
5. Kill the same NPC again → verify the chat message is different from the last one (quality score changes).

---

## Project structure

```
src/main/java/.../bestiary/
  BestiaryPlugin.java       — main plugin (event wiring, kill/capture flow)
  BestiaryConfig.java       — all user-facing settings
  model/                    — data classes (CapturedCreature, BestiaryCollection, CreatureRarity, ...)
  service/                  — business logic
    BestiaryDataService     — load/save JSON; owns BestiaryCollection
    ProgressionService      — XP, level-up, achievement checking
    CaptureService          — probability roll, rarity assignment, quality generation
    KillTracker             — per-NPC kill tracking (handles despawn edge cases)
  ui/                       — all Swing panels and dialogs
    BestiaryPanel           — root PluginPanel, tabs host
    CollectionTab           — Grouped/Individual view with rarity section headers
    ProgressTab             — XP bar, level display, achievement list
    InfoTab                 — live stats strip + rarity table + how-it-works tiles
    CreatureCard            — one card per NPC+rarity in Grouped mode
    CaptureRow              — one compact row per capture in Individual mode
    CreatureDetailDialog    — full capture history for one NPC+rarity
    BestiaryOverlay         — in-game overlay notification on capture
  util/
    RegionNames             — ~200 OSRS region ID to readable name mappings
    XpTable                 — OSRS XP formula (matches in-game table exactly)
```

---

## Data files

| File | Contents |
|------|----------|
| `~/.runelite/bestiary/collection.json` | All captures + kill counts |
| `~/.runelite/bestiary/progress.json` | XP total, level, unlocked achievements |

Deleting these files resets all progress — useful during development.

---

## Configuration options

| Setting | Default | Description |
|---------|---------|-------------|
| Capture Enabled | true | Master on/off for capture attempts |
| Base Capture Rate | 10% | Starting probability at level 1 |
| Max Capture Rate | 60% | Ceiling including all level bonuses |
| Notify on Capture | true | Chat message on each capture |
| Notify Rare+ Only | false | Suppress Common/Uncommon notifications |
| Notify on Level Up | true | Chat message on level increase |
| Show Capture Overlay | true | In-game overlay popup on capture |
| Show Capture Animation | false | Pokeball-style shake animation on every kill attempt |
| Animate Failed Catches | false | Show animation even on failed attempts |
| Capture XP Enabled | true | Bonus XP on successful captures |
| Chat Notification Mode | Verbose | Verbose (per-capture + quality) or Batched (30s accumulation) |
