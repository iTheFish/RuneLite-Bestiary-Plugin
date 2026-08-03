# Bestiary

**A card-collection layer for Old School RuneScape.** Every monster you kill has a chance to be
**captured** as a collectible card — with a rarity, rolled stats, and the monster's real
Hitpoints — building a personal bestiary you can browse, showcase, reroll and export.

<p align="center">
  <img src="docs/images/capture-animation.gif" alt="A capture and a level-up playing over the game screen" width="420"><br>
  <em>Kill a monster → roll to capture it → rank up your Capture Level. It all plays out on screen.</em>
</p>

---

## The panel

A sidebar panel is your home base: live stats (Level, Kills, Species, Caught), quick links to the
Album, Favourites and Catch Rates, and tabs for your Cards, the Shop and your Progress.

<p align="center">
  <img src="docs/images/BestiarySidePanel.png" alt="The Bestiary sidebar panel" width="250">
</p>

## Cards

Each capture is a card. A weighted **rarity** (Common → Mythic) and an independent **shiny** roll
decide how good it is: higher rarities lift every stat toward 99, and a card's headline **Power
Level** blends its seven rolled stats with the monster's factual Hitpoints and combat level, so
bosses tower over trash mobs. Same monster, wildly different cards:

<table align="center">
  <tr>
    <td align="center"><img src="docs/images/common_bestiary_chicken.png" width="230"><br><em>Common — Power 8</em></td>
    <td align="center"><img src="docs/images/epic_bestiary_chicken.png" width="230"><br><em>Epic — Power 14</em></td>
    <td align="center"><img src="docs/images/mythic_bestiary_chicken.png" width="230"><br><em>Mythic — Power 56</em></td>
  </tr>
</table>

Every card carries its full stat spread, HP, Prayer, combat class and species, a unique ID and an
owner stamp — and can be exported as an image to copy or share.

## Album

The album is a catalogue of all tracked monsters (200+). Search it, filter by difficulty tier or
species, sort it, and open any monster for its detail page and all your captures of it. Duplicates
can be discarded for credits, and cards transferred between your own accounts.

<p align="center">
  <img src="docs/images/Bestiary_Album.png" alt="The Album grid" width="760">
</p>

## Progression, dashboards & economy

A **Capture Level** (1–99, with virtual levels beyond) grows from kill and capture XP, unlocking
achievements along the way — all shown on the **Progress** tab. Captures also earn **Bestiary
Credits** you spend in the **Shop** on passive upgrades and the **Card Reroller**. Shareable
dashboards break down your progression, economy, species completion and best captures.

<table align="center">
  <tr>
    <td align="center"><img src="docs/images/CardTab.png" width="230"><br><em>Cards tab</em></td>
    <td align="center"><img src="docs/images/ShopUnlocks.png" width="230"><br><em>Shop</em></td>
    <td align="center"><img src="docs/images/ProgressionTab.png" width="230"><br><em>Progress tab</em></td>
  </tr>
</table>

<p align="center">
  <img src="docs/images/AllDashboardExport.png" alt="Progression, Economy, Species and Caught dashboards" width="760">
</p>

## Capture rates

Catch chance depends on the monster's difficulty tier and your Capture Level; rarity is a separate
roll that also improves as you level. The Info tab spells it all out — including the max rates —
and it climbs steeply as you grow. A level 1 hunter versus a level 52 one:

<table align="center">
  <tr>
    <td align="center"><img src="docs/images/BestiaryLevel1CatchRates.png" width="330"><br><em>Level 1</em></td>
    <td align="center"><img src="docs/images/BestiaryLevel52CatchRates.png" width="330"><br><em>Level 52</em></td>
  </tr>
</table>

## Features at a glance

Core mechanics:

- **Capture cards on kill** — a catch roll by difficulty tier and Capture Level, then a weighted
  rarity plus an independent shiny roll.
- **Power Level** — a card's headline number: its seven rolled stats plus the monster's factual
  Hitpoints and combat level (at reduced weight), so bosses outclass trash mobs.
- **Overlay & chat notifications** — an on-screen capture animation, level-up banners, and
  configurable chat messages.

Tabs & views:

- **Cards tab** — your collection grouped by rarity or monster, or a flat individual list, plus
  favourites; rich detail dialogs with per-stat bands and personal bests.
- **Album** — a searchable, filterable grid of every known monster, with per-monster detail pages
  and paginated capture views.
- **Shop** — spend Bestiary Credits (earned per capture) on passive upgrades and the Card Reroller.
- **Progress tab** — a Capture Level (1–99, with virtual levels), XP from kills and captures, and
  achievements.

Screens & tools (each opens its own window):

- **Catch Rates** — per-difficulty catch and rarity chances at your current Capture Level.
- **Card Info** — a per-card breakdown: Overview, Odds, a percentile/reroll Graph, and reroll history.
- **Card Reroller** — re-roll a card's stats/shiny for credits, with an odds breakdown and a
  before/after result (shiny stays shiny).
- **Card export** — save or copy any card, or a whole album page, as an image with a unique ID and
  owner stamp.
- **Discard duplicates** — turn spare cards into credits.
- **Transfer cards** — move cards between your own accounts via a searchable picker.
- **Dashboards** — Progression, Economy, Species and Caught breakdowns, exportable as images.
- **Session Recap** — a shareable per-session summary of XP and credits gained.
- **Reset Progress & Collection** — wipe everything and start over (double-confirmed).
- **About** — plugin info and version.

Accounts:

- **Multiple accounts** — each account keeps its own collection; browse any known account read-only.

## Settings

Settings live under the **Bestiary** config group (RuneLite sidebar → wrench icon → Bestiary),
organised into three collapsible sections. Sensible defaults ship out of the box — the list below
notes each default in brackets.

**Notifications**

- **Notify on Capture** *(on)* — post a chat message on each capture.
- **Notify for Rarity** *(All)* — which captures produce a chat message: all of them, or only a
  chosen rarity and above (Uncommon+, Rare+, Epic+, Legendary+, or Mythic only). Shinies always
  announce regardless of this setting.
- **Chat Notification Mode** *(Verbose)* — **Verbose** posts one message per capture (with the kill
  number, so identical captures aren't silently de-duplicated by the client). **Batched** rolls up
  captures of the same monster + rarity that happen within a 9-second lull into one `Nx …` summary.
  Shinies always post individually in either mode.
- **Notify on Achievement** *(on)* — post a chat message when you unlock an achievement.
- **Notify on Level Up** *(on)* — post a chat message when your Capture Level increases.

**Overlay & animation**

- **Show Capture Overlay** *(on)* — the on-screen card reveal shown when you catch something.
- **Show Capture Animation** *(on)* — the collection-jar shake sequence that plays *before* the
  result on each kill attempt.
- **Animate Failed Catches** *(off)* — extends that animation to failed attempts too (requires the
  capture animation to be enabled).
- **Show Level-Up Banner** *(on)* — play the on-screen banner when your Capture Level increases.
- **Overlay Position** *(Top center)* — which edge/corner of the screen the overlay appears at.
- **Overlay Width** *(200px; range 150–300)* — width of the overlay panel.
- **Overlay Opacity** *(75%; range 20–100)* — how opaque the overlay's background is. Lower is more
  see-through, so it blocks less of the screen.

**Album & images**

- **Auto-shimmer EPIC+ Cards** *(on)* — periodically play the foil shimmer on Epic+ cards in the
  Album, every 10 seconds.
- **Fetch NPC images from the Wiki** *(off)* — the only setting that touches the network. Downloads
  monster artwork from the OSRS Wiki to show on cards and in the album; only the monster's name is
  requested (no account or personal data), and images are cached to disk. Off by default — turn it
  on to sync images. This setting only controls **network fetching**: once images are cached you can
  turn it back off and keep seeing them, then re-enable it later to fetch newly added monsters. See
  *Data & privacy* below.

## Data & privacy

- Your collection is stored locally as JSON in `~/.runelite/bestiary/`.
- The plugin makes **no network requests by default**. Enabling **"Fetch NPC images from the Wiki"**
  (off by default) downloads monster artwork from the OSRS Wiki (`oldschool.runescape.wiki`) — only
  the monster's name is requested, no account or personal data is sent, and images are cached to disk.

## Community

- **Discord** — help, showcase your cards, and shape what's next: https://discord.gg/2HWSHH4mS5
- **GitHub** — issues, source and releases: https://github.com/iTheFish/RuneLite-Bestiary-Plugin

## Building

A standard RuneLite external plugin, built with Gradle (targets Java 11 bytecode; a JDK 11+ is
required — the Gradle wrapper downloads Gradle itself).

```
./gradlew build        # compile + run tests
./gradlew run          # launch a from-source RuneLite client with the plugin (developer mode)
./gradlew shadowJar    # build a sideloadable jar in build/libs/
```

`./gradlew run` is the easiest way to try it: log in and open the Bestiary panel from the sidebar.

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

## Disclaimer

Bestiary is a free, fan-made minigame for entertainment only. Bestiary Credits, cards, rarities,
shinies and Power Levels live entirely inside the plugin — they have **no real-world or in-game
value** and cannot be bought, sold, or traded for real money, RuneScape gold, or items. Nothing in
Bestiary affects your Old School RuneScape account or grants any in-game advantage.

Bestiary is not affiliated with, endorsed by, or associated with Jagex Ltd. Old School RuneScape is a
trademark of Jagex Ltd; all monster names and artwork are the property of Jagex and/or the OSRS Wiki.

## Licence

BSD 2-Clause — see [LICENSE](LICENSE).
