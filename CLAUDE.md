# Bestiary Plugin — AI Handover

## What this is

RuneLite OSRS plugin. Pokemon-style capture system. On every NPC kill, a random roll fires against a configurable base capture rate. Successful captures are stored with rarity (6 tiers), quality score (0-100), location (readable region name), date, and kill count.

---

## Build commands

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot"
C:\tools\apache-maven-3.9.9\bin\mvn.cmd clean compile -P dev -f F:\repos\Bestiary\pom.xml
```

A few `WARNING: system modules path` lines are normal. `BUILD SUCCESS` = good.

---

## Critical architectural decisions

### 1. @Singleton is mandatory on ALL services and UI classes

ALL service classes and UI panels MUST carry `@Singleton` (from `javax.inject`). Without it, Guice's child injector creates separate instances per injection point — the panel gets an empty collection while the plugin writes to a different instance.

Affected classes (must all have @Singleton):
- `BestiaryDataService`, `ProgressionService`, `CaptureService`, `KillTracker`
- `BestiaryPanel`, `BestiaryOverlay`

### 2. Guice wiring in BestiaryPlugin

`BestiaryPlugin` uses `@Inject` fields for all services and UI. Config is bound via `@Provides BestiaryConfig provideConfig(ConfigManager)`. There is no manual `injector.getInstance()` call — Guice handles everything through field injection.

### 3. File encoding — historical corruption warning

Source files suffered encoding corruption (UTF-8 bytes stored as Windows-1252 mojibake). When making string replacements via PowerShell, build search strings by concatenation rather than typing Unicode chars directly:

```powershell
# WRONG — encoding ambiguity causes silent failures
$search = "â€¢"

# CORRECT
$search = "\" + "u00e2" + "\" + "u20ac" + "\" + "u00a2"
```

Check for remaining mojibake with: `Select-String -Path *.java -Pattern "\\u00e2"`.

---

## Domain logic

### Capture probability
```
captureRate = baseCaptureRate + (level - 1) * scalingFactor
captureRate = min(captureRate, maxCaptureRate)
```
Both `baseCaptureRate` and `maxCaptureRate` are user-configurable (default 10% / 60%).

### Rarity assignment
After a successful capture, rarity is rolled from a weighted table:
- Common 75%, Uncommon 17%, Rare 6%, Epic 1.5%, Legendary 0.4%, Mythic 0.1%

### Quality score (0-100)
Derived from NPC combat stats at time of capture. Higher combat level = higher base quality. Stored in `CaptureQuality` object inside `CapturedCreature`.

### XP table
`XpTable.java` uses the exact OSRS formula:
```
Points(L) = floor((L + 300 * 2^(L/7)) / 4)
Cumulative XP to level L = sum of Points(1..L-1)
```
Spot-check: Level 50 = 101,333 XP, Level 99 = 13,034,431 XP. No changes needed.

### Kill XP
`max(10, npcCombatLevel * 10)` per kill. Capture bonus = killXp * rarity.xpMultiplier.

### Region IDs
`WorldPoint.getRegionID()` returns `(x/64) | ((y/64) << 8)`. `RegionNames.java` maps ~200 common OSRS region IDs to readable names (Lumbridge, Varrock, etc.). Unknown IDs fall back to `"Region N"`.

---

## UI architecture

### Panel hierarchy
```
BestiaryPanel (PluginPanel, @Singleton)
  ├── JTabbedPane
  │     ├── CollectionTab    — creature cards / rows
  │     ├── ProgressTab      — XP bar + achievements
  │     └── InfoTab          — live stats + rarity table + how-it-works
  └── statsLabel header (species count | capture count)
```

### CollectionTab modes
- **Grouped**: one `CreatureCard` per NPC+rarity, arranged under rarity section headers (MYTHIC → COMMON)
- **Individual**: one `CaptureRow` per raw capture, flat sorted list
- Toggle buttons switch between modes; sort dropdown applies in both modes

### CreatureDetailDialog stacking prevention
```java
private static CreatureDetailDialog current;
// in constructor:
if (current != null && current.isShowing()) current.dispose();
current = this;
```
Opening a new detail dialog always closes the previous one.

### MODELESS dialogs
All detail dialogs use `ModalityType.MODELESS` so the user can still interact with the RuneLite client while the dialog is open.

### Thread safety rule
All game-thread callbacks arrive on the RuneLite client thread. All Swing updates must go through `SwingUtilities.invokeLater()`. Batch notification state (`batchCounts`, `batchFutures` maps in `BestiaryPlugin`) is only ever accessed on the `ScheduledExecutorService` thread to avoid races.

---

## Chat notification deduplication

RuneLite silently drops chat messages that are identical to the previous one. Fix: append the quality score to each capture message in VERBOSE mode — `"! (Quality: 72)"` — making every message unique even for the same NPC+rarity back-to-back.

BATCHED mode accumulates captures per NPC+rarity key over 30s then flushes a count: `"3x Uncommon Goblin captured!"`.

---

## Known state (July 2026)

- Build: clean on JDK 21 + Maven 3.9.9
- Collection tab: Grouped/Individual toggle works; rarity section headers display with colored left borders
- Region names: ~200 hardcoded OSRS regions; unknown IDs show raw `"Region N"`
- Chat: VERBOSE (per-capture + quality) and BATCHED (30s accumulation) modes
- Achievements: 13 total, auto-unlock on species/kills/levels/rarity targets
- Dialog stacking: prevented via `static CreatureDetailDialog current`
- Data storage: `~/.runelite/bestiary/collection.json` and `progress.json`
- Encoding: source files may still contain `â€` sequences in comments — harmless at runtime, avoid touching those lines without checking file encoding first
