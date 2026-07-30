# Multi-account: account viewing (#48) + card transfer (#50) — design draft

Status: **#49 ownership + #50 intra-profile transfer BUILT + tested** (Mockup B, local-only).
**#48 view-any-account is design-only** — proposal with open questions below, not yet coded.
Cross-player trading is split out to **#121** (needs a backend; parked for a dev chat).

---

## Where we are now (post #47)

- One collection **file per account**: `~/.runelite/bestiary/accounts/<accountHash>.json`, keyed by
  the stable `accountHash`. A registry `accounts/index.json` maps `hash → {rsn, lastActive}`.
- `BestiaryDataService` holds a **single** in-memory `collection` + `progressionState`, which is the
  account you're logged into ("the played account"). Every game tick re-asserts it via `switchAccount`.
- The whole UI reads `dataService.getCollection()` and the `progressionService` singleton.
- Captures/kills/credits/XP are written by `BestiaryPlugin.handleKill` through `dataService` mutators
  (`addCapture`, `incrementKillCount`, `awardCaptureCredits`, `progressionService.awardXp`), which all
  operate on that single played collection/state.

The consequence for #48: **"switching account" cannot redirect where new captures go** — captures
always belong to the logged-in character. So the switcher is a **read-only viewer** of another
account's collection. The design below makes that safe and unambiguous.

---

## #49 — Ownership model (BUILT in this branch)

`CapturedCreature` now carries:
- `originalOwner` — RSN that first caught the card. Set at capture, **never changes** (survives trades).
- `currentOwner` — RSN that holds it now. Equals `originalOwner` until a transfer (#50) moves it.

Both default to the capturer; legacy saves are back-filled from `playerName` on load; owners carry
across a reroll. `transferTo(newOwner)` updates `currentOwner` only. This is the data spine #50 needs.

---

## #48 — View any account (read-only)

### Core idea: split "played" from "viewed"

Introduce a **viewed account** that only affects what the panel *displays*. The **played account**
(the logged-in character) keeps receiving captures in the background, always written to its own file.

`BestiaryDataService`:
- Keep `collection` / `progressionState` as the **played** state (all mutators + `handleKill` already
  use these — verified). Rename accessors to make intent explicit (`getPlayedCollection()`).
- Add nullable `viewedCollection`, `viewedProgressionState`, `viewedHash`, `viewedName`, loaded
  **read-only** from the chosen account's file (never written back).
- `getCollection()` returns `viewedCollection != null ? viewedCollection : collection`. One change,
  and the entire UI (Cards, Album, dashboards, Info stat boxes) shows the viewed account for free.
- `viewAccount(hash)` loads that file read-only; `clearView()` drops back to the played account.

### The capture-routing guarantee (your concern #1)

While viewing another account, a kill still happens for the **played** character. Because every
mutator writes to the played `collection` (not `getCollection()`), captures/credits/XP always land in
the **played** account's file, regardless of what's on screen. Two touch-ups make this airtight:
- `handleKill` currently reads `dataService.getCollection().getKillCount(...)` for the kill# label —
  repoint to a played-specific getter so the label is right while viewing.
- When a capture lands while you're viewing another account, show a small "New catch on
  \<playedRSN\>" toast and leave the view where it is (don't yank you back).

### The progression-display problem (the genuinely tricky bit)

`progressionService` is a singleton holding the **played** level/XP, and `awardXp` must keep hitting
it. But Progress tab / dashboards / the Info "Level" box read it for display — so while viewing
another account they'd show *your* level, not the viewed account's. Options:

- **A. Collection-only view (recommended first cut).** Viewing swaps the collection everywhere, but
  Level/XP-derived surfaces show the viewed account's numbers computed **directly from its stored
  `totalXp`** via `XpTable` (no `progressionService` involvement). The Progress tab's interactive bits
  (session recap, achievement notifier) are disabled in view mode — you're browsing, not playing.
- **B. Full dual-state progression.** Give `progressionService` an explicit played-vs-viewed split so
  every surface is perfectly correct. More invasive; better saved for after the concept lands.

Recommend **A** — it's correct for everything the viewer cares about (cards, rarity spread, species,
economy totals, level number) with far less blast radius.

### Multi-client safety (your concern #2)

- **Viewing never writes.** `viewAccount()` opens the file read-only; there is no code path that
  persists the viewed collection. So a second client viewing account X cannot corrupt X.
- **Same account in two clients** (both *played*) can still clobber each other's saves — but that risk
  exists **today**, unchanged by #48, and is out of scope here. Note it; a future lock-file/`lastActive`
  guard can warn "X is active in another client."
- Refresh the viewed snapshot from disk whenever the view is (re)opened, so you see the latest even if
  the other client just wrote.

### UI

- **Account picker.** A compact control listing known accounts from `index.json` (RSN · last active ·
  card count). Two candidate homes — see mockups. Selecting one calls `viewAccount(hash)`.
- **Read-only banner** while viewing a non-played account: `👁 Viewing AltRSN (read-only) — [Return to
  MainRSN]`. Reuse the existing "disable interactive controls" plumbing from the logged-out state so
  discard/reroll/reset/favourite are all inert in view mode.
- Logged out, the picker also lets you browse any account from the login screen (falls out for free).

### Open questions for you
1. First cut = collection-only view (A) or full dual-state progression (B)?
2. Picker as an always-present dropdown, or a button that opens an account-list dialog? (mockups below)
3. In view mode, hide the Shop/Progress tabs (they're about *your* play), or show them read-only?

---

## #50 — Transfer cards between your accounts  — BUILT (Mockup B)

Implemented local-only, DiscardDialog-style: a **"Transfer cards…"** button in the album opens a
MODELESS `TransferDialog` (owned by the album, so it auto-disposes when the album closes). Tick cards
+ pick a target account (from the `index.json` registry, excluding the active one) → confirm → the
cards leave the active collection and are appended to the target account's file (`writeAccountNow`,
target-first for safety), with `currentOwner` updated and `originalOwner` preserved. Refreshes the
panel + album (and the discard dialog) on completion. Needs the target to have logged in on this
machine at least once. The original mockups + rationale are kept below for reference.

Depended on #49 (owner fields, done). Did NOT need #48 — the target picker reads the registry directly.

### Flow
Right-click a card → **"Send to ▸ \<account\>"** (submenu of your other known accounts) → confirm →
the card is **moved**: removed from the sender's file, `transferTo(target)` sets `currentOwner`, and it's
appended to the target account's file (read-modify-write on `<targetHash>.json`, even if that account
is offline). A provenance line is logged so history survives. The card face can show
"Caught by MainRSN · Held by AltRSN" when owners differ.

### Mockup A — right-click send (recommended)
```
┌ right-click a card ─────────────┐
│ Favourite                       │
│ Album cover                     │
│ Name capture…                   │
│ Reroll…                         │
│ Discard                         │
│ ─────────────────────────────── │
│ Send to ▸  ┌──────────────────┐ │
│            │ AltRSN           │ │
│            │ IronMain         │ │
│            │ (2 more…)        │ │
│            └──────────────────┘ │
└─────────────────────────────────┘
  → confirm: "Send Mythic Goblin to AltRSN?
     It leaves MainRSN's collection."   [Send] [Cancel]
```

### Mockup B — transfer tray (bulk)
```
┌ Album ─ [Transfer mode]  ▸ target: [ AltRSN ▾ ] ┐
│  ▢ select cards to send…                        │
│  ┌────┐ ┌────┐ ┌────┐ ┌────┐                    │
│  │ ✓  │ │    │ │    │ │ ✓  │   2 selected        │
│  └────┘ └────┘ └────┘ └────┘                    │
│                        [ Send 2 → AltRSN ]      │
└─────────────────────────────────────────────────┘
```

### Mockup C — send from the card detail/export view
```
  Mythic Goblin  ·  PWR 142  ·  ✦ shiny
  Caught by: MainRSN
  ───────────────────────────────────────
  [ Export ]  [ Favourite ]  [ Send to ▾ ]
```

### Data / correctness
- **Atomic-ish move:** write the target file first (append), then remove from sender + save. If the
  target write fails, abort with the card still in the sender — never lose a card.
- **Offline target:** we own the target's file on disk, so a transfer works whether or not that account
  is logged in. If the target is *currently loaded in another client*, that client won't see the card
  until it reloads (accept as a known limitation, or add a "reload" nudge later).
- **Provenance:** append a transfer entry (from/to/epoch) — mirrors the reroll history pattern, and
  feeds the eventual provenance registry (#81).
- **Guard rails:** can only send to *your own* known accounts (from the registry); confirm dialog;
  favourites/nickname/album-cover travel with the card.

### Open questions for you
1. Single-card right-click send (A) as the v1, with bulk tray (B) later? Or bulk from the start?
2. Should a transfer require both accounts to have been seen on this machine (registry-gated), and
   should there be any cooldown/confirmation friction, or keep it frictionless since it's all "you"?
3. Show "Held by X" on the card face when `currentOwner != originalOwner`, or keep provenance in the
   Card Info panel only?
```
