package com.bestiary.model;

import java.util.*;
import java.util.stream.Collectors;

import static com.bestiary.model.DifficultyTier.*;
import static com.bestiary.model.CombatClass.*;
import static com.bestiary.model.CreatureSpecies.*;

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
        "Scorpion",
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
        "Kalphite soldier", "Kalphite guardian", "Kalphite worker",
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
        "Vampyre", "Vyrewatch",
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
        "Brutal black dragon", "Brutal red dragon", "Brutal blue dragon", "Brutal green dragon","Abyssal Sire",

        // === Wilderness / other ===
        "Dark warrior",
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
            "Rock crab", "Sand crab",
            "Barbarian", "Banshee",
            "Baby blue dragon", "Baby green dragon"
        )) { d.put(n, BEGINNER); }

        // Easy — early/mid F2P, basic slayer
        for (String n : Arrays.asList(
            "Guard", "Warrior", "Wizard",
            "Giant spider", "Scorpion",
            "Zombie", "Skeleton", "Ghost", "Pirate", "Rogue",
            "Swamp crab",
            "Hobgoblin", "Chaos druid",
            "Cave crawler", "Cave slime",
            "Fever spider", "Pyrefiend", "Rockslugs",
            "Fleshcrawler", "Ice warrior", "Ice spider",
            "Hill giant", "Moss giant", "Ice giant", "Earth warrior",
            "Ankou",
            "Basilisk", "Black knight"
        )) { d.put(n, EASY); }

        // Medium — mid-game slayer, accessible dungeon monsters
        for (String n : Arrays.asList(
            "Fire giant",
            "Lesser demon", "White knight",
            "Twisted banshee", "Bloodveld",
            "Cave horror", "Jelly", "Warped jelly",
            "Kalphite soldier", "Kalphite worker",
            "Turoth", "Nechryael",
            "Blue dragon", "Red dragon", "Green dragon", "Bronze dragon", "Iron dragon",
            "Spiritual warrior", "Spiritual mage", "Spiritual ranger",
            "Lizardman", "Lizardman brute",
            "Mogre",
            "Vampyre", "Vyrewatch",
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
            "Lava dragon", "Dark warrior"
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

        // Attack-style x weight taxonomy (#70). AGI/Prayer are per-monster (STAT_BASES / PRAYER).

        // WARRIOR - pure melee
        for (String n : Arrays.asList(
            "Chicken","Cow","Cow calf","Duck","Ram","Seagull","Man","Woman","Farmer",
            "Goblin","Guard","Rat","Giant rat","Imp","Zombie","Skeleton","Ghost",
            "Barbarian","Warrior","Minotaur","Bear","Grizzly bear","Unicorn","Spider",
            "Giant spider","Scorpion","Hill giant","Moss giant","Earth warrior",
            "Black knight","White knight","Hobgoblin","Chaos druid","Chaos druid warrior",
            "Pirate","Rogue","Ankou","Spiritual warrior","Lizardman","Lizardman brute",
            "Turoth","Mogre","Kalphite","Kalphite soldier","Kalphite worker","Ice warrior",
            "Dark warrior","Ice spider","Cave bug","Cave crawler","Fever spider","Fleshcrawler",
            "Rockslugs","Kurask","Dust devil","Nechryael","Greater nechryael","Abyssal demon",
            "Vampyre","Feral vampyre","Dagannoth","Cave horror","Vyrewatch","Hellhound",
            "Troll","Mountain troll","Ice troll","Zombie pirate","Dharok the Wretched",
            "Guthan the Infested","Verac the Defiled","Baby blue dragon","Baby green dragon",
            "Scorpia","Ba-Ba","Suqah","Desert lizard","King scorpion"
        )) { a.put(n, WARRIOR); }

        // JUGGERNAUT - melee + high defence (tanky)
        for (String n : Arrays.asList(
            "Lesser demon","Greater demon","Black demon","Fire giant","Ice giant","Gargoyle","Basilisk",
            "Basilisk knight","Kalphite guardian","Rock crab","Sand crab","Swamp crab",
            "Gemstone crab","Tortoise","Warped tortoise","Bloodveld","Mutated bloodveld",
            "Torag the Corrupted","Giant Mole","Callisto","Artio","Corporeal Beast",
            "General Graardor","K'ril Tsutsaroth","Dagannoth Rex","Tekton","Duke Sucellus",
            "Vardorvis"
        )) { a.put(n, JUGGERNAUT); }

        // MAGE - pure magic
        for (String n : Arrays.asList(
            "Wizard","Dark wizard","Banshee","Twisted banshee","Infernal mage","Spiritual mage",
            "Pyrefiend","Cave slime","Aberrant spectre","Deviant spectre","Chaos Fanatic",
            "Kraken","Amoxliatl","Dagannoth Prime","Ahrim the Blighted"
        )) { a.put(n, MAGE); }

        // MARKSMAN - pure ranged
        for (String n : Arrays.asList(
            "Spiritual ranger","Crazy Archaeologist","Deranged Archaeologist","Smoke devil",
            "Dagannoth Supreme","Xarpus","Vespula","Karil the Tainted","TzKal-Zuk"
        )) { a.put(n, MARKSMAN); }

        // BATTLEMAGE - melee + magic
        for (String n : Arrays.asList(
            "King Black Dragon","Bryophyta","Vet'ion","Calvar'ion",
            "Commander Zilyana","Venenatis","Spindel","Drake","Wyrm","Dark beast",
            "Jelly","Warped jelly","Green dragon","Blue dragon","Red dragon","Black dragon",
            "Lava dragon","Bronze dragon","Iron dragon","Steel dragon","Brutal black dragon",
            "Brutal red dragon","Brutal blue dragon","Brutal green dragon"
        )) { a.put(n, BATTLEMAGE); }

        // WARDEN - melee + ranged
        for (String n : Arrays.asList(
            "Sarachnis","Obor","Araxxor"
        )) { a.put(n, WARDEN); }

        // OCCULTIST - magic + ranged
        for (String n : Arrays.asList(
            "Hespori","Thermonuclear smoke devil","Alchemical Hydra","Zulrah","The Whisperer",
            "Kree'arra","Maiden of Sugadinti","Kephri","Hydra","Waterfiend"
        )) { a.put(n, OCCULTIST); }

        // APEX - tribrid (all styles)
        for (String n : Arrays.asList(
            "Scurrius","Chaos Elemental","Cerberus","Vorkath","Phantom Muspah","The Leviathan",
            "Nex","TzTok-Jad","Hueycoatl","Sol Heredit","Dusk","Dawn","The Nightmare",
            "Phosani's Nightmare","Pestilent Bloat","Sotetseg","Verzik Vitur","Akkha","Zebak",
            "Tumeken's Warden","Elidinis' Warden","Great Olm","Lizardman shaman","Wyvern",
            "Ancient wyvern","Skeletal wyvern","Fossil island wyvern","Mithril dragon",
            "Adamant dragon","Rune dragon"
        )) { a.put(n, APEX); }

        COMBAT_CLASSES = Collections.unmodifiableMap(a);
    }

    // -------------------------------------------------------------------------
    // Per-monster stat bases — actual OSRS stats scaled to 1-90.
    // Formula: max(1, min(90, round(osrsStat * 90.0 / 300.0)))
    // AGI (not an OSRS stat): estimated mobility * 15, capped at 90.
    // Order: {ATK, STR, DEF, MAG, RNG, AGI}
    // -------------------------------------------------------------------------

    private static final Map<String, int[]> STAT_BASES;
    static {
        Map<String, int[]> b = new HashMap<>();

        // Per-monster stat floors — reviewed/tuned by user (2026-07-25).

        b.put("Chicken", new int[]{1, 1, 1, 1, 1, 45});
        b.put("Cow", new int[]{1, 1, 1, 1, 1, 30});
        b.put("Cow calf", new int[]{1, 1, 1, 1, 1, 45});
        b.put("Duck", new int[]{1, 1, 1, 1, 1, 60});
        b.put("Ram", new int[]{1, 1, 1, 1, 1, 45});
        b.put("Seagull", new int[]{1, 1, 1, 1, 1, 75});
        b.put("Man", new int[]{1, 1, 1, 1, 1, 45});
        b.put("Woman", new int[]{1, 1, 1, 1, 1, 45});
        b.put("Farmer", new int[]{1, 1, 2, 1, 1, 30});
        b.put("Goblin", new int[]{1, 1, 1, 1, 1, 60});
        b.put("Guard", new int[]{5, 5, 4, 1, 1, 45});
        b.put("Rat", new int[]{1, 1, 1, 1, 1, 75});
        b.put("Giant rat", new int[]{1, 1, 1, 1, 1, 60});
        b.put("Imp", new int[]{1, 1, 1, 1, 1, 90});
        b.put("Zombie", new int[]{2, 3, 3, 1, 1, 15});
        b.put("Skeleton", new int[]{5, 5, 5, 1, 1, 30});
        b.put("Ghost", new int[]{4, 4, 5, 1, 1, 60});
        b.put("Barbarian", new int[]{2, 2, 1, 1, 1, 60});
        b.put("Warrior", new int[]{2, 2, 1, 1, 1, 60});
        b.put("Wizard", new int[]{2, 2, 2, 3, 1, 45});
        b.put("Dark wizard", new int[]{5, 5, 4, 7, 1, 45});
        b.put("Minotaur", new int[]{7, 8, 8, 1, 1, 30});
        b.put("Bear", new int[]{5, 5, 5, 1, 1, 30});
        b.put("Grizzly bear", new int[]{12, 11, 11, 1, 1, 30});
        b.put("Unicorn", new int[]{3, 4, 4, 1, 1, 75});
        b.put("Spider", new int[]{1, 1, 1, 1, 1, 75});
        b.put("Giant spider", new int[]{6, 7, 6, 1, 1, 45});
        b.put("Scorpion", new int[]{3, 4, 3, 1, 1, 60});
        b.put("Hill giant", new int[]{5, 7, 8, 1, 1, 15});
        b.put("Moss giant", new int[]{9, 9, 9, 1, 1, 15});
        b.put("Fire giant", new int[]{20, 20, 20, 1, 1, 15});
        b.put("Ice giant", new int[]{12, 12, 12, 1, 1, 15});
        b.put("Earth warrior", new int[]{13, 13, 13, 1, 1, 30});
        b.put("Lesser demon", new int[]{20, 21, 21, 1, 1, 30});
        b.put("Greater demon", new int[]{23, 23, 24, 1, 1, 30});
        b.put("Black demon", new int[]{44, 44, 46, 1, 1, 30});
        b.put("Black knight", new int[]{8, 8, 8, 1, 1, 30});
        b.put("White knight", new int[]{8, 9, 6, 1, 1, 30});
        b.put("Hobgoblin", new int[]{7, 7, 7, 1, 1, 45});
        b.put("Chaos druid", new int[]{2, 2, 4, 3, 1, 45});
        b.put("Rock crab", new int[]{1, 1, 1, 1, 1, 15});
        b.put("Sand crab", new int[]{1, 1, 1, 1, 1, 15});
        b.put("Swamp crab", new int[]{1, 1, 15, 15, 1, 15});
        b.put("Gemstone crab", new int[]{1, 1, 1, 1, 1, 33});
        b.put("Pirate", new int[]{6, 6, 6, 1, 1, 45});
        b.put("Rogue", new int[]{4, 4, 4, 1, 1, 75});
        b.put("Abyssal demon", new int[]{55, 55, 40, 1, 5, 65});
        b.put("Aberrant spectre", new int[]{1, 1, 27, 50, 1, 45});
        b.put("Deviant spectre", new int[]{1, 1, 27, 62, 1, 45});
        b.put("Ankou", new int[]{21, 21, 18, 1, 1, 15});
        b.put("Banshee", new int[]{7, 5, 7, 1, 1, 30});
        b.put("Twisted banshee", new int[]{23, 26, 15, 1, 1, 30});
        b.put("Basilisk", new int[]{9, 14, 23, 1, 1, 10});
        b.put("Basilisk knight", new int[]{51, 39, 60, 1, 1, 10});
        b.put("Bloodveld", new int[]{23, 14, 9, 1, 1, 20});
        b.put("Mutated bloodveld", new int[]{33, 35, 9, 1, 1, 20});
        b.put("Cave bug", new int[]{2, 2, 2, 1, 1, 60});
        b.put("Cave crawler", new int[]{7, 5, 5, 1, 1, 45});
        b.put("Cave slime", new int[]{4, 4, 11, 4, 1, 30});
        b.put("Cave horror", new int[]{24, 23, 19, 24, 1, 45});
        b.put("Dagannoth", new int[]{20, 21, 15, 1, 1, 60});
        b.put("Dark beast", new int[]{42, 48, 36, 48, 1, 30});
        b.put("Drake", new int[]{42, 35, 36, 34, 42, 45});
        b.put("Dust devil", new int[]{32, 21, 12, 1, 1, 40});
        b.put("Fever spider", new int[]{18, 9, 12, 1, 1, 60});
        b.put("Fleshcrawler", new int[]{18, 1, 3, 1, 1, 60});
        b.put("Gargoyle", new int[]{35, 35, 32, 1, 1, 42});
        b.put("Hellhound", new int[]{32, 31, 31, 1, 1, 45});
        b.put("Hydra", new int[]{1, 1, 30, 63, 63, 30});
        b.put("Ice troll", new int[]{30, 30, 36, 1, 1, 15});
        b.put("Mountain troll", new int[]{12, 23, 12, 1, 1, 15});
        b.put("Troll", new int[]{11, 17, 9, 1, 1, 15});
        b.put("Infernal mage", new int[]{1, 1, 18, 23, 1, 45});
        b.put("Jelly", new int[]{14, 14, 36, 14, 1, 30});
        b.put("Warped jelly", new int[]{29, 27, 21, 29, 1, 30});
        b.put("Kalphite soldier", new int[]{21, 21, 21, 1, 1, 20});
        b.put("Kalphite guardian", new int[]{33, 33, 33, 1, 1, 25});
        b.put("Kalphite worker", new int[]{6, 6, 6, 1, 1, 45});
        b.put("Kurask", new int[]{20, 32, 32, 1, 1, 30});
        b.put("Lizardman", new int[]{13, 13, 13, 1, 13, 45});
        b.put("Lizardman brute", new int[]{20, 20, 20, 1, 1, 30});
        b.put("Lizardman shaman", new int[]{36, 36, 42, 39, 36, 45});
        b.put("Mogre", new int[]{17, 17, 14, 10, 1, 22});
        b.put("Nechryael", new int[]{29, 29, 32, 1, 1, 15});
        b.put("Greater nechryael", new int[]{59, 59, 26, 1, 1, 15});
        b.put("Pyrefiend", new int[]{16, 9, 7, 1, 1, 25});
        b.put("Rockslugs", new int[]{7, 8, 8, 1, 1, 15});
        b.put("Smoke devil", new int[]{1, 1, 40, 20, 66, 45});
        b.put("Spiritual warrior", new int[]{30, 30, 30, 1, 1, 30});
        b.put("Spiritual mage", new int[]{1, 1, 18, 54, 1, 30});
        b.put("Spiritual ranger", new int[]{1, 1, 24, 1, 42, 30});
        b.put("Suqah", new int[]{29, 29, 29, 1, 1, 25});
        b.put("Turoth", new int[]{16, 25, 25, 1, 1, 45});
        b.put("Vampyre", new int[]{17, 18, 17, 12, 1, 33});
        b.put("Vyrewatch", new int[]{32, 26, 26, 32, 32, 60});
        b.put("Waterfiend", new int[]{1, 1, 38, 32, 32, 30});
        b.put("Wyrm", new int[]{26, 18, 24, 24, 24, 20});
        b.put("Wyvern", new int[]{38, 35, 36, 38, 36, 22});
        b.put("Ancient wyvern", new int[]{45, 45, 45, 27, 27, 22});
        b.put("Skeletal wyvern", new int[]{38, 35, 36, 38, 36, 22});
        b.put("Fossil island wyvern", new int[]{38, 36, 27, 27, 27, 22});
        b.put("Warped tortoise", new int[]{23, 32, 23, 15, 1, 15});
        b.put("Tortoise", new int[]{5, 27, 24, 1, 1, 15});
        b.put("Zombie pirate", new int[]{6, 3, 6, 1, 1, 15});
        b.put("Baby blue dragon", new int[]{12, 12, 12, 5, 1, 22});
        b.put("Baby green dragon", new int[]{12, 12, 12, 5, 1, 22});
        b.put("Green dragon", new int[]{20, 20, 20, 55, 1, 35});
        b.put("Blue dragon", new int[]{29, 29, 29, 55, 1, 45});
        b.put("Red dragon", new int[]{39, 39, 39, 55, 1, 35});
        b.put("Black dragon", new int[]{58, 58, 58, 55, 1, 35});
        b.put("Lava dragon", new int[]{60, 60, 60, 55, 1, 40});
        b.put("Bronze dragon", new int[]{45, 46, 46, 55, 1, 20});
        b.put("Iron dragon", new int[]{47, 47, 47, 55, 1, 22});
        b.put("Steel dragon", new int[]{50, 50, 50, 55, 1, 25});
        b.put("Mithril dragon", new int[]{55, 55, 55, 55, 55, 30});
        b.put("Adamant dragon", new int[]{60, 60, 60, 56, 56, 35});
        b.put("Rune dragon", new int[]{67, 67, 67, 67, 67, 40});
        b.put("Brutal black dragon", new int[]{64, 64, 64, 55, 1, 32});
        b.put("Brutal red dragon", new int[]{62, 62, 62, 55, 1, 30});
        b.put("Brutal blue dragon", new int[]{60, 60, 60, 55, 1, 30});
        b.put("Brutal green dragon", new int[]{58, 57, 56, 50, 1, 30});
        b.put("Dark warrior", new int[]{23, 23, 17, 1, 1, 25});
        b.put("Ice warrior", new int[]{14, 14, 14, 1, 1, 25});
        b.put("Ice spider", new int[]{15, 17, 13, 1, 1, 45});
        b.put("Scurrius", new int[]{64, 30, 18, 45, 41, 33});
        b.put("Giant Mole", new int[]{40, 40, 68, 20, 1, 65});
        b.put("King Black Dragon", new int[]{72, 72, 72, 72, 65, 30});
        b.put("Chaos Elemental", new int[]{60, 60, 60, 65, 65, 55});
        b.put("Chaos Fanatic", new int[]{1, 1, 66, 60, 1, 33});
        b.put("Crazy Archaeologist", new int[]{1, 1, 54, 1, 59, 33});
        b.put("Scorpia", new int[]{65, 70, 50, 1, 1, 37});
        b.put("Deranged Archaeologist", new int[]{1, 1, 70, 1, 70, 33});
        b.put("Sarachnis", new int[]{60, 69, 45, 45, 65, 45});
        b.put("Hespori", new int[]{1, 1, 42, 45, 45, 1});
        b.put("Obor", new int[]{32, 32, 18, 5, 32, 5});
        b.put("Bryophyta", new int[]{24, 24, 30, 45, 1, 5});
        b.put("Cerberus", new int[]{57, 57, 60, 52, 60, 20});
        b.put("Kraken", new int[]{1, 1, 50, 66, 1, 15});
        b.put("Thermonuclear smoke devil", new int[]{1, 1, 50, 66, 72, 40});
        b.put("Alchemical Hydra", new int[]{30, 30, 30, 72, 72, 40});
        b.put("Zulrah", new int[]{1, 1, 75, 75, 75, 60});
        b.put("Vorkath", new int[]{75, 75, 70, 60, 75, 5});
        b.put("Phantom Muspah", new int[]{75, 75, 61, 55, 84, 44});
        b.put("Duke Sucellus", new int[]{85, 85, 80, 78, 1, 1});
        b.put("The Leviathan", new int[]{85, 85, 82, 83, 84, 10});
        b.put("Vardorvis", new int[]{90, 90, 72, 30, 1, 50});
        b.put("The Whisperer", new int[]{75, 75, 75, 54, 54, 42});
        b.put("Nex", new int[]{90, 81, 78, 69, 90, 75});
        b.put("TzTok-Jad", new int[]{75, 75, 70, 85, 85, 15});
        b.put("TzKal-Zuk", new int[]{90, 90, 85, 65, 65, 1});
        b.put("Araxxor", new int[]{77, 77, 41, 44, 63, 60});
        b.put("Hueycoatl", new int[]{62, 62, 60, 62, 62, 25});
        b.put("Sol Heredit", new int[]{90, 90, 75, 60, 60, 30});
        b.put("Amoxliatl", new int[]{1, 1, 24, 55, 1, 35});
        b.put("Callisto", new int[]{85, 85, 65, 60, 60, 15});
        b.put("Artio", new int[]{75, 81, 48, 45, 39, 17});
        b.put("Venenatis", new int[]{80, 70, 65, 65, 65, 45});
        b.put("Spindel", new int[]{60, 49, 48, 71, 62, 48});
        b.put("Vet'ion", new int[]{80, 80, 70, 70, 1, 31});
        b.put("Calvar'ion", new int[]{65, 65, 52, 55, 1, 35});
        b.put("Corporeal Beast", new int[]{82, 82, 90, 80, 35, 10});
        b.put("Commander Zilyana", new int[]{80, 70, 70, 60, 10, 75});
        b.put("General Graardor", new int[]{60, 80, 72, 10, 70, 10});
        b.put("K'ril Tsutsaroth", new int[]{65, 70, 72, 27, 1, 15});
        b.put("Kree'arra", new int[]{60, 60, 72, 65, 82, 40});
        b.put("Dagannoth Rex", new int[]{70, 70, 60, 1, 1, 30});
        b.put("Dagannoth Prime", new int[]{1, 1, 60, 77, 1, 30});
        b.put("Dagannoth Supreme", new int[]{1, 1, 55, 1, 77, 30});
        b.put("Dusk", new int[]{50, 42, 30, 42, 42, 30});
        b.put("Dawn", new int[]{42, 42, 30, 30, 42, 40});
        b.put("The Nightmare", new int[]{70, 70, 45, 70, 70, 40});
        b.put("Phosani's Nightmare", new int[]{75, 75, 45, 75, 75, 40});
        b.put("Ahrim the Blighted", new int[]{1, 1, 30, 40, 1, 30});
        b.put("Dharok the Wretched", new int[]{10, 65, 50, 1, 1, 10});
        b.put("Guthan the Infested", new int[]{30, 50, 30, 1, 1, 10});
        b.put("Karil the Tainted", new int[]{1, 1, 40, 1, 55, 25});
        b.put("Torag the Corrupted", new int[]{30, 50, 60, 1, 1, 10});
        b.put("Verac the Defiled", new int[]{40, 30, 50, 1, 10, 30});
        b.put("Tekton", new int[]{90, 90, 70, 5, 50, 5});
        b.put("Great Olm", new int[]{75, 75, 65, 85, 85, 15});
        b.put("Vespula", new int[]{1, 1, 26, 26, 60, 75});
        b.put("Maiden of Sugadinti", new int[]{40, 40, 70, 80, 80, 1});
        b.put("Pestilent Bloat", new int[]{55, 60, 75, 45, 70, 48});
        b.put("Sotetseg", new int[]{75, 75, 66, 75, 75, 15});
        b.put("Xarpus", new int[]{1, 1, 75, 30, 66, 12});
        b.put("Verzik Vitur", new int[]{80, 80, 77, 82, 72, 40});
        b.put("Akkha", new int[]{70, 70, 45, 70, 70, 30});
        b.put("Ba-Ba", new int[]{65, 68, 46, 30, 66, 45});
        b.put("Kephri", new int[]{15, 44, 42, 65, 55, 30});
        b.put("Zebak", new int[]{55, 75, 55, 55, 55, 10});
        b.put("Tumeken's Warden", new int[]{80, 85, 48, 77, 75, 15});
        b.put("Elidinis' Warden", new int[]{80, 85, 48, 77, 75, 15});
        b.put("Abyssal Sire", new int[]{54, 41, 75, 60, 1, 15});
        
        STAT_BASES = Collections.unmodifiableMap(b);
    }

    // -------------------------------------------------------------------------
    // Per-monster Hitpoints — a REAL, factual OSRS attribute (wiki-sourced).
    // Shown on cards as info, NOT a rolled stat. Drives the "Power Level" metric:
    //   powerLevel = round((7 rolled stats incl. Prayer) / 7 + HP / 6)
    // HP is added separately at 1/6 weight so it separates the difficulty tiers
    // (raw hundreds-thousands for bosses), and Power Level can exceed 99 for bosses.
    // Unlisted monsters fall back to DEFAULT_HP.
    // -------------------------------------------------------------------------

    public static final int DEFAULT_HP = 8;
    private static final Map<String, Integer> HITPOINTS;
    static {
        Map<String, Integer> h = new HashMap<>();
        // F2P / Early game
        h.put("Chicken",             3);
        h.put("Cow",                 8);
        h.put("Cow calf",            3);
        h.put("Duck",                3);
        h.put("Ram",                 5);
        h.put("Seagull",             3);
        h.put("Man",                 7);
        h.put("Woman",               7);
        h.put("Farmer",              8);
        h.put("Goblin",              5);
        h.put("Guard",               22);
        h.put("Rat",                 2);
        h.put("Giant rat",           8);
        h.put("Imp",                 8);
        h.put("Zombie",              22);
        h.put("Skeleton",            18);
        h.put("Ghost",               14);
        h.put("Barbarian",           30);
        h.put("Warrior",             30);
        h.put("Wizard",              15);
        h.put("Dark wizard",         24);
        h.put("Minotaur",            24);
        h.put("Bear",                25);
        h.put("Grizzly bear",        45);
        h.put("Unicorn",             30);
        h.put("Spider",              2);
        h.put("Giant spider",        20);
        h.put("Scorpion",            15);
        h.put("Hill giant",          35);
        h.put("Moss giant",          60);
        h.put("Fire giant",          111);
        h.put("Ice giant",           70);
        h.put("Earth warrior",       38);
        h.put("Lesser demon",        79);
        h.put("Greater demon",       87);
        h.put("Black demon",         157);
        h.put("Black knight",        33);
        h.put("White knight",        34);
        h.put("Hobgoblin",           29);
        h.put("Chaos druid",         30);
        h.put("Rock crab",           50);
        h.put("Sand crab",           60);
        h.put("Swamp crab",          60);
        h.put("Gemstone crab",       20000);
        h.put("Pirate",              30);
        h.put("Rogue",               30);
        // Slayer monsters
        h.put("Abyssal demon",       150);
        h.put("Aberrant spectre",    90);
        h.put("Deviant spectre",     165);
        h.put("Ankou",               60);
        h.put("Banshee",             45);
        h.put("Twisted banshee",     90);
        h.put("Basilisk",            82);
        h.put("Basilisk knight",     300);
        h.put("Bloodveld",           120);
        h.put("Mutated bloodveld",   150);
        h.put("Cave bug",            10);
        h.put("Cave crawler",        22);
        h.put("Cave slime",          10);
        h.put("Cave horror",         58);
        h.put("Dagannoth",           80);
        h.put("Dark beast",          220);
        h.put("Drake",               240);
        h.put("Dust devil",          105);
        h.put("Fever spider",        30);
        h.put("Fleshcrawler",        25);
        h.put("Gargoyle",            105);
        h.put("Hellhound",           116);
        h.put("Hydra",               300);
        h.put("Ice troll",           130);
        h.put("Mountain troll",      84);
        h.put("Troll",               89);
        h.put("Infernal mage",       60);
        h.put("Jelly",               75);
        h.put("Warped jelly",        150);
        h.put("Kalphite soldier",    90);
        h.put("Kalphite guardian",   200);
        h.put("Kalphite worker",     60);
        h.put("Kurask",              130);
        h.put("Lizardman",           30);
        h.put("Lizardman brute",     70);
        h.put("Lizardman shaman",    150);
        h.put("Mogre",               40);
        h.put("Nechryael",           105);
        h.put("Greater nechryael",   200);
        h.put("Pyrefiend",           60);
        h.put("Rockslugs",           30);
        h.put("Smoke devil",         65);
        h.put("Spiritual warrior",   100);
        h.put("Spiritual mage",      120);
        h.put("Spiritual ranger",    90);
        h.put("Suqah",               100);
        h.put("Turoth",              100);
        h.put("Vampyre",             30);
        h.put("Vyrewatch",           150);
        h.put("Waterfiend",          128);
        h.put("Wyrm",                130);
        h.put("Wyvern",              200);
        h.put("Ancient wyvern",      200);
        h.put("Skeletal wyvern",     200);
        h.put("Fossil island wyvern",200);
        h.put("Warped tortoise",     145);
        h.put("Tortoise",            145);
        h.put("Zombie pirate",       55);
        // Dragons
        h.put("Baby blue dragon",    60);
        h.put("Baby green dragon",   55);
        h.put("Green dragon",        75);
        h.put("Blue dragon",         105);
        h.put("Red dragon",          140);
        h.put("Black dragon",        189);
        h.put("Lava dragon",         240);
        h.put("Bronze dragon",       120);
        h.put("Iron dragon",         165);
        h.put("Steel dragon",        210);
        h.put("Mithril dragon",      250);
        h.put("Adamant dragon",      300);
        h.put("Rune dragon",         330);
        h.put("Brutal black dragon", 315);
        h.put("Brutal red dragon",   240);
        h.put("Brutal blue dragon",  200);
        h.put("Brutal green dragon", 170);
        // Wilderness / other
        h.put("Dark warrior",        30);
        h.put("Ice warrior",         45);
        h.put("Ice spider",          30);
        // Bosses
        h.put("Scurrius",            250);
        h.put("Giant Mole",          500);
        h.put("King Black Dragon",   240);
        h.put("Chaos Elemental",     300);
        h.put("Chaos Fanatic",       200);
        h.put("Crazy Archaeologist", 200);
        h.put("Scorpia",             200);
        h.put("Deranged Archaeologist", 220);
        h.put("Sarachnis",           400);
        h.put("Hespori",             250);
        h.put("Obor",                150);
        h.put("Bryophyta",           180);
        h.put("Cerberus",            600);
        h.put("Kraken",              255);
        h.put("Thermonuclear smoke devil", 245);
        h.put("Alchemical Hydra",    1100);
        h.put("Zulrah",              500);
        h.put("Vorkath",             750);
        h.put("Phantom Muspah",      350);
        h.put("Duke Sucellus",       440);
        h.put("The Leviathan",       900);
        h.put("Vardorvis",           700);
        h.put("The Whisperer",       550);
        h.put("Nex",                 3400);
        h.put("TzTok-Jad",           250);
        h.put("TzKal-Zuk",           1200);
        h.put("Araxxor",             1000);
        h.put("Hueycoatl",           1500);
        h.put("Sol Heredit",         1400);
        h.put("Amoxliatl",           250);
        h.put("Callisto",            500);
        h.put("Artio",               350);
        h.put("Venenatis",           500);
        h.put("Spindel",             350);
        h.put("Vet'ion",             350);
        h.put("Calvar'ion",          220);
        h.put("Corporeal Beast",     2000);
        h.put("Commander Zilyana",   255);
        h.put("General Graardor",    255);
        h.put("K'ril Tsutsaroth",    255);
        h.put("Kree'arra",           255);
        h.put("Dagannoth Rex",       255);
        h.put("Dagannoth Prime",     255);
        h.put("Dagannoth Supreme",   255);
        h.put("Dusk",                315);
        h.put("Dawn",                315);
        h.put("The Nightmare",       2400);
        h.put("Phosani's Nightmare", 2400);
        h.put("Ahrim the Blighted",  100);
        h.put("Dharok the Wretched", 100);
        h.put("Guthan the Infested", 100);
        h.put("Karil the Tainted",   100);
        h.put("Torag the Corrupted", 100);
        h.put("Verac the Defiled",   100);
        h.put("Tekton",              300);
        h.put("Great Olm",           800);
        h.put("Vespula",             200);
        h.put("Maiden of Sugadinti", 2500);
        h.put("Pestilent Bloat",     2000);
        h.put("Sotetseg",            450);
        h.put("Xarpus",              450);
        h.put("Verzik Vitur",        500);
        h.put("Akkha",               400);
        h.put("Ba-Ba",              380);
        h.put("Kephri",              400);
        h.put("Zebak",               580);
        h.put("Tumeken's Warden",    880);
        h.put("Elidinis' Warden",    880);
        h.put("Abyssal Sire",        400);
        HITPOINTS = Collections.unmodifiableMap(h);
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
            "Warped tortoise", "Tortoise",
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
            "Chaos druid", "Dark warrior",
            "Ice warrior",
            "Crazy Archaeologist", "Deranged Archaeologist", "Chaos Fanatic",
            "Sol Heredit"
        )) { s.put(n, HUMAN); }

        // INSECT — insects, spiders, scorpions, and arthropod-type creatures
        for (String n : Arrays.asList(
            "Spider", "Giant spider", "Ice spider", "Fever spider",
            "Cave bug", "Cave crawler", "Cave slime", "Fleshcrawler",
            "Scorpion", "Scorpia",
            "Araxxor", "Venenatis", "Spindel", "Sarachnis",
            "Vespula", "Kephri"
        )) { s.put(n, INSECT); }

        // KALPHITE — kalphite species
        for (String n : Arrays.asList(
            "Kalphite soldier", "Kalphite worker", "Kalphite guardian"
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
            "Vampyre", "Vyrewatch",
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

    /** Case-insensitive lookup of the catalogued roster, for membership tests. */
    private static final java.util.Set<String> ROSTER_LOWER =
            ROSTER.stream().map(s -> s.toLowerCase(java.util.Locale.ROOT)).collect(Collectors.toSet());

    /**
     * True if this NPC name is part of the catalogued Bestiary roster (case-insensitive).
     * Kills/captures of unknown NPCs are ignored so they can't pollute the dex or collection.
     */
    public static boolean isKnown(String npcName) {
        return npcName != null && ROSTER_LOWER.contains(npcName.toLowerCase(java.util.Locale.ROOT));
    }

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
        "Dusk", "Dawn",
        "Amoxliatl", "Chaos Elemental", "Hueycoatl", "King Black Dragon"
    ));

    private static final java.util.Set<String> ENDGAME_BOSSES = new java.util.HashSet<>(Arrays.asList(
        "TzTok-Jad", "TzKal-Zuk", "Nex",
        "Duke Sucellus", "The Leviathan", "Vardorvis", "The Whisperer",
        "Sol Heredit",
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

    /**
     * Returns per-stat base floors for the given NPC, scaled from actual OSRS stats.
     * Falls back to a uniform array derived from getStatFloor() if the monster is not
     * in STAT_BASES (e.g. dynamically added kill-count monsters).
     * Order: {ATK, STR, DEF, MAG, RNG, AGI}
     */
    public static int[] getStatBases(String npcName, int combatLevel) {
        int[] bases = STAT_BASES.get(npcName);
        if (bases != null) return bases.clone();
        int floor = getStatFloor(npcName, combatLevel);
        return new int[]{floor, floor, floor, floor, floor, floor};
    }

    /**
     * Returns the monster's factual Hitpoints (wiki-sourced), used as card info and
     * as the dominant term in the Power Level metric. Unlisted monsters (e.g.
     * dynamically added kill-count entries) fall back to {@link #DEFAULT_HP}.
     */
    public static int getHitpoints(String npcName) {
        return HITPOINTS.getOrDefault(npcName, DEFAULT_HP);
    }

    // -------------------------------------------------------------------------
    // Per-monster Prayer level — factual card info. Almost every monster has 1;
    // only monsters that actually use/have a notable Prayer level are listed.
    // -------------------------------------------------------------------------

    private static final Map<String, Integer> PRAYER;
    static {
        Map<String, Integer> p = new HashMap<>();

        // Per-monster base Prayer (reviewed). Unlisted monsters default to 1.

        p.put("Abyssal demon", 15);
        p.put("Aberrant spectre", 5);
        p.put("Deviant spectre", 5);
        p.put("Ankou", 2);
        p.put("Bloodveld", 10);
        p.put("Kalphite guardian", 3);
        p.put("Lizardman shaman", 20);
        p.put("Nechryael", 5);
        p.put("Greater nechryael", 7);
        p.put("Smoke devil", 20);
        p.put("Spiritual warrior", 43);
        p.put("Spiritual mage", 43);
        p.put("Spiritual ranger", 43);
        p.put("Vyrewatch", 20);
        p.put("Wyrm", 15);
        p.put("Wyvern", 30);
        p.put("Ancient wyvern", 30);
        p.put("Skeletal wyvern", 30);
        p.put("Fossil island wyvern", 30);
        p.put("Green dragon", 2);
        p.put("Blue dragon", 2);
        p.put("Red dragon", 2);
        p.put("Black dragon", 2);
        p.put("Lava dragon", 2);
        p.put("Bronze dragon", 2);
        p.put("Iron dragon", 3);
        p.put("Steel dragon", 5);
        p.put("Mithril dragon", 10);
        p.put("Adamant dragon", 15);
        p.put("Rune dragon", 20);
        p.put("Brutal black dragon", 5);
        p.put("Brutal red dragon", 5);
        p.put("Brutal blue dragon", 5);
        p.put("Brutal green dragon", 5);
        p.put("Scurrius", 20);
        p.put("Giant Mole", 30);
        p.put("King Black Dragon", 40);
        p.put("Chaos Elemental", 30);
        p.put("Deranged Archaeologist", 5);
        p.put("Sarachnis", 5);
        p.put("Hespori", 15);
        p.put("Obor", 10);
        p.put("Bryophyta", 10);
        p.put("Cerberus", 20);
        p.put("Zulrah", 40);
        p.put("Vorkath", 50);
        p.put("Phantom Muspah", 50);
        p.put("Duke Sucellus", 70);
        p.put("The Leviathan", 70);
        p.put("Vardorvis", 70);
        p.put("The Whisperer", 70);
        p.put("Nex", 75);
        p.put("TzTok-Jad", 70);
        p.put("TzKal-Zuk", 80);
        p.put("Sol Heredit", 80);
        p.put("Callisto", 50);
        p.put("Artio", 35);
        p.put("Venenatis", 50);
        p.put("Spindel", 35);
        p.put("Vet'ion", 50);
        p.put("Calvar'ion", 35);
        p.put("Corporeal Beast", 70);
        p.put("Commander Zilyana", 65);
        p.put("General Graardor", 65);
        p.put("K'ril Tsutsaroth", 65);
        p.put("Kree'arra", 65);
        p.put("Dusk", 30);
        p.put("Dawn", 30);
        p.put("The Nightmare", 68);
        p.put("Phosani's Nightmare", 68);
        p.put("Ahrim the Blighted", 40);
        p.put("Dharok the Wretched", 40);
        p.put("Guthan the Infested", 40);
        p.put("Karil the Tainted", 40);
        p.put("Torag the Corrupted", 40);
        p.put("Verac the Defiled", 40);
        p.put("Tekton", 65);
        p.put("Great Olm", 80);
        p.put("Vespula", 65);
        p.put("Maiden of Sugadinti", 65);
        p.put("Pestilent Bloat", 65);
        p.put("Sotetseg", 65);
        p.put("Xarpus", 50);
        p.put("Verzik Vitur", 80);
        p.put("Akkha", 65);
        p.put("Ba-Ba", 65);
        p.put("Kephri", 65);
        p.put("Zebak", 70);
        p.put("Tumeken's Warden", 80);
        p.put("Elidinis' Warden", 80);
        p.put("Abyssal Sire", 40);
        
        PRAYER = Collections.unmodifiableMap(p);
    }

    /**
     * Returns the monster's Prayer level — a factual card attribute. Almost all
     * monsters default to 1; only listed prayer-using monsters differ.
     */
    public static int getPrayer(String npcName) {
        return PRAYER.getOrDefault(npcName, 1);
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
