package com.bestiary.service;

import com.bestiary.model.Achievement;
import com.bestiary.model.BestiaryCollection;
import com.bestiary.model.CapturedCreature;
import com.bestiary.model.CreatureQuality;
import com.bestiary.model.CreatureRarity;
import com.bestiary.model.MonsterRoster;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Loads and saves the bestiary collection and progression state as JSON (via
 * {@link BestiaryStore}). The whole collection lives in memory here; mutations update it
 * and then persist a snapshot — debounced for high-frequency events (kills, credits),
 * immediate for valuable/rare ones (captures, rerolls, discards) and lifecycle events.
 */
@Slf4j
@Singleton
public class BestiaryDataService {

    private final ProgressionService progressionService;
    private final BestiaryStore store;

    /** The "played" collection — the logged-in character. All mutators write here. */
    private BestiaryCollection collection = new BestiaryCollection();
    private ProgressionService.ProgressionState progressionState = new ProgressionService.ProgressionState();

    /** The active account (RuneLite accountHash), or null while logged out. */
    private Long activeAccountHash;
    /** Display name (RSN) of the active account, for dialog headers. */
    private String activeAccountName = "";

    // --- View-any-account (#48): a read-only overlay of another account's collection. ---
    /** Non-null while viewing another account; the UI reads this instead of the played collection. */
    private BestiaryCollection viewedCollection;
    /** RSN of the account being viewed (for the read-only banner + dialog headers). */
    private String viewedAccountName = "";
    /** accountHash of the account being viewed (so the dropdown can mark the selection). */
    private Long viewedAccountHash;
    /** The viewed account's stored lifetime XP, so its level can be shown without touching progression. */
    private long viewedTotalXp;
    /** The viewed account's unlocked achievements, for read-only dashboards while viewing. */
    private EnumSet<Achievement> viewedAchievements;

    @Inject
    public BestiaryDataService(ProgressionService progressionService, BestiaryStore store) {
        this.progressionService = progressionService;
        this.store = store;
        // Let progression grant credits for level-ups and achievement unlocks.
        progressionService.setCreditAwarder(this::awardCredits);
        // Wire progression to the (empty) logged-out state so nothing NPEs before an account loads.
        progressionService.init(progressionState, collection);
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Switches to {@code accountHash}'s collection: flushes the current account, repoints the store,
     * and loads that account's data (or an empty collection for a first-seen account). Called on
     * {@code LOGGED_IN} from the client thread. No-ops on a repeated hash (e.g. world hops) so an
     * in-progress session's unsaved state isn't reloaded away.
     */
    public boolean switchAccount(long accountHash, String rsn) {
        if (activeAccountHash != null && activeAccountHash == accountHash) {
            // Same account (e.g. a later tick once the RSN is available, or a world hop): just
            // fill in / refresh the display name and registry entry. No reload.
            if (rsn != null && !rsn.isEmpty() && !rsn.equals(activeAccountName)) {
                activeAccountName = rsn;
                store.setActiveAccount(accountHash, rsn);
            }
            return false;
        }
        clearView();                                   // a real account change drops any read-only view
        if (activeAccountHash != null) persistNow();   // flush the previously active account
        activeAccountHash = accountHash;
        activeAccountName = rsn != null ? rsn : "";
        store.setActiveAccount(accountHash, activeAccountName);
        load();
        return true;
    }

    /**
     * Handles logout (return to the login screen): flushes the active account to disk, deactivates
     * the store, and clears the in-memory collection/progression so no account's data lingers in the
     * (logged-out) UI. The next login reloads that account's data fresh from disk.
     */
    public void handleLogout() {
        clearView();                  // drop any read-only view — nothing is browsable while logged out
        if (activeAccountHash == null) return;
        persistNow();                 // flush the active account to its own file first
        store.clearActiveAccount();
        activeAccountHash = null;
        activeAccountName = "";
        collection       = new BestiaryCollection();
        progressionState = new ProgressionService.ProgressionState();
        progressionService.init(progressionState, collection);
    }

    /** True once a character has logged in and its collection is loaded. */
    public boolean hasActiveAccount() {
        return activeAccountHash != null;
    }

    /** Builds an in-memory collection from a stored snapshot (shared by played load + viewed load). */
    private static BestiaryCollection hydrateCollection(BestiaryStore.StoreData d) {
        BestiaryCollection col = new BestiaryCollection();
        for (CapturedCreature c : d.captures) {
            // Migration: pre-#49 saves have no owner fields — seed them from the capturer.
            if (c.originalOwner == null || c.originalOwner.isEmpty()) c.originalOwner = c.playerName;
            if (c.currentOwner == null || c.currentOwner.isEmpty()) {
                c.currentOwner = c.originalOwner != null && !c.originalOwner.isEmpty()
                        ? c.originalOwner : c.playerName;
            }
            col.creatures.add(c);
            col.captureCountByNpc.merge(c.npcName, 1, Integer::sum);
        }
        col.killCounts   = new HashMap<>(d.killCounts);
        col.credits      = d.credits;
        col.lifetimeCreditsEarned = d.lifetimeCreditsEarned;
        col.lifetimeCreditsSpent  = d.lifetimeCreditsSpent;
        // Legacy saves predate lifetime tracking — baseline "earned" from the current balance
        // so the economy dashboard/achievements aren't zeroed for existing players.
        if (col.lifetimeCreditsEarned == 0 && col.credits > 0) {
            col.lifetimeCreditsEarned = col.credits;
        }
        col.shopUpgrades = new HashMap<>(d.shopUpgrades);
        col.lifetimeCardsSent = d.lifetimeCardsSent;
        col.lifetimeCardsDiscarded = d.lifetimeCardsDiscarded;

        // Lifetime captures: prefer the stored counter, but baseline it from the cards this account
        // actually caught (not traded-in) so pre-#N accounts don't read 0. Because own-caught-held can
        // never exceed true lifetime captures, the max() is a safe one-time floor, self-correcting once
        // the counter is tracked live (received cards are excluded, so they never inflate "Caught").
        col.lifetimeCaptures = Math.max(d.lifetimeCaptures, col.ownCaughtHeldCount());
        col.lifetimeCapturesByNpc = new HashMap<>(d.lifetimeCapturesByNpc);
        // Per-species baseline = max(stored, own-caught-held for that species).
        java.util.Map<String, Integer> ownHeldByNpc = new HashMap<>();
        for (CapturedCreature c : col.creatures) {
            if (!BestiaryCollection.isTradedIn(c)) ownHeldByNpc.merge(c.npcName, 1, Integer::sum);
        }
        for (java.util.Map.Entry<String, Integer> e : ownHeldByNpc.entrySet()) {
            col.lifetimeCapturesByNpc.merge(e.getKey(), e.getValue(), Math::max);
        }
        return col;
    }

    // -------------------------------------------------------------------------
    // View any account (read-only, #48)
    // -------------------------------------------------------------------------

    /**
     * The collection the UI should display: the read-only viewed account if one is selected (#48),
     * otherwise the played account. Every panel/dialog reads through here, so switching the view
     * updates the whole UI. Mutators deliberately do NOT go through this — they write the played
     * collection directly, so captures always land on the logged-in character even while viewing.
     */
    public BestiaryCollection getCollection() {
        return viewedCollection != null ? viewedCollection : collection;
    }

    /** The played (logged-in) collection, regardless of any active view. */
    public BestiaryCollection getPlayedCollection() {
        return collection;
    }

    /** True while a read-only view of another account is active. */
    public boolean isViewing() {
        return viewedCollection != null;
    }

    /** RSN of the account currently being viewed (empty when not viewing). */
    public String getViewedAccountName() {
        return viewedAccountName;
    }

    /** accountHash of the account currently being viewed, or null when not viewing. */
    public Long getViewedAccountHash() {
        return viewedAccountHash;
    }

    /**
     * Loads {@code accountHash}'s collection read-only for display (#48). Never writes it back.
     * No-ops (and clears any view) if the target is the played account. Returns true if a view
     * is now active.
     */
    public boolean viewAccount(long accountHash, String rsn) {
        if (activeAccountHash != null && activeAccountHash == accountHash) {
            clearView();   // "viewing" your own account just means the normal played view
            return false;
        }
        BestiaryStore.StoreData d = store.readAccount(accountHash);
        viewedCollection    = hydrateCollection(d);
        viewedTotalXp       = d.totalXp;
        viewedAchievements  = parseAchievements(d.achievements);
        viewedAccountHash   = accountHash;
        viewedAccountName   = rsn != null ? rsn : "";
        return true;
    }

    /** Drops the read-only view and returns the UI to the played account. */
    public void clearView() {
        viewedCollection    = null;
        viewedAchievements  = null;
        viewedAccountHash   = null;
        viewedAccountName   = "";
        viewedTotalXp       = 0;
    }

    /** All known accounts from the registry (for the switcher dropdown), most-recently-active first. */
    public java.util.List<BestiaryStore.AccountRef> listAllAccounts() {
        return store.listAccounts();
    }

    /** accountHash of the played (logged-in) account, or null while logged out. */
    public Long getActiveAccountHash() {
        return activeAccountHash;
    }

    /**
     * Bestiary level to DISPLAY: the viewed account's level (from its stored XP via {@link XpTable})
     * when viewing, else the live played level. Keeps view mode collection-only (no dual progression).
     */
    public int getDisplayLevel() {
        return viewedCollection != null
                ? com.bestiary.util.XpTable.levelForXp(viewedTotalXp)
                : progressionService.getLevel();
    }

    /** Lifetime XP to DISPLAY — the viewed account's stored XP when viewing, else the played total. */
    public long getDisplayTotalXp() {
        return viewedCollection != null ? viewedTotalXp : progressionState.totalXp;
    }

    /** XP remaining to the next level for the DISPLAYED account (viewed or played). */
    public long getDisplayXpToNextLevel() {
        if (viewedCollection == null) return progressionService.getXpToNextLevel();
        int lvl = com.bestiary.util.XpTable.levelForXp(viewedTotalXp);
        long nextStart = com.bestiary.util.XpTable.xpForLevel(
                Math.min(lvl + 1, com.bestiary.util.XpTable.MAX_VIRTUAL_LEVEL));
        return Math.max(0, nextStart - viewedTotalXp);
    }

    /** Unlocked achievements to DISPLAY — the viewed account's set when viewing, else the played set. */
    public java.util.Set<Achievement> getDisplayAchievements() {
        return viewedCollection != null && viewedAchievements != null
                ? viewedAchievements : progressionState.unlockedAchievements;
    }

    // -------------------------------------------------------------------------
    // Intra-profile card transfer (#50)
    // -------------------------------------------------------------------------

    /** Known accounts other than the active one — candidate targets for a card transfer. */
    public java.util.List<BestiaryStore.AccountRef> listOtherAccounts() {
        java.util.List<BestiaryStore.AccountRef> all = store.listAccounts();
        all.removeIf(a -> activeAccountHash != null && a.hash == activeAccountHash);
        return all;
    }

    /**
     * Moves {@code cards} from the active account to another of the player's accounts (#50). Writes
     * the target file FIRST (append), then removes the cards from the active collection and persists,
     * so a failed target write can never lose a card. Updates {@code currentOwner} (originalOwner is
     * preserved). Returns the number actually transferred.
     */
    public int transferCards(java.util.Collection<CapturedCreature> cards, long targetHash, String targetRsn) {
        if (isViewing()) return 0;   // read-only while viewing another account
        if (activeAccountHash != null && targetHash == activeAccountHash) return 0;  // can't send to self
        java.util.List<CapturedCreature> moving = new ArrayList<>();
        for (CapturedCreature c : cards) {
            if (collection.creatures.contains(c)) moving.add(c);
        }
        if (moving.isEmpty()) return 0;

        // Update owners and append to the target file first; roll back the owner change if it fails.
        java.util.Map<CapturedCreature, String> priorOwner = new HashMap<>();
        BestiaryStore.StoreData target = store.readAccount(targetHash);
        for (CapturedCreature c : moving) {
            priorOwner.put(c, c.currentOwner);
            c.transferTo(targetRsn);
            target.captures.add(c);
        }
        if (!store.writeAccountNow(targetHash, target)) {
            for (CapturedCreature c : moving) c.currentOwner = priorOwner.get(c);
            log.warn("Card transfer aborted — could not write target account {}", targetHash);
            return 0;
        }

        for (CapturedCreature c : moving) collection.removeCapture(c);
        collection.lifetimeCardsSent += moving.size();   // lifetime "sent" tally (not a capture change)
        persistNow();
        log.info("Transferred {} card(s) to {}", moving.size(), targetRsn);
        return moving.size();
    }

    /** RSN of the active account (empty while logged out). */
    public String getActiveAccountName() {
        return activeAccountName;
    }

    /** Loads the active account's data into memory. Must be called from the client thread. */
    public void load() {
        BestiaryStore.StoreData d = store.load();
        collection = hydrateCollection(d);

        progressionState = new ProgressionService.ProgressionState();
        progressionState.totalXp = d.totalXp;
        progressionState.unlockedAchievements = parseAchievements(d.achievements);

        progressionService.init(progressionState, collection);
        log.info("Bestiary loaded: {} captures", collection.totalCaptures());
    }

    /** Flushes the current state to disk immediately. Called from UI callbacks after edits. */
    public void saveNow() {
        persistNow();
    }

    /** Debounced save. Kept for API compatibility with callers that don't need an immediate flush. */
    public void scheduleSave() {
        persist();
    }

    /** Permanently deletes all data and resets in-memory state. */
    public void wipeCollection() {
        clearView();     // reset acts on the played account — leave any view behind
        collection       = new BestiaryCollection();
        progressionState = new ProgressionService.ProgressionState();
        progressionService.init(progressionState, collection);
        persistNow();
        log.info("Bestiary collection wiped");
    }

    /** Flushes and stops the store writer. Call from plugin shutDown. */
    public void shutdown() {
        persistNow();
        store.close();
    }

    // -------------------------------------------------------------------------
    // Mutators (call from client thread)
    // -------------------------------------------------------------------------

    public void addCapture(CapturedCreature c) {
        collection.addCapture(c);
        collection.recordLifetimeCapture(c.npcName);   // genuine capture — bump the lifetime counters
        persistNow();   // a capture is rare + valuable — write immediately
    }

    public void incrementKillCount(String npcName) {
        collection.incrementKillCount(npcName);
        persist();      // high-frequency — debounce
    }

    /** Persists mutable fields of a single creature (favourite/nickname/etc. already changed in memory). */
    public void updateCapture(CapturedCreature c) {
        persist();
    }

    public ProgressionService.ProgressionState getProgressionState() {
        return progressionState;
    }

    public long getCredits() {
        return collection.credits;
    }

    public void awardCredits(long amount) {
        addCredits(amount);
        persist();
    }

    /**
     * Awards capture credits after applying the Hunter's Bounty passive boost. Returns the
     * actual credits granted (so the caller can show the real, boosted number).
     */
    public long awardCaptureCredits(long base) {
        long total = Math.max(1L, base + captureCreditFlatBonus());
        addCredits(total);
        persist();
        return total;
    }

    /** Adds credits and tracks the lifetime-earned total. */
    private void addCredits(long amount) {
        if (amount <= 0) return;
        collection.credits += amount;
        collection.lifetimeCreditsEarned += amount;
    }

    /** Deducts credits and tracks the lifetime-spent total. Caller must have checked affordability. */
    private void subtractCredits(long amount) {
        if (amount <= 0) return;
        collection.credits -= amount;
        collection.lifetimeCreditsSpent += amount;
    }

    /** Flat capture-credit bonus from the Hunter's Bounty upgrade (+2 credits per tier). */
    public long captureCreditFlatBonus() {
        return (long) com.bestiary.model.ShopUpgrade.CREDIT_CAPTURE.effectFor(
                collection.getUpgradeTier(com.bestiary.model.ShopUpgrade.CREDIT_CAPTURE));
    }

    /** Flat bonus XP added to every kill from the Hunter's Focus upgrade (+5 per tier). */
    public long killXpFlatBonus() {
        return (long) com.bestiary.model.ShopUpgrade.KILL_XP.effectFor(
                collection.getUpgradeTier(com.bestiary.model.ShopUpgrade.KILL_XP));
    }

    /** Capture-XP percentage bonus (fraction, e.g. 0.25 = +25%) from the Scholar's Insight upgrade. */
    public double captureXpBonus() {
        return com.bestiary.model.ShopUpgrade.CAPTURE_XP.effectFor(
                collection.getUpgradeTier(com.bestiary.model.ShopUpgrade.CAPTURE_XP));
    }

    /** Passive discard-credit bonus from the Salvager's Eye upgrade. */
    public double discardCreditBonus() {
        return com.bestiary.model.ShopUpgrade.CREDIT_DISCARD.effectFor(
                collection.getUpgradeTier(com.bestiary.model.ShopUpgrade.CREDIT_DISCARD));
    }

    /** Base credits a card is worth if discarded, BEFORE the Salvager's Eye passive boost. */
    public long discardValueBase(CapturedCreature c) {
        return Math.max(1L, com.bestiary.util.CreditCalculator.forDiscard(
                com.bestiary.model.MonsterRoster.getDifficulty(c.npcName, c.npcCombatLevel),
                c.rarity, c.isShiny()));
    }

    /**
     * Credits a card is worth if discarded, including the Salvager's Eye passive boost.
     * The boost always rounds UP, so even a 2-credit discard gains at least +1 with one tier —
     * giving low-rarity cards a tangible benefit from the upgrade.
     */
    public long discardValue(CapturedCreature c) {
        long base = com.bestiary.util.CreditCalculator.forDiscard(
                com.bestiary.model.MonsterRoster.getDifficulty(c.npcName, c.npcCombatLevel),
                c.rarity, c.isShiny());
        return Math.max(1L, (long) Math.ceil(base * (1.0 + discardCreditBonus())));
    }

    /**
     * Discards a card: removes it, credits its discard value, persists. Returns credits
     * awarded, or 0 if the card was already gone (guards against double-discard exploits).
     */
    public long discardCapture(CapturedCreature c) {
        if (isViewing()) return 0;                    // read-only while viewing another account
        if (!collection.removeCapture(c)) return 0;   // already discarded — award nothing
        long credits = discardValue(c);
        addCredits(credits);
        collection.lifetimeCardsDiscarded++;
        persistNow();
        return credits;
    }

    /** Discards several cards at once. Returns total credits awarded (only for cards present). */
    public long discardCaptures(java.util.Collection<CapturedCreature> cards) {
        if (isViewing()) return 0;   // read-only while viewing another account
        long total = 0;
        for (CapturedCreature c : cards) {
            if (!collection.removeCapture(c)) continue;
            total += discardValue(c);
            collection.lifetimeCardsDiscarded++;
        }
        addCredits(total);
        persistNow();
        return total;
    }

    public void saveProgressionState() {
        persist();
    }

    // -------------------------------------------------------------------------
    // Shop (POC)
    // -------------------------------------------------------------------------

    /**
     * Cost of one "Card Reroller" use, scaled by the card's difficulty tier × rarity
     * (shiny does NOT affect cost). Difficulty sets a base (= a Common card's cost) and
     * rarity multiplies it, so rerolling a high-tier rare card costs far more than a
     * beginner common. Anchors: Beginner Common 20 → Mythic 240; Boss Common 100 → Mythic 1200.
     */
    public static long rerollCost(CapturedCreature c) {
        return rerollBaseCost(MonsterRoster.getDifficulty(c.npcName, c.npcCombatLevel))
                * rerollRarityMultiplier(c.rarity);
    }

    /** Per-difficulty base cost — the price of rerolling a Common card of that tier. */
    private static long rerollBaseCost(com.bestiary.model.DifficultyTier d) {
        switch (d) {
            case BEGINNER: return 20;
            case EASY:     return 30;
            case MEDIUM:   return 45;
            case HARD:     return 65;
            case ELITE:    return 80;
            case BOSS:     return 100;
            default:       return 45;
        }
    }

    /** Rarity multiplier applied to the difficulty base (Common 1× → Mythic 12×). */
    private static long rerollRarityMultiplier(CreatureRarity r) {
        switch (r) {
            case COMMON:    return 1;
            case UNCOMMON:  return 2;
            case RARE:      return 3;
            case EPIC:      return 5;
            case LEGENDARY: return 8;
            case MYTHIC:    return 12;
            default:        return 1;
        }
    }

    /** Base chance a non-Mythic reroll bumps up one rarity (raised later by shop unlocks). */
    public static final double RARITY_UP_CHANCE = 0.05;

    private final Random rerollRng = new Random();

    /** Deducts credits if affordable; persists. Returns false if too poor. */
    public boolean spendCredits(long amount) {
        if (isViewing()) return false;   // read-only while viewing another account
        if (collection.credits < amount) return false;
        subtractCredits(amount);
        persist();
        return true;
    }

    // --- Passive shop upgrades (#39) ---

    /**
     * Tiers currently owned of a passive upgrade — for DISPLAY (dashboard/export). Reflects the
     * viewed account while browsing another profile (#48); shop purchase/bonus logic reads
     * {@code collection} directly so it always acts on the played account.
     */
    public int getUpgradeTier(com.bestiary.model.ShopUpgrade u) {
        return getCollection().getUpgradeTier(u);
    }

    /** Cost of the next tier, or -1 if the upgrade is already maxed. */
    public long upgradeCost(com.bestiary.model.ShopUpgrade u) {
        int owned = collection.getUpgradeTier(u);
        return owned >= u.maxTier ? -1 : u.costForNextTier(owned);
    }

    /**
     * Buys the next tier of a passive upgrade if it's not maxed and the player can afford it.
     * Returns true on success. Persists immediately (a purchase is rare + valuable).
     */
    public boolean purchaseUpgrade(com.bestiary.model.ShopUpgrade u) {
        if (isViewing()) return false;   // read-only while viewing another account
        int owned = collection.getUpgradeTier(u);
        if (owned >= u.maxTier) return false;
        long cost = u.costForNextTier(owned);
        if (collection.credits < cost) return false;
        subtractCredits(cost);
        collection.shopUpgrades.put(u.name(), owned + 1);
        persistNow();
        return true;
    }

    /** Passive capture shiny-chance bonus from the Shiny Charm upgrade (on top of the level-scaled base). */
    public double bonusShinyChance() {
        return com.bestiary.model.ShopUpgrade.SHINY_CHANCE.effectFor(
                collection.getUpgradeTier(com.bestiary.model.ShopUpgrade.SHINY_CHANCE));
    }

    /**
     * Shiny-chance bonus for DISPLAY (the Catch Rates screen) — reflects the viewed account while
     * browsing another profile (#48). The live capture flow uses {@link #bonusShinyChance()} so a
     * kill on the played account always applies that account's own bonus.
     */
    public double displayBonusShinyChance() {
        return com.bestiary.model.ShopUpgrade.SHINY_CHANCE.effectFor(
                getCollection().getUpgradeTier(com.bestiary.model.ShopUpgrade.SHINY_CHANCE));
    }

    /** Shiny-chance bonus applied when rerolling a card, from the Reroll Shine upgrade. */
    public double bonusRerollShinyChance() {
        return com.bestiary.model.ShopUpgrade.REROLL_SHINY.effectFor(
                collection.getUpgradeTier(com.bestiary.model.ShopUpgrade.REROLL_SHINY));
    }

    /** Passive reroll rarity-up bonus from the Reroll Fortune upgrade. */
    public double bonusRerollRarityChance() {
        return com.bestiary.model.ShopUpgrade.REROLL_RARITY.effectFor(
                collection.getUpgradeTier(com.bestiary.model.ShopUpgrade.REROLL_RARITY));
    }

    /**
     * Card Reroller (shop POC): for {@link #rerollCost} credits, re-rolls a card's stats,
     * prayer and shiny at the SAME rarity/monster (a chance to improve stats or hit shiny).
     * Keeps id + metadata (favourite/nickname/album-cover/observed HP). Returns the new
     * card, or null if the player can't afford it.
     */
    public CapturedCreature rerollCard(CapturedCreature c, int currentLevel) {
        if (isViewing()) return null;   // read-only while viewing another account
        // Guard against a stale card from a since-closed view (#131): never reroll — and never spend
        // credits or inject a foreign card via addCapture — for a card not in the played collection.
        if (!collection.containsId(c.id)) return null;
        if (!spendCredits(rerollCost(c))) return null;
        // Non-Mythic cards get a small chance to move up a rarity (raised by the Reroll Fortune shop upgrade).
        CreatureRarity rarity = c.rarity;
        if (rarity != CreatureRarity.MYTHIC
                && rerollRng.nextDouble() < RARITY_UP_CHANCE + bonusRerollRarityChance()) {
            rarity = CreatureRarity.values()[rarity.ordinal() + 1];
        }
        com.bestiary.model.CombatClass cls =
                com.bestiary.model.MonsterRoster.getCombatClass(c.npcName, c.npcCombatLevel);
        int[] bases = com.bestiary.model.MonsterRoster.getStatBases(c.npcName, c.npcCombatLevel);
        // A shiny stays shiny; a non-shiny gets a fresh shiny roll (raised by the Reroll Shine shop upgrade).
        boolean shiny = c.isShiny()
                || rerollRng.nextDouble() < CaptureService.shinyChance(currentLevel) + bonusRerollShinyChance();
        int prayerBase = com.bestiary.model.MonsterRoster.getPrayer(c.npcName);
        com.bestiary.model.CreatureQuality q =
                com.bestiary.util.RarityRoller.generateQuality(cls, rarity, bases, prayerBase, rerollRng, shiny);
        // The reroller is the account performing the reroll now (the active/current owner), NOT the
        // card's original capturer — otherwise a traded-in card credits its reroll to the wrong account.
        String reroller = activeAccountName != null && !activeAccountName.isEmpty() ? activeAccountName
                : (c.currentOwner != null && !c.currentOwner.isEmpty() ? c.currentOwner
                : (c.playerName != null && !c.playerName.isEmpty() ? c.playerName : "Player"));
        // Log the card's pre-reroll state, then carry the whole history forward onto the new card.
        java.util.List<CapturedCreature.RerollState> history = new java.util.ArrayList<>(c.rerollHistory);
        history.add(new CapturedCreature.RerollState(
                c.rarity, c.quality, c.powerLevel(), c.isShiny(), reroller, java.time.Instant.now().getEpochSecond()));
        CapturedCreature nc = CapturedCreature.builder()
                .id(c.id).npcId(c.npcId).npcName(c.npcName).npcCombatLevel(c.npcCombatLevel)
                .rarity(rarity).quality(q).captureTime(c.captureTime).regionName(c.regionName)
                .captureLevel(currentLevel)   // reroll happened now → odds reflect the current level
                .killsBeforeCapture(c.killsBeforeCapture)
                .playerName(c.playerName).originalOwner(c.originalOwner).currentOwner(c.currentOwner)
                .shiny(shiny).observedHp(c.observedHp)
                .shinyBonus(bonusRerollShinyChance())   // reroll re-rolled shiny with the current reroll bonus
                .rerolledBy(reroller)
                .rerollHistory(history)
                .build();
        nc.favourite     = c.favourite;
        nc.nickname      = c.nickname;
        nc.albumCover    = c.albumCover;
        nc.creditsEarned = c.creditsEarned;   // a reroll doesn't re-award capture credits — carry the original
        nc.xpEarned      = c.xpEarned;        // nor capture XP — carry the original
        // Replace in place (by id) so it can't leave a stale/duplicate copy behind.
        if (!collection.replaceCapture(nc)) collection.addCapture(nc);
        persistNow();
        return nc;
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    /** Debounced snapshot write (coalesces high-frequency mutations). */
    private void persist() {
        store.save(snapshot());
    }

    /** Immediate snapshot write. */
    private void persistNow() {
        store.saveNow(snapshot());
    }

    /** Builds a cheap shallow-copy snapshot so the writer thread never races the collection. */
    private BestiaryStore.StoreData snapshot() {
        BestiaryStore.StoreData d = new BestiaryStore.StoreData();
        d.version     = BestiaryStore.VERSION;
        d.captures    = new ArrayList<>(collection.creatures);
        d.killCounts  = new LinkedHashMap<>(collection.killCounts);
        d.credits     = collection.credits;
        d.lifetimeCreditsEarned = collection.lifetimeCreditsEarned;
        d.lifetimeCreditsSpent  = collection.lifetimeCreditsSpent;
        d.lifetimeCaptures      = collection.lifetimeCaptures;
        d.lifetimeCapturesByNpc = new LinkedHashMap<>(collection.lifetimeCapturesByNpc);
        d.lifetimeCardsSent     = collection.lifetimeCardsSent;
        d.lifetimeCardsDiscarded = collection.lifetimeCardsDiscarded;
        d.shopUpgrades = new LinkedHashMap<>(collection.shopUpgrades);
        d.totalXp     = progressionState.totalXp;
        d.achievements = progressionState.unlockedAchievements.stream()
                .map(Enum::name).collect(Collectors.toList());
        return d;
    }

    private EnumSet<Achievement> parseAchievements(List<String> names) {
        EnumSet<Achievement> set = EnumSet.noneOf(Achievement.class);
        if (names != null) {
            for (String name : names) {
                try {
                    set.add(Achievement.valueOf(name.trim()));
                } catch (IllegalArgumentException ignored) {
                    log.warn("Unknown achievement in store: {}", name);
                }
            }
        }
        return set;
    }
}
