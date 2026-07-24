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
        "Brutal black dragon", "Brutal red dragon", "Brutal blue dragon", "Brutal green dragon",

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

        // NIMBLE — P: RNG+AGI  |  small, fast, evasive
        for (String n : Arrays.asList(
            "Chicken", "Duck", "Seagull",
            "Rat", "Giant rat", "Imp",
            "Goblin", "Spider", "Cave bug", "Desert lizard",
            "Unicorn", "Cow calf",
            "Baby blue dragon", "Baby green dragon"
        )) { a.put(n, NIMBLE); }

        // BRUTE — P: ATK+STR  |  pure melee muscle
        for (String n : Arrays.asList(
            "Man", "Woman", "Farmer",
            "Guard", "Barbarian", "Warrior", "Pirate", "Rogue",
            "Black knight", "White knight",
            "Zombie", "Skeleton",
            "Hobgoblin",
            "Troll", "Mountain troll",
            "Hill giant",
            "Earth warrior",
            "Chaos druid", "Chaos druid warrior",
            "Bear", "Grizzly bear",
            "Lesser demon", "Greater demon",
            "Zombie pirate",
            "Ankou",
            "Spiritual warrior",
            "Lizardman brute",
            "Turoth",
            "Mogre",
            "Kalphite", "Kalphite soldier", "Kalphite worker",
            "Ice warrior",
            "Guthan the Infested", "Verac the Defiled",
            "Dark warrior"
        )) { a.put(n, BRUTE); }

        // TANK — P: STR+DEF  |  armoured, slow, durable
        for (String n : Arrays.asList(
            "Cow", "Minotaur",
            "Rock crab", "Sand crab", "Swamp crab", "Gemstone crab",
            "Cave slime",
            "Scorpion", "King scorpion",
            "Moss giant", "Fire giant", "Ice giant", "Ice troll",
            "Basilisk", "Basilisk knight",
            "Gargoyle",
            "Rockslugs",
            "Jelly", "Warped jelly",
            "Tortoise", "Warped tortoise",
            "Kalphite guardian",
            "Kurask",
            "Bloodveld",
            "Torag the Corrupted"
        )) { a.put(n, TANK); }

        // PREDATOR — P: ATK+AGI  |  quick striker, mobile hunter
        for (String n : Arrays.asList(
            "Giant spider", "Fever spider", "Cave crawler", "Fleshcrawler",
            "Ice spider",
            "Scorpia",
            "Hellhound",
            "Nechryael", "Greater nechryael",
            "Abyssal demon",
            "Lizardman",
            "Suqah",
            "Dust devil",
            "Vampyre", "Feral vampyre",
            "Green dragon"
        )) { a.put(n, PREDATOR); }

        // MYSTIC — P: DEF+MAG  |  magic users, spell-casters
        for (String n : Arrays.asList(
            "Wizard", "Dark wizard",
            "Banshee", "Twisted banshee",
            "Infernal mage", "Spiritual mage",
            "Pyrefiend",
            "Jelly", "Warped jelly",       // magical melee
            "Waterfiend",
            "Aberrant spectre", "Deviant spectre",
            "Chaos Fanatic",
            "Kraken",
            "Ahrim the Blighted",
            "Kephri",
            "Amoxliatl"
        )) { a.put(n, MYSTIC); }

        // STALKER — P: MAG+RNG  |  ranged/magic ambushers
        for (String n : Arrays.asList(
            "Ghost",
            "Dagannoth",
            "Bloodveld", "Mutated bloodveld",
            "Cave horror",
            "Smoke devil",
            "Hydra",
            "Wyrm",
            "Vyrewatch",
            "Vespula",
            "Xarpus"
        )) { a.put(n, STALKER); }

        // RANGER — P: RNG+AGI  |  pure ranged attackers, mobile archers
        for (String n : Arrays.asList(
            "Spiritual ranger",
            "Crazy Archaeologist", "Deranged Archaeologist",
            "Thermonuclear smoke devil",
            "Karil the Tainted"
        )) { a.put(n, RANGER); }

        // TITAN — P: STR+MAG  |  powerful melee+magic hybrids
        for (String n : Arrays.asList(
            "Black demon",
            "Dark beast",
            "Drake",
            "Wyvern", "Ancient wyvern", "Skeletal wyvern", "Fossil island wyvern",
            "Blue dragon", "Red dragon", "Black dragon", "Lava dragon",
            "Bronze dragon", "Iron dragon", "Steel dragon",
            "Brutal green dragon",
            "Lizardman shaman",
            "Obor", "Bryophyta",
            "Hespori",
            "Ba-Ba",
            "Dharok the Wretched"
        )) { a.put(n, TITAN); }

        // APEX — P: ATK+STR+MAG  |  true melee-mage hybrid bosses
        for (String n : Arrays.asList(
            "Rune dragon",
            "Scurrius",
            "Chaos Elemental",
            "Sarachnis",
            "Phantom Muspah",
            "TzTok-Jad",
            "Hueycoatl",
            "Venenatis", "Spindel",
            "K'ril Tsutsaroth",
            "Dusk", "Dawn",
            "The Nightmare", "Phosani's Nightmare",
            "Maiden of Sugadinti", "Pestilent Bloat",
            "Akkha", "Zebak",
            "Tumeken's Warden", "Elidinis' Warden",
            "Abyssal Sire"
        )) { a.put(n, APEX); }

        // JUGGERNAUT — P: ATK+STR+DEF  |  physical powerhouse bosses, no magic
        for (String n : Arrays.asList(
            "Mithril dragon", "Adamant dragon",
            "Brutal black dragon", "Brutal red dragon", "Brutal blue dragon",
            "King Black Dragon",
            "Giant Mole",
            "Cerberus",
            "Duke Sucellus", "Vardorvis",
            "Araxxor",
            "Sol Heredit",
            "Callisto", "Artio",
            "Vet'ion", "Calvar'ion",
            "Corporeal Beast",
            "General Graardor",
            "Dagannoth Rex",
            "Tekton"
        )) { a.put(n, JUGGERNAUT); }

        // ARCHON — P: DEF+MAG+RNG  |  arcane/ranged bosses, weak melee
        for (String n : Arrays.asList(
            "Alchemical Hydra",
            "Zulrah", "Vorkath",
            "The Leviathan", "The Whisperer",
            "Nex",
            "TzKal-Zuk",
            "Commander Zilyana", "Kree'arra",
            "Dagannoth Prime", "Dagannoth Supreme",
            "Great Olm",
            "Sotetseg",
            "Verzik Vitur"
        )) { a.put(n, ARCHON); }

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
        // F2P / Early game
        b.put("Chicken",             new int[]{ 1,  1,  1,  1,  1, 45});
        b.put("Cow",                 new int[]{ 1,  1,  1,  1,  1, 30});
        b.put("Cow calf",            new int[]{ 1,  1,  1,  1,  1, 45});
        b.put("Duck",                new int[]{ 1,  1,  1,  1,  1, 60});
        b.put("Ram",                 new int[]{ 1,  1,  1,  1,  1, 45});
        b.put("Seagull",             new int[]{ 1,  1,  1,  1,  1, 75});
        b.put("Man",                 new int[]{ 1,  1,  1,  1,  1, 45});
        b.put("Woman",               new int[]{ 1,  1,  1,  1,  1, 45});
        b.put("Farmer",              new int[]{ 1,  1,  2,  1,  1, 30});
        b.put("Goblin",              new int[]{ 1,  1,  1,  1,  1, 60});
        b.put("Guard",               new int[]{ 5,  5,  4,  1,  1, 45});
        b.put("Rat",                 new int[]{ 1,  1,  1,  1,  1, 75});
        b.put("Giant rat",           new int[]{ 1,  1,  1,  1,  1, 60});
        b.put("Imp",                 new int[]{ 1,  1,  1,  1,  1, 90});
        b.put("Zombie",              new int[]{ 2,  3,  3,  1,  1, 15});
        b.put("Skeleton",            new int[]{ 5,  5,  5,  1,  1, 30});
        b.put("Ghost",               new int[]{ 4,  4,  5,  1,  1, 60});
        b.put("Barbarian",           new int[]{ 2,  2,  1,  1,  1, 60});
        b.put("Warrior",             new int[]{ 2,  2,  1,  1,  1, 60});
        b.put("Wizard",              new int[]{ 2,  2,  2,  3,  1, 45});
        b.put("Dark wizard",         new int[]{ 5,  5,  4,  7,  1, 45});
        b.put("Minotaur",            new int[]{ 7,  8,  8,  1,  1, 30});
        b.put("Bear",                new int[]{ 5,  5,  5,  1,  1, 30});
        b.put("Grizzly bear",        new int[]{12, 11, 11,  1,  1, 30});
        b.put("Unicorn",             new int[]{ 3,  4,  4,  1,  1, 75});
        b.put("Spider",              new int[]{ 1,  1,  1,  1,  1, 75});
        b.put("Giant spider",        new int[]{ 6,  7,  6,  1,  1, 45});
        b.put("Scorpion",            new int[]{ 3,  4,  3,  1,  1, 60});
        b.put("Hill giant",          new int[]{ 5,  7,  8,  1,  1, 15});
        b.put("Moss giant",          new int[]{ 9,  9,  9,  1,  1, 15});
        b.put("Fire giant",          new int[]{20, 20, 20,  1,  1, 15});
        b.put("Ice giant",           new int[]{12, 12, 12,  1,  1, 15});
        b.put("Earth warrior",       new int[]{13, 13, 13,  1,  1, 30});
        b.put("Lesser demon",        new int[]{20, 21, 21,  1,  1, 30});
        b.put("Greater demon",       new int[]{23, 23, 24,  1,  1, 30});
        b.put("Black demon",         new int[]{44, 44, 46,  1,  1, 30});
        b.put("Black knight",        new int[]{ 8,  8,  8,  1,  1, 30});
        b.put("White knight",        new int[]{ 8,  9,  6,  1,  1, 30});
        b.put("Hobgoblin",           new int[]{ 7,  7,  7,  1,  1, 45});
        b.put("Chaos druid",         new int[]{ 2,  2,  4,  3,  1, 45});
        b.put("Rock crab",           new int[]{ 1,  1,  1,  1,  1, 15});
        b.put("Sand crab",           new int[]{ 1,  1,  1,  1,  1, 15});
        b.put("Swamp crab",          new int[]{ 1,  1, 15, 15,  1, 15});
        b.put("Gemstone crab",       new int[]{ 1,  1,  1,  1,  1, 33});
        b.put("Pirate",              new int[]{ 6,  6,  6,  1,  1, 45});
        b.put("Rogue",               new int[]{ 4,  4,  4,  1,  1, 75});
        // Slayer monsters
        b.put("Abyssal demon",       new int[]{29, 20, 41,  1,  1, 60});
        b.put("Aberrant spectre",    new int[]{ 1,  1, 27, 32,  1, 45});
        b.put("Deviant spectre",     new int[]{ 1,  1, 27, 62,  1, 45});
        b.put("Ankou",               new int[]{21, 21, 18,  1,  1, 30});
        b.put("Banshee",             new int[]{ 7,  5,  7,  1,  1, 30});
        b.put("Twisted banshee",     new int[]{23, 26, 15,  1,  1, 30});
        b.put("Basilisk",            new int[]{ 9, 14, 23,  1,  1, 30});
        b.put("Basilisk knight",     new int[]{51, 39, 60,  1,  1, 30});
        b.put("Bloodveld",           new int[]{23, 14,  9,  1,  1, 45});
        b.put("Mutated bloodveld",   new int[]{33, 35,  9,  1,  1, 45});
        b.put("Cave bug",            new int[]{ 2,  2,  2,  1,  1, 60});
        b.put("Cave crawler",        new int[]{ 7,  5,  5,  1,  1, 45});
        b.put("Cave slime",          new int[]{ 4,  4, 11,  4,  1, 30});
        b.put("Cave horror",         new int[]{24, 23, 19, 24,  1, 45});
        b.put("Dagannoth",           new int[]{20, 21, 15,  1,  1, 60});
        b.put("Dark beast",          new int[]{42, 48, 36, 48,  1, 30});
        b.put("Drake",               new int[]{42, 35, 36, 34, 42, 45});
        b.put("Dust devil",          new int[]{32, 21, 12,  1,  1, 60});
        b.put("Fever spider",        new int[]{18,  9, 12,  1,  1, 60});
        b.put("Fleshcrawler",        new int[]{18,  1,  3,  1,  1, 60});
        b.put("Gargoyle",            new int[]{23, 32, 32,  1,  1, 30});
        b.put("Hellhound",           new int[]{32, 31, 31,  1,  1, 45});
        b.put("Hydra",               new int[]{ 1,  1, 30, 63, 63, 30});
        b.put("Ice troll",           new int[]{30, 30, 36,  1,  1, 15});
        b.put("Mountain troll",      new int[]{12, 23, 12,  1,  1, 15});
        b.put("Troll",               new int[]{11, 17,  9,  1,  1, 15});
        b.put("Infernal mage",       new int[]{ 1,  1, 18, 23,  1, 45});
        b.put("Jelly",               new int[]{14, 14, 36, 14,  1, 30});
        b.put("Warped jelly",        new int[]{29, 27, 21, 29,  1, 30});
        b.put("Kalphite soldier",    new int[]{21, 21, 21,  1,  1, 30});
        b.put("Kalphite guardian",   new int[]{33, 33, 33,  1,  1, 30});
        b.put("Kalphite worker",     new int[]{ 6,  6,  6,  1,  1, 45});
        b.put("Kurask",              new int[]{20, 32, 32,  1,  1, 30});
        b.put("Lizardman",           new int[]{13, 13, 13,  1, 13, 45});
        b.put("Lizardman brute",     new int[]{20, 20, 20,  1,  1, 30});
        b.put("Lizardman shaman",    new int[]{36, 36, 42, 39, 36, 45});
        b.put("Mogre",               new int[]{17, 17, 14,  1,  1, 30});
        b.put("Nechryael",           new int[]{29, 29, 32,  1,  1, 45});
        b.put("Greater nechryael",   new int[]{59, 59, 26,  1,  1, 45});
        b.put("Pyrefiend",           new int[]{16,  9,  7,  1,  1, 45});
        b.put("Rockslugs",           new int[]{ 7,  8,  8,  1,  1, 15});
        b.put("Smoke devil",         new int[]{42, 39, 83,  1, 59, 60});
        b.put("Spiritual warrior",   new int[]{30, 30, 30,  1,  1, 45});
        b.put("Spiritual mage",      new int[]{ 1,  1, 18, 54,  1, 45});
        b.put("Spiritual ranger",    new int[]{ 1,  1, 24,  1, 42, 60});
        b.put("Suqah",               new int[]{29, 29, 29,  1,  1, 45});
        b.put("Turoth",              new int[]{16, 25, 25,  1,  1, 45});
        b.put("Vampyre",             new int[]{17, 18, 17, 12,  1, 45});
        b.put("Vyrewatch",           new int[]{32, 26, 26, 32, 32, 60});
        b.put("Waterfiend",          new int[]{ 1,  1, 38, 32, 32, 30});
        b.put("Wyrm",                new int[]{26, 18, 24, 24, 24, 45});
        b.put("Wyvern",              new int[]{38, 35, 36, 38, 36, 45});
        b.put("Ancient wyvern",      new int[]{45, 45, 45, 27, 27, 45});
        b.put("Skeletal wyvern",     new int[]{38, 35, 36, 38, 36, 45});
        b.put("Fossil island wyvern",new int[]{38, 36, 27, 27, 27, 45});
        b.put("Warped tortoise",     new int[]{23, 32, 23, 15,  1, 15});
        b.put("Tortoise",            new int[]{ 5, 27, 24,  1,  1, 15});
        b.put("Zombie pirate",       new int[]{ 6,  3,  6,  1,  1, 15});
        // Dragons
        b.put("Baby blue dragon",    new int[]{12, 12, 12,  1,  1, 45});
        b.put("Baby green dragon",   new int[]{12, 12, 12,  1,  1, 45});
        b.put("Green dragon",        new int[]{20, 20, 20, 20,  1, 30});
        b.put("Blue dragon",         new int[]{29, 29, 29,  1,  1, 30});
        b.put("Red dragon",          new int[]{39, 39, 39,  1,  1, 30});
        b.put("Black dragon",        new int[]{60, 60, 60, 30,  1, 30});
        b.put("Lava dragon",         new int[]{72, 66, 66,  1,  1, 30});
        b.put("Bronze dragon",       new int[]{34, 34, 34, 30,  1, 30});
        b.put("Iron dragon",         new int[]{50, 50, 50, 30,  1, 30});
        b.put("Steel dragon",        new int[]{65, 65, 65, 30,  1, 30});
        b.put("Mithril dragon",      new int[]{80, 80, 80, 50, 50, 45});
        b.put("Adamant dragon",      new int[]{84, 84, 82, 56, 56, 45});
        b.put("Rune dragon",         new int[]{85, 85, 83, 59, 74, 45});
        b.put("Brutal black dragon", new int[]{90, 63, 78, 75,  1, 30});
        b.put("Brutal red dragon",   new int[]{90, 63, 60, 75,  1, 30});
        b.put("Brutal blue dragon",  new int[]{90, 60, 60, 60,  1, 30});
        b.put("Brutal green dragon", new int[]{80, 50, 50, 50,  1, 30});
        // Wilderness / other
        b.put("Dark warrior",        new int[]{23, 23, 17,  1,  1, 45});
        b.put("Ice warrior",         new int[]{14, 14, 14,  1,  1, 30});
        b.put("Ice spider",          new int[]{15, 17, 13,  1,  1, 45});
        // Bosses
        b.put("Scurrius",            new int[]{90, 30, 18, 15, 15, 60});
        b.put("Giant Mole",          new int[]{60, 60, 60, 60,  1, 45});
        b.put("King Black Dragon",   new int[]{72, 72, 72, 72, 65, 30});
        b.put("Chaos Elemental",     new int[]{81, 81, 81, 81, 81, 75});
        b.put("Chaos Fanatic",       new int[]{ 1,  1, 66, 60,  1, 45});
        b.put("Crazy Archaeologist", new int[]{ 1,  1, 54,  1, 59, 60});
        b.put("Scorpia",             new int[]{75, 45, 54,  1,  1, 45});
        b.put("Deranged Archaeologist", new int[]{ 1,  1, 75,  1, 72, 45});
        b.put("Sarachnis",           new int[]{60, 72, 45, 45, 90, 45});
        b.put("Hespori",             new int[]{ 1,  1, 36, 38, 45, 30});
        b.put("Obor",                new int[]{27, 30, 18,  1, 36, 30});
        b.put("Bryophyta",           new int[]{39, 30, 30, 27,  1, 15});
        b.put("Cerberus",            new int[]{66, 66, 30, 66, 66, 45});
        b.put("Kraken",              new int[]{ 1,  1, 50, 50,  1,  1});
        b.put("Thermonuclear smoke devil", new int[]{69, 66, 90,  1, 90, 60});
        b.put("Alchemical Hydra",    new int[]{30, 30, 30, 78, 78, 45});
        b.put("Zulrah",              new int[]{ 1,  1, 90, 90, 90, 45});
        b.put("Vorkath",             new int[]{90, 90, 64, 45, 90, 30});
        b.put("Phantom Muspah",      new int[]{84, 84, 60, 45, 84, 45});
        b.put("Duke Sucellus",       new int[]{90, 90, 83, 90,  1, 30});
        b.put("The Leviathan",       new int[]{90, 90, 75, 48, 48, 45});
        b.put("Vardorvis",           new int[]{84, 90, 65, 65,  1, 45});
        b.put("The Whisperer",       new int[]{84, 84, 75, 54, 54, 60});
        b.put("Nex",                 new int[]{90, 60, 78, 69, 90, 75});
        b.put("TzTok-Jad",           new int[]{90, 90, 90, 90, 90, 15});
        b.put("TzKal-Zuk",           new int[]{90, 90, 78, 45, 90, 30});
        b.put("Araxxor",             new int[]{90, 90, 41, 57, 63, 60});
        b.put("Hueycoatl",           new int[]{84, 84, 60, 66, 66, 45});
        b.put("Sol Heredit",         new int[]{90, 90, 60, 90, 90, 60});
        b.put("Amoxliatl",           new int[]{ 1,  1, 24, 51,  1, 30});
        b.put("Callisto",            new int[]{90, 90, 68, 42, 60, 30});
        b.put("Artio",               new int[]{75, 81, 45, 27, 36, 30});
        b.put("Venenatis",           new int[]{90, 60, 90, 90, 90, 45});
        b.put("Spindel",             new int[]{60, 39, 68, 71, 86, 45});
        b.put("Vet'ion",             new int[]{90, 90, 90, 90,  1, 30});
        b.put("Calvar'ion",          new int[]{75, 75, 68, 53,  1, 30});
        b.put("Corporeal Beast",     new int[]{90, 90, 90, 90, 45, 15});
        b.put("Commander Zilyana",   new int[]{84, 59, 90, 90, 75, 75});
        b.put("General Graardor",    new int[]{84, 90, 75, 24, 90, 30});
        b.put("K'ril Tsutsaroth",    new int[]{90, 90, 81, 60,  1, 45});
        b.put("Kree'arra",           new int[]{90, 60, 78, 60, 90, 75});
        b.put("Dagannoth Rex",       new int[]{77, 77, 77,  1, 77, 30});
        b.put("Dagannoth Prime",     new int[]{77, 77, 77, 77,  1, 30});
        b.put("Dagannoth Supreme",   new int[]{77, 77, 38, 77, 77, 30});
        b.put("Dusk",                new int[]{60, 42, 30, 42, 42, 30});
        b.put("Dawn",                new int[]{42, 42, 30, 30, 42, 45});
        b.put("The Nightmare",       new int[]{45, 45, 45, 45, 45, 45});
        b.put("Phosani's Nightmare", new int[]{45, 45, 45, 45, 45, 45});
        b.put("Ahrim the Blighted",  new int[]{ 1,  1, 30, 30,  1, 30});
        b.put("Dharok the Wretched", new int[]{30, 30, 30,  1,  1, 30});
        b.put("Guthan the Infested", new int[]{30, 30, 30,  1,  1, 30});
        b.put("Karil the Tainted",   new int[]{ 1,  1, 30,  1, 30, 60});
        b.put("Torag the Corrupted", new int[]{30, 30, 30,  1,  1, 15});
        b.put("Verac the Defiled",   new int[]{30, 30, 30,  1,  1, 30});
        b.put("Tekton",              new int[]{90, 90, 62, 62,  1, 15});
        b.put("Great Olm",           new int[]{75, 75, 45, 75, 75, 30});
        b.put("Vespula",             new int[]{45, 45, 26, 26, 45, 60});
        b.put("Maiden of Sugadinti", new int[]{90, 90, 60, 90, 90, 45});
        b.put("Pestilent Bloat",     new int[]{75, 90, 30, 45, 54, 30});
        b.put("Sotetseg",            new int[]{75, 75, 60, 75, 75, 30});
        b.put("Xarpus",              new int[]{ 1,  1, 75, 66, 30, 30});
        b.put("Verzik Vitur",        new int[]{90, 90, 45, 90, 90, 45});
        b.put("Akkha",               new int[]{30, 42, 24, 30, 30, 60});
        b.put("Ba-Ba",               new int[]{45, 48, 24, 30,  1, 45});
        b.put("Kephri",              new int[]{ 1,  1, 24, 38,  1, 30});
        b.put("Zebak",               new int[]{75, 42, 21, 30, 36, 30});
        b.put("Tumeken's Warden",    new int[]{90, 45, 45, 57, 57, 45});
        b.put("Elidinis' Warden",    new int[]{90, 45, 45, 57, 57, 45});
        b.put("Abyssal Sire",        new int[]{54, 41, 75, 60,  1, 30});
        STAT_BASES = Collections.unmodifiableMap(b);
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

    /** Assigns stable alphabetical dex numbers to the full roster. */
    public static Map<String, Integer> assignDexNumbers(List<String> fullRoster) {
        Map<String, Integer> nums = new LinkedHashMap<>();
        for (int i = 0; i < fullRoster.size(); i++) {
            nums.put(fullRoster.get(i), i + 1);
        }
        return nums;
    }
}
