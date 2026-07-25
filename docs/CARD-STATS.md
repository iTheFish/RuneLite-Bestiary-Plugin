# How a Card Gets Its Stats — Full Pipeline

_Reference for the capture → card flow. Every number here is live in the code; file/line
pointers are given so you can find the lever you want to pull._

---

## The pipeline at a glance

```
 Kill an NPC
    │
    ▼
[1] CATCH ROLL ........... did we catch it at all?      (difficulty tier × your level)
    │  (fail → nothing)
    ▼
[2] RARITY ROLL .......... Common … Mythic              (weighted; your level shifts it rarer)
    │
    ▼
[3] SHINY ROLL ........... shiny? yes/no                (independent, 0.2%→2% by level)
    │
    ▼
[4] STAT GENERATION ...... the 6 rolled stats           (combat class + rarity + stat bases + shiny)
    │       ATK STR DEF MAG RNG AGI
    ▼
[5] POWER LEVEL .......... the headline number          (avg of the 6 stats + the monster's HP)
    │
    ▼
[6] CARD DISPLAY ......... everything you see on the card
```

Stages 1–4 happen in `CaptureService.attemptCapture()`. Stage 5 is computed on demand
(`CapturedCreature.powerLevel()`). Stage 6 is all in `AlbumCard.paint()`.

Only **rolled once, at capture time**, and then frozen forever: rarity, shiny, the 6 stats.
Everything else (HP, prayer, class, species, difficulty, Power Level) is looked up live from
the monster's data, so if we retune a monster later, old cards update too.

---

## [1] Catch roll — *do we catch it?*

`CaptureService.calculateCatchRate(captureLevel, difficulty)`

Each **difficulty tier** has a base %, a per-level bonus, and a cap:

| Tier | Base (lvl 1) | +per level | Cap (lvl 99) |
|------|------|------|------|
| Beginner | 20% | +0.50% | 60% |
| Easy | 15% | +0.40% | 50% |
| Medium | 10% | +0.30% | 40% |
| Hard | 6% | +0.22% | 28% |
| Elite | 3% | +0.13% | 15% |
| Boss | 1.5% | +0.07% | 8% |

`rate = min(base + (level-1)×perLevel, cap)`. One `rng` roll; if it fails, no card.
Difficulty tier comes from `MonsterRoster.getDifficulty()`.

**This stage does not touch stats** — it only gates whether a card is created.

---

## [2] Rarity roll — *Common … Mythic*

`RarityRoller.roll(rng, captureLevel)`

Base weights (`CreatureRarity`): Common 75%, Uncommon 17%, Rare 6%, Epic 1.5%,
Legendary 0.4%, Mythic 0.1%.

Your **capture level tilts it toward rarer** outcomes. At level 99 the weights are multiplied by:
Common ×0.5, Uncommon ×1.3, Rare ×2, Epic ×4, Legendary ×8, Mythic ×12 (linear from ×1 at level 1).
Then renormalised and rolled.

**Rarity is the single biggest driver of the rolled stats** (see stage 4). It also sets the
card's colour/frame and the XP multiplier (Common 1× … Mythic 50×).

---

## [3] Shiny roll — *independent of everything*

`CaptureService.shinyChance(captureLevel)` → `0.2% at level 1, scaling linearly to 2% at level 99`.

Rolled **separately** after rarity, so **any rarity can be shiny** (a shiny Common is possible).
If shiny, the stat generator uses a different, near-max path (stage 4).

---

## [4] Stat generation — *the 6 rolled stats*

`RarityRoller.generateQuality(combatClass, rarity, statBases, rng, shiny)`

The reviewed per-stat bases (`MonsterRoster.STAT_BASES`, from `base-stats-review.csv`) represent an
**average / Epic** card. Rarity moves each stat from that centre — but **how much depends on the
base** (`RarityRoller.statCentre`), then a wiggle is added:

Each rarity has an **expected centre** (base-dependent), and the stat **rolls a band** around it:

```
centre:  below Epic  = base × mult              // Common ×0.72, Uncommon ×0.82, Rare ×0.92
         Epic        = base
         above Epic  = base + lift × (99 − base) // Legendary lift 0.30, Mythic lift 0.60

band:    [ centre − 0.65×gapDown , centre + 0.65×gapUp ]   // gap = distance to neighbour centre
stat  =  uniform int in that band                          // non-shiny
shiny =  clamp( centre + uniform(+6, +20), 1, 99 )         // always high
```

Because each band reaches **0.65** of the way toward each neighbour (> 0.5), **adjacent bands
overlap** — a lucky Legendary can out-roll an unlucky Mythic. The **above-Epic lift is a fraction of
the headroom (99 − base)**, so a **weak stat gets a big boost at high rarity** while an already-high
one only edges up. Example (centre, and the roll band):

| base | Common | Epic | Legendary | Mythic |
|---|---|---|---|---|
| 1  | 1 (1)     | 1 (1–20)   | 30 (11–50) | **60 (40–80)** |
| 55 | 40 (37–43) | 55 (52–63) | 68 (60–76) | 81 (73–89) |
| 90 | 65 (59–71) | 90 (85–92) | 93 (91–94) | 95 (94–96) |

So weak monsters stay fun at high rarity, Epic ≈ base, **nothing auto-maxes** unless base ~97+, and
neighbouring rarities overlap. Band width scales with the local gap (`RarityRoller.BAND_OVERLAP`). The stat wiggle is **flavour only** — deliberately **not** part of a card's
rarity odds; the odds screen shows each stat's offset-from-expected as info, but the "this exact
card" figure is **rarity × shiny** alone. **Combat class is a display label only now** — the
per-stat bases already encode each monster's profile, so class no longer decides which stat spikes.

The result is a `CreatureQuality` — six ints in 1..99. **These never change after capture.**

**"What were the odds?"** (`OddsCalculator` + `OddsDialog`, button on the export screen) reconstructs
this exactly: `P(stat = v)` = (number of the 7 wiggle offsets that land on `v`) ÷ 7, multiplied across
all six stats, times the rarity/shiny chances — and shows the catch chance separately.

---

## [5] Power Level — *the headline number*

`CapturedCreature.powerLevel()` = `round( (ATK+STR+DEF+MAG+RNG+AGI + monsterHP) / 7 )`

- HP is a **factual, looked-up** attribute (`MonsterRoster.getHitpoints`), **not** a rolled stat.
- Because HP is a raw number (single digits for a chicken, hundreds–thousands for a boss), it
  **dominates the average** for tanky monsters. That's intentional: a maxed chicken can never
  approach a boss's Power Level. **Power Level can exceed 99** for big bosses.
- The 6 stats are basically **flavour** now — they nudge Power Level a little; HP decides the tier.

---

## [6] What actually shows on the card

`AlbumCard.paint()` pulls these together:

| Element | Source | Notes |
|---|---|---|
| **Power pill `P:xx`** (header) | `powerLevel()` | colour-banded by value (`powerColor`): grey→green→blue→purple→orange→red |
| **Rarity colour / frame / dots** | `rarity` | frame + the coloured dots |
| **Shiny wash + ✦ sparkles** | `shiny` | golden overlay; only if the shown capture is shiny |
| **HP pill** ❤ | `getHitpoints()` | factual; real in-game skill icon |
| **Prayer pill** | `getPrayer()` | factual (default 1) |
| **Agility pill** | rolled `AGI` stat | it's stat index 5, relocated out of the bar block |
| **Stat bars (ATK/STR/DEF/MAG/RNG)** | the rolled stats | icon = real skill sprite; primary stat's value is amber; 99 = full bar |
| **Class label** | `getCombatClass()` | Warrior/Mage/Marksman/Battlemage/Warden/Occultist/Juggernaut/Apex |
| **Species / Difficulty badges** | `getSpecies()` / `getDifficulty()` | |
| **Combat level, region, date, kill#** | capture metadata | |

For an album **catalog** card (one per monster) the shown rarity/shiny/stats come from the chosen
**album cover** capture, or else the "best" capture (highest rarity, then highest Power Level).

---

## The levers (if we want to retune)

| Want to change… | Pull this | Where |
|---|---|---|
| How often you catch things | tier base/perLevel/cap | `CaptureService.calculateCatchRate` |
| How fast rarity improves with level | the ×multipliers | `RarityRoller.roll` |
| Base rarity odds | the probabilities | `CreatureRarity` |
| Shiny frequency | `0.002 … 0.02` | `CaptureService.shinyChance` |
| How much shinies over-roll | `SHINY_MIN/MAX_BONUS` (15..22) | `RarityRoller` |
| How hard primaries spike vs secondaries | the `secondCeil` / `tertiCeil` / gaussian sd | `RarityRoller.generateQuality` |
| Which stats a monster favours | its `CombatClass` | `MonsterRoster.COMBAT_CLASSES` |
| A monster's stat floors | its row | `MonsterRoster.STAT_BASES` |
| **Power Level formula / HP weight** | the `/7` and HP term | `CapturedCreature.powerLevel()` |
| A monster's HP / Prayer | its entry | `MonsterRoster.HITPOINTS` / `PRAYER` |

**The one knob most worth discussing:** `powerLevel()` currently weights HP as *one of seven equal
terms* with the six stats. If bosses feel too far ahead (or chickens too high), we change only that
line — e.g. weight HP more/less, use `log(HP)`, or divide differently — without touching anything else.
