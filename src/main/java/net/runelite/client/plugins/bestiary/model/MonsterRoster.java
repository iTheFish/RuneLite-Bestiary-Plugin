package net.runelite.client.plugins.bestiary.model;

import java.util.*;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.bestiary.model.DifficultyTier.*;
import static net.runelite.client.plugins.bestiary.model.CombatClass.*;
import static net.runelite.client.plugins.bestiary.model.CreatureSpecies.*;

/**
 * Canonical list of known OSRS NPC names and their difficulty ratings.
 * Names must match what npc.getName() returns in-game.
 * Kill-count names are added dynamically at runtime; this list is the seed.
 *
 * NOTE: Both the roster and the difficulty map need ongoing review as new
 *       content is added to the game.
 */
public class MonsterRoster {

    // -------------------------------------------------------------------------
    // Static roster
    // -------------------------------------------------------------------------

    public static final List<String> ROSTER = Arrays.asList(
        // === F2P / Early game ===
        "Chicken", "Cow", "Cow calf", "Duck", "Ram", "Seagull",
        "Man", "Woman", "Farmer",
        "Goblin",
        "Guard",
        "Rat", "Giant rat",
        "Imp",
        "Zombie", "Skeleton", "Ghost",
        "Barbarian", "Warrior", "Wizard", "Dark wizard",
        "Minotaur",
        "Bear", "Grizzly bear",
        "Unicorn",
        "Spider", "Giant spider",
        "Scorpion", "King scorpion",
        "Hill giant", "Moss giant", "Fire giant", "Ice giant",
        "Earth warrior",
        "Lesser demon", "Greater demon", "Black demon",
        "Black knight", "White knight",
        "Hobgoblin",
        "Chaos druid",
        "Rock crab", "Sand crab", "Swamp crab", "Gemstone crab",
        "Pirate", "Rogue",

        // === Slayer monsters ===
        "Abyssal demon",
        "Aberrant spectre", "Deviant spectre",
        "Ankou",
        "Banshee", "Twisted banshee",
        "Basilisk", "Basilisk knight",
        "Bloodveld", "Mutated bloodveld",
        "Cave bug", "Cave crawler", "Cave slime", "Cave horror",
        "Dagannoth",
        "Dark beast",
        "Desert lizard",
        "Drake",
        "Dust devil",
        "Fever spider",
        "Fleshcrawler",
        "Gargoyle",
        "Hellhound",
        "Hydra",
        "Ice troll", "Mountain troll", "Troll",
        "Infernal mage",
        "Jelly", "Warped jelly",
        "Kalphite", "Kalphite soldier", "Kalphite guardian", "Kalphite worker",
        "Kurask",
        "Lizardman", "Lizardman brute", "Lizardman shaman",
        "Mogre",
        "Nechryael", "Greater nechryael",
        "Pyrefiend",
        "Rockslugs",
        "Smoke devil",
        "Spiritual warrior", "Spiritual mage", "Spiritual ranger",
        "Suqah",
        "Turoth",
        "Vampyre", "Feral vampyre", "Vyrewatch",
        "Waterfiend",
        "Wyrm",
        "Wyvern", "Ancient wyvern", "Skeletal wyvern", "Fossil island wyvern",
        "Warped tortoise", "Tortoise",
        "Zombie pirate",

        // === Dragons ===
        "Baby blue dragon", "Baby green dragon",
        "Green dragon", "Blue dragon", "Red dragon", "Black dragon", "Lava dragon",
        "Bronze dragon", "Iron dragon", "Steel dragon",
        "Mithril dragon", "Adamant dragon", "Rune dragon",
        "Brutal black dragon", "Brutal red dragon", "Brutal blue dragon", "Brutal green dragon",

        // === Wilderness / other ===
        "Chaos druid warrior", "Dark warrior",
        "Ice warrior", "Ice spider",

        // === Solo bosses ===
        "Scurrius",
        "Giant Mole",
        "King Black Dragon",
        "Chaos Elemental",
        "Chaos Fanatic",
        "Crazy Archaeologist",
        "Scorpia",
        "Deranged Archaeologist",
        "Sarachnis",
        "Hespori",
        "Obor",
        "Bryophyta",
        "Cerberus",
        "Kraken",
        "Thermonuclear smoke devil",
        "Alchemical Hydra",
        "Zulrah",
        "Vorkath",
        "Phantom Muspah",
        "Duke Sucellus",
        "The Leviathan",
        "Vardorvis",
        "The Whisperer",
        "Nex",
        "TzTok-Jad",
        "TzKal-Zuk",
        "Araxxor",
        "Hueycoatl",
        "Sol Heredit",
        "Amoxliatl",

        // === Wilderness bosses ===
        "Callisto", "Artio",
        "Venenatis", "Spindel",
        "Vet'ion", "Calvar'ion",
        "Corporeal Beast",

        // === GWD ===
        "Commander Zilyana",
        "General Graardor",
        "K'ril Tsutsaroth",
        "Kree'arra",

        // === Dagannoth Kings ===
        "Dagannoth Rex", "Dagannoth Prime", "Dagannoth Supreme",

        // === Grotesque Guardians ===
        "Dusk", "Dawn",

        // === Nightmare ===
        "The Nightmare", "Phosani's Nightmare",

        // === Barrows ===
        "Ahrim the Blighted", "Dharok the Wretched", "Guthan the Infested",
        "Karil the Tainted",  "Torag the Corrupted",  "Verac the Defiled",

        // === CoX bosses ===
        "Tekton", "Great Olm", "Vespula",

        // === ToB bosses ===
        "Maiden of Sugadinti", "Pestilent Bloat", "Sotetseg",
        "Xarpus", "Verzik Vitur",

        // === ToA bosses ===
        "Akkha", "Ba-Ba", "Kephri", "Zebak",
        "Tumeken's Warden", "Elidinis' Warden",

        // === Abyssal Sire ===
        "Abyssal Sire"
    );

    // -------------------------------------------------------------------------
    // Difficulty map — assign every named monster a difficulty tier.
    // Monsters not listed here fall back to fromCombatLevel().
    // -------------------------------------------------------------------------

    private static final Map<String, DifficultyTier> DIFFICULTY;
    static {
        Map<String, DifficultyTier> d = new HashMap<>();

        // Beginner — trivially easy, basically AFK
        for (String n : Arrays.asList(
            "Chicken", "Cow", "Cow calf", "Duck", "Ram", "Seagull", "Man", "Woman", "Farmer",
            "Goblin", "Rat", "Giant rat", "Imp", "Unicorn", "Spider",
            "Bear", "Grizzly bear",
            "Dark wizard", "Minotaur",
            "Cave bug",
            "Rock crab", "Sand crab"
        )) { d.put(n, BEGINNER); }

        // Easy — early/mid F2P, basic slayer
        for (String n : Arrays.asList(
            "Guard", "Barbarian", "Warrior", "Wizard",
            "Giant spider", "Scorpion", "King scorpion",
            "Zombie", "Skeleton", "Ghost", "Pirate", "Rogue",
            "Swamp crab",
            "Hobgoblin", "Chaos druid",
            "Cave crawler", "Cave slime",
            "Desert lizard", "Fever spider", "Pyrefiend", "Rockslugs",
            "Fleshcrawler", "Ice warrior", "Ice spider",
            "Hill giant", "Moss giant", "Ice giant", "Earth warrior",
            "Baby blue dragon", "Baby green dragon",
            "Banshee", "Basilisk", "Black knight"
        )) { d.put(n, EASY); }

        // Medium — mid-game slayer, accessible dungeon monsters
        for (String n : Arrays.asList(
            "Fire giant",
            "Lesser demon", "White knight",
            "Ankou",
            "Twisted banshee", "Bloodveld",
            "Cave horror", "Jelly", "Warped jelly",
            "Kalphite", "Kalphite soldier", "Kalphite worker",
            "Turoth", "Nechryael",
            "Blue dragon", "Red dragon", "Green dragon", "Bronze dragon", "Iron dragon",
            "Spiritual warrior", "Spiritual mage", "Spiritual ranger",
            "Lizardman", "Lizardman brute",
            "Mogre",
            "Vampyre", "Feral vampyre", "Vyrewatch",
            "Smoke devil", "Dust devil", "Infernal mage",
            "Zombie pirate",
            "Tortoise", "Warped tortoise",
            "Troll", "Ice troll", "Mountain troll",
            "Dagannoth"
        )) { d.put(n, MEDIUM); }

        // Hard — high slayer level, requires solid stats/gear
        for (String n : Arrays.asList(
            "Greater demon", "Black demon", "Abyssal demon",
            "Gargoyle", "Hellhound", "Dark beast",
            "Greater nechryael", "Mutated bloodveld",
            "Kalphite guardian",
            "Basilisk knight",
            "Kurask",
            "Steel dragon", "Mithril dragon", "Black dragon",
            "Wyrm", "Drake", "Wyvern", "Fossil island wyvern",
            "Ancient wyvern", "Skeletal wyvern",
            "Waterfiend", "Suqah",
            "Aberrant spectre", "Deviant spectre",
            "Lizardman shaman",
            "Brutal black dragon", "Brutal red dragon",
            "Brutal blue dragon", "Brutal green dragon",
            "Lava dragon", "Dark warrior", "Chaos druid warrior"
        )) { d.put(n, HARD); }

        // Elite — challenging late-game PvM (pre-boss tier)
        for (String n : Arrays.asList(
            "Adamant dragon", "Rune dragon",
            "Hydra",
            "Kraken"
        )) { d.put(n, ELITE); }

        // Boss — endgame encounters requiring preparation/skill
        for (String n : Arrays.asList(
            "Scurrius", "Gemstone crab",
            "Alchemical Hydra",
            "Giant Mole",
            "Sarachnis", "Hespori", "Obor", "Bryophyta",
            "Cerberus", "Thermonuclear smoke devil",
            "Abyssal Sire",
            "Deranged Archaeologist",
            "Chaos Fanatic", "Crazy Archaeologist", "Scorpia",
            "Callisto", "Artio",
            "Venenatis", "Spindel",
            "Vet'ion", "Calvar'ion",
            "King Black Dragon", "Chaos Elemental", "Corporeal Beast",
            "Commander Zilyana", "General Graardor", "K'ril Tsutsaroth", "Kree'arra",
            "Dagannoth Rex", "Dagannoth Prime", "Dagannoth Supreme",
            "Dusk", "Dawn",
            "Zulrah", "Vorkath",
            "The Nightmare", "Phosani's Nightmare",
            "Nex",
            "Phantom Muspah",
            "Duke Sucellus", "The Leviathan", "Vardorvis", "The Whisperer",
            "TzTok-Jad", "TzKal-Zuk",
            "Araxxor", "Hueycoatl", "Sol Heredit", "Amoxliatl",
            "Ahrim the Blighted", "Dharok the Wretched", "Guthan the Infested",
            "Karil the Tainted",  "Torag the Corrupted",  "Verac the Defiled",
            "Tekton", "Great Olm", "Vespula",
            "Maiden of Sugadinti", "Pestilent Bloat", "Sotetseg",
            "Xarpus", "Verzik Vitur",
            "Akkha", "Ba-Ba", "Kephri", "Zebak",
            "Tumeken's Warden", "Elidinis' Warden"
        )) { d.put(n, BOSS); }

        DIFFICULTY = Collections.unmodifiableMap(d);
    }

    // -------------------------------------------------------------------------
    // Combat class map — determines which stats are "primary" for each monster.
    // Monsters not listed fall back to classFromTier().
    // -------------------------------------------------------------------------

    private static final Map<String, CombatClass> COMBAT_CLASSES;
    static {
        Map<String, CombatClass> a = new HashMap<>();

        // NIMBLE — SPD, STL
        for (String n : Arrays.asList(
            "Rat", "Giant rat", "Chicken", "Duck", "Seagull", "Imp", "Goblin",
            "Spider", "Giant spider", "Ice spider", "Fever spider",
            "Cave bug", "Cave crawler", "Fleshcrawler",
            "Desert lizard",
            "Rockslugs", "Turoth",
            "Sarachnis"
        )) { a.put(n, NIMBLE); }

        // BRUTE — STR, END
        for (String n : Arrays.asList(
            "Grizzly bear",
            "Barbarian", "Warrior", "Guard", "Black knight", "White knight",
            "Zombie", "Skeleton",
            "Hobgoblin", "Minotaur",
            "Troll", "Ice troll", "Mountain troll",
            "Hill giant", "Moss giant", "Ice giant", "Fire giant",
            "Earth warrior",
            "Lizardman", "Lizardman brute",
            "Dagannoth",
            "Zombie pirate",
            "Man", "Woman", "Farmer",
            "Rogue", "Pirate", "Dark warrior",
            "Ankou",
            "Obor", "Bryophyta"
        )) { a.put(n, BRUTE); }

        // TANK — END, VIT
        for (String n : Arrays.asList(
            "Rock crab", "Sand crab", "Swamp crab", "Gemstone crab",
            "Bear", "Cave slime",
            "Gargoyle",
            "Kalphite", "Kalphite soldier", "Kalphite worker", "Kalphite guardian",
            "Basilisk", "Basilisk knight",
            "Jelly", "Warped jelly",
            "Tortoise", "Warped tortoise",
            "Scorpion", "King scorpion",
            "Kurask",
            "Pyrefiend", "Nechryael", "Greater nechryael",
            "Cow", "Unicorn",
            "Bloodveld",
            "Spiritual warrior",
            "Giant Mole",
            "Dusk", "Dawn"
        )) { a.put(n, TANK); }

        // PREDATOR — STR, SPD
        for (String n : Arrays.asList(
            "Baby blue dragon", "Baby green dragon",
            "Green dragon", "Blue dragon", "Red dragon", "Black dragon", "Lava dragon",
            "Bronze dragon", "Iron dragon", "Steel dragon", "Mithril dragon",
            "Adamant dragon", "Rune dragon",
            "Brutal black dragon", "Brutal red dragon", "Brutal blue dragon", "Brutal green dragon",
            "Hellhound",
            "Wyrm", "Drake", "Wyvern", "Fossil island wyvern", "Ancient wyvern", "Skeletal wyvern",
            "Abyssal demon", "Black demon", "Greater demon",
            "Waterfiend",
            "Aberrant spectre", "Deviant spectre",
            "King Black Dragon", "Vorkath", "Zulrah",
            "Araxxor", "Hueycoatl",
            "Scorpia", "Callisto", "Venenatis", "Vet'ion",
            "Alchemical Hydra",
            "Phantom Muspah", "Vardorvis", "The Whisperer",
            "TzTok-Jad"
        )) { a.put(n, PREDATOR); }

        // MYSTIC — INT, VIT
        for (String n : Arrays.asList(
            "Wizard", "Dark wizard",
            "Ghost",
            "Banshee", "Twisted banshee",
            "Chaos druid", "Chaos druid warrior",
            "Infernal mage",
            "Spiritual mage",
            "Mogre",
            "Abyssal Sire",
            "The Nightmare", "Phosani's Nightmare",
            "Nex",
            "Duke Sucellus",
            "Chaos Elemental", "Chaos Fanatic", "Crazy Archaeologist", "Deranged Archaeologist",
            "Hespori",
            "Kraken", "Thermonuclear smoke devil"
        )) { a.put(n, MYSTIC); }

        // STALKER — INT, STL
        for (String n : Arrays.asList(
            "Smoke devil", "Dust devil",
            "Mutated bloodveld",
            "Cave horror",
            "Suqah",
            "Dark beast",
            "Lizardman shaman",
            "Hydra",
            "Cerberus",
            "Scurrius",
            "The Leviathan",
            "Vampyre", "Feral vampyre", "Vyrewatch",
            "Spiritual ranger",
            "Artio", "Spindel", "Calvar'ion",
            "Amoxliatl"
        )) { a.put(n, STALKER); }

        // TITAN — STR, VIT
        for (String n : Arrays.asList(
            "Lesser demon",
            "Commander Zilyana", "General Graardor", "K'ril Tsutsaroth", "Kree'arra",
            "Dagannoth Rex", "Dagannoth Prime", "Dagannoth Supreme",
            "Ahrim the Blighted", "Dharok the Wretched", "Guthan the Infested",
            "Karil the Tainted", "Torag the Corrupted", "Verac the Defiled",
            "Tekton", "Maiden of Sugadinti", "Pestilent Bloat", "Sotetseg",
            "Xarpus",
            "Akkha", "Ba-Ba", "Kephri", "Zebak",
            "Vespula",
            "Corporeal Beast"
        )) { a.put(n, TITAN); }

        // APEX — STR, SPD, INT (3 primaries — hardest endgame content)
        for (String n : Arrays.asList(
            "Nex",
            "Duke Sucellus",
            "The Leviathan",
            "Sol Heredit",
            "TzKal-Zuk",
            "Verzik Vitur",
            "Great Olm",
            "Tumeken's Warden", "Elidinis' Warden"
        )) { a.put(n, APEX); }

        COMBAT_CLASSES = Collections.unmodifiableMap(a);
    }

    // -------------------------------------------------------------------------
    // Species map — biological/lore type of each monster.
    // Monsters not listed fall back to OTHER.
    // -------------------------------------------------------------------------

    private static final Map<String, CreatureSpecies> SPECIES;
    static {
        Map<String, CreatureSpecies> s = new HashMap<>();

        // ANIMAL — real-world creature analogues
        for (String n : Arrays.asList(
            "Chicken", "Cow", "Cow calf", "Duck", "Ram", "Seagull",
            "Rat", "Giant rat", "Bear", "Grizzly bear", "Unicorn",
            "Rock crab", "Sand crab", "Swamp crab", "Gemstone crab",
            "Desert lizard", "Warped tortoise", "Tortoise",
            "Giant Mole", "Callisto", "Artio", "Scurrius", "Kraken",
            "Ba-Ba", "Zebak", "Amoxliatl"
        )) { s.put(n, ANIMAL); }

        // DEMON — creatures of demonic or infernal origin
        for (String n : Arrays.asList(
            "Imp", "Lesser demon", "Greater demon", "Black demon", "Abyssal demon",
            "Bloodveld", "Mutated bloodveld", "Pyrefiend", "Waterfiend",
            "Dust devil", "Smoke devil", "Thermonuclear smoke devil",
            "Hellhound", "Nechryael", "Greater nechryael",
            "Cerberus", "Abyssal Sire", "K'ril Tsutsaroth",
            "Infernal mage", "Duke Sucellus", "Sotetseg"
        )) { s.put(n, DEMON); }

        // DRAGON — true dragons and dragon-kind
        for (String n : Arrays.asList(
            "Baby blue dragon", "Baby green dragon",
            "Green dragon", "Blue dragon", "Red dragon", "Black dragon", "Lava dragon",
            "Bronze dragon", "Iron dragon", "Steel dragon",
            "Mithril dragon", "Adamant dragon", "Rune dragon",
            "Brutal black dragon", "Brutal red dragon", "Brutal blue dragon", "Brutal green dragon",
            "King Black Dragon", "Vorkath", "Great Olm"
        )) { s.put(n, DRAGON); }

        // GIANT — giants and giant-kin
        for (String n : Arrays.asList(
            "Hill giant", "Moss giant", "Fire giant", "Ice giant",
            "Obor", "Tekton", "General Graardor"
        )) { s.put(n, GIANT); }

        // GOBLINOID — goblins and goblin-like creatures
        for (String n : Arrays.asList(
            "Goblin", "Hobgoblin"
        )) { s.put(n, GOBLINOID); }

        // HUMAN — humanoid mortals (NPCs and human-variant monsters)
        for (String n : Arrays.asList(
            "Man", "Woman", "Farmer",
            "Guard", "Barbarian", "Warrior", "Wizard", "Dark wizard",
            "Black knight", "White knight",
            "Pirate", "Rogue",
            "Chaos druid", "Chaos druid warrior", "Dark warrior",
            "Ice warrior",
            "Crazy Archaeologist", "Deranged Archaeologist", "Chaos Fanatic",
            "Sol Heredit"
        )) { s.put(n, HUMAN); }

        // INSECT — insects, spiders, scorpions, and arthropod-type creatures
        for (String n : Arrays.asList(
            "Spider", "Giant spider", "Ice spider", "Fever spider",
            "Cave bug", "Cave crawler", "Cave slime", "Fleshcrawler",
            "Scorpion", "King scorpion", "Scorpia",
            "Araxxor", "Venenatis", "Spindel", "Sarachnis",
            "Vespula", "Kephri"
        )) { s.put(n, INSECT); }

        // KALPHITE — kalphite species
        for (String n : Arrays.asList(
            "Kalphite", "Kalphite soldier", "Kalphite worker", "Kalphite guardian"
        )) { s.put(n, KALPHITE); }

        // TROLL — trolls
        for (String n : Arrays.asList(
            "Troll", "Ice troll", "Mountain troll"
        )) { s.put(n, TROLL); }

        // UNDEAD — reanimated or spectral creatures
        for (String n : Arrays.asList(
            "Zombie", "Skeleton", "Ghost", "Zombie pirate",
            "Ankou", "Banshee", "Twisted banshee",
            "Aberrant spectre", "Deviant spectre",
            "Vampyre", "Feral vampyre", "Vyrewatch",
            "Spiritual warrior", "Spiritual mage", "Spiritual ranger",
            "Vet'ion", "Calvar'ion",
            "Ahrim the Blighted", "Dharok the Wretched", "Guthan the Infested",
            "Karil the Tainted", "Torag the Corrupted", "Verac the Defiled",
            "Maiden of Sugadinti", "Verzik Vitur", "Xarpus",
            "Akkha", "Vardorvis"
        )) { s.put(n, UNDEAD); }

        // WYRM — reptilian magical creatures (hydras, wyverns, wyrms)
        for (String n : Arrays.asList(
            "Wyrm", "Drake", "Hydra", "Alchemical Hydra",
            "Wyvern", "Ancient wyvern", "Skeletal wyvern", "Fossil island wyvern"
        )) { s.put(n, WYRM); }

        // All unlisted monsters fall back to OTHER via getSpecies()
        SPECIES = Collections.unmodifiableMap(s);
    }

    // -------------------------------------------------------------------------
    // Public helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the difficulty tier for a given NPC. Checks the static map first;
     * falls back to a combat-level heuristic for unlisted monsters.
     */
    public static DifficultyTier getDifficulty(String npcName, int combatLevel) {
        DifficultyTier tier = DIFFICULTY.get(npcName);
        return tier != null ? tier : fromCombatLevel(combatLevel);
    }

    /** Returns the combat class for a given NPC, falling back to tier-based default. */
    public static CombatClass getCombatClass(String npcName, int combatLevel) {
        CombatClass cls = COMBAT_CLASSES.get(npcName);
        return cls != null ? cls : classFromTier(getDifficulty(npcName, combatLevel));
    }

    /** Returns the biological species for a given NPC. Falls back to OTHER for unlisted monsters. */
    public static CreatureSpecies getSpecies(String npcName, int combatLevel) {
        CreatureSpecies species = SPECIES.get(npcName);
        return species != null ? species : CreatureSpecies.OTHER;
    }

    private static CombatClass classFromTier(DifficultyTier tier) {
        switch (tier) {
            case BOSS:  return CombatClass.APEX;
            case ELITE: return CombatClass.TITAN;
            case HARD:  return CombatClass.PREDATOR;
            default:    return CombatClass.BRUTE;
        }
    }

    /**
     * Derives a difficulty tier purely from combat level.
     * Used for monsters not present in the static difficulty map.
     */
    public static DifficultyTier fromCombatLevel(int combatLevel) {
        if (combatLevel <= 0)   return MEDIUM;   // non-combat or unknown
        if (combatLevel <= 15)  return BEGINNER;
        if (combatLevel <= 60)  return EASY;
        if (combatLevel <= 120) return MEDIUM;
        if (combatLevel <= 200) return HARD;
        if (combatLevel <= 400) return ELITE;
        return BOSS;
    }

    /**
     * Merges the static roster with the player's kill-count keys, deduplicating
     * case-insensitively (kill-count name wins as it's the exact game string).
     */
    public static List<String> buildFullRoster(Map<String, Integer> killCounts) {
        Map<String, String> deduped = new LinkedHashMap<>();
        for (String name : ROSTER) {
            deduped.putIfAbsent(name.toLowerCase(), name);
        }
        for (String name : killCounts.keySet()) {
            deduped.put(name.toLowerCase(), name);
        }
        return deduped.values().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    /** Stable dex numbers from the static roster alone (no kill-count entries). */
    private static final Map<String, Integer> STATIC_DEX =
            assignDexNumbers(buildFullRoster(Collections.emptyMap()));

    /** Returns the dex number for a monster in the static roster, or 0 if unlisted. */
    public static int getDexNumber(String npcName) {
        Integer num = STATIC_DEX.get(npcName);
        if (num != null) return num;
        // Case-insensitive fallback: in-game NPC names may differ in casing from roster
        for (Map.Entry<String, Integer> e : STATIC_DEX.entrySet()) {
            if (e.getKey().equalsIgnoreCase(npcName)) return e.getValue();
        }
        return 0;
    }

    // -------------------------------------------------------------------------
    // Stat floor — used by RarityRoller to lift secondary/tertiary stats for
    // harder monsters so high-tier bosses always feel impressive.
    // -------------------------------------------------------------------------

    private static final java.util.Set<String> ACCESSIBLE_BOSSES = new java.util.HashSet<>(Arrays.asList(
        "Obor", "Bryophyta", "Giant Mole", "Hespori", "Scurrius", "Sarachnis", "Gemstone crab",
        "Chaos Fanatic", "Crazy Archaeologist", "Deranged Archaeologist", "Scorpia",
        "Ahrim the Blighted", "Dharok the Wretched", "Guthan the Infested",
        "Karil the Tainted", "Torag the Corrupted", "Verac the Defiled",
        "Dusk", "Dawn"
    ));

    private static final java.util.Set<String> ENDGAME_BOSSES = new java.util.HashSet<>(Arrays.asList(
        "TzTok-Jad", "TzKal-Zuk", "Nex",
        "Duke Sucellus", "The Leviathan", "Vardorvis", "The Whisperer",
        "Araxxor", "Hueycoatl", "Sol Heredit", "Amoxliatl",
        "Tekton", "Great Olm", "Vespula",
        "Maiden of Sugadinti", "Pestilent Bloat", "Sotetseg", "Xarpus", "Verzik Vitur",
        "Akkha", "Ba-Ba", "Kephri", "Zebak", "Tumeken's Warden", "Elidinis' Warden"
    ));

    /**
     * Returns the stat floor used by RarityRoller when generating quality stats.
     * Higher floors mean even secondary/tertiary stats are elevated for hard monsters.
     */
    public static int getStatFloor(String npcName, int combatLevel) {
        DifficultyTier tier = getDifficulty(npcName, combatLevel);
        switch (tier) {
            case BEGINNER: return 5;
            case EASY:     return 12;
            case MEDIUM:   return 22;
            case HARD:     return 33;
            case ELITE:    return 44;
            case BOSS:
                if (ENDGAME_BOSSES.contains(npcName))    return 72;
                if (ACCESSIBLE_BOSSES.contains(npcName)) return 52;
                return 62; // mid-tier boss default
            default:       return 22;
        }
    }

    /** Assigns stable alphabetical dex numbers to the full roster. */
    public static Map<String, Integer> assignDexNumbers(List<String> fullRoster) {
        Map<String, Integer> nums = new LinkedHashMap<>();
        for (int i = 0; i < fullRoster.size(); i++) {
            nums.put(fullRoster.get(i), i + 1);
        }
        return nums;
    }
}
