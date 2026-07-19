package net.runelite.client.plugins.bestiary.model;

import java.util.*;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.bestiary.model.DifficultyTier.*;

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
        "Chicken", "Cow", "Duck", "Seagull",
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
        "Rock crab", "Sand crab", "Swamp crab",
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
        "Mogre", "Molanisk",
        "Nechryael", "Greater nechryael", "Nechryarch",
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
        "Mutant tarn",
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
        "The Mimic",
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
        "Tempoross",
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
            "Chicken", "Cow", "Duck", "Seagull", "Man", "Woman", "Farmer",
            "Goblin", "Rat", "Giant rat", "Imp", "Unicorn", "Spider",
            "Baby blue dragon", "Baby green dragon"
        )) { d.put(n, BEGINNER); }

        // Easy — early F2P grind, low-level slayer
        for (String n : Arrays.asList(
            "Guard", "Barbarian", "Warrior", "Wizard", "Dark wizard", "Minotaur",
            "Bear", "Grizzly bear", "Giant spider", "Scorpion", "King scorpion",
            "Zombie", "Skeleton", "Ghost", "Pirate", "Rogue",
            "Rock crab", "Sand crab", "Swamp crab",
            "Hobgoblin", "Chaos druid", "Ankou",
            "Cave bug", "Cave crawler", "Cave slime",
            "Desert lizard", "Fever spider", "Pyrefiend", "Rockslugs",
            "Fleshcrawler", "Ice warrior", "Ice spider",
            "Green dragon"
        )) { d.put(n, EASY); }

        // Medium — mid-game slayer, accessible dungeon monsters
        for (String n : Arrays.asList(
            "Hill giant", "Moss giant", "Fire giant", "Ice giant", "Earth warrior",
            "Lesser demon", "Black knight", "White knight",
            "Banshee", "Twisted banshee", "Bloodveld",
            "Cave horror", "Jelly", "Warped jelly",
            "Basilisk", "Kalphite", "Kalphite soldier", "Kalphite worker",
            "Kurask", "Turoth", "Nechryael",
            "Blue dragon", "Red dragon", "Bronze dragon", "Iron dragon",
            "Spiritual warrior", "Spiritual mage", "Spiritual ranger",
            "Lizardman", "Lizardman brute",
            "Mogre", "Molanisk",
            "Vampyre", "Feral vampyre", "Vyrewatch",
            "Smoke devil", "Dust devil", "Infernal mage",
            "Mutant tarn", "Zombie pirate",
            "Tortoise", "Warped tortoise",
            "Troll", "Ice troll", "Mountain troll",
            "Dagannoth"
        )) { d.put(n, MEDIUM); }

        // Hard — high slayer level, requires solid stats/gear
        for (String n : Arrays.asList(
            "Greater demon", "Black demon", "Abyssal demon",
            "Gargoyle", "Hellhound", "Dark beast",
            "Greater nechryael", "Nechryarch", "Mutated bloodveld",
            "Kalphite guardian", "Kalphite soldier",
            "Basilisk knight",
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

        // Elite — challenging late-game PvM
        for (String n : Arrays.asList(
            "Adamant dragon", "Rune dragon",
            "Hydra", "Alchemical Hydra",
            "Giant Mole",
            "Sarachnis", "Hespori", "Obor", "Bryophyta",
            "Cerberus", "Kraken", "Thermonuclear smoke devil",
            "Abyssal Sire",
            "Deranged Archaeologist",
            "Chaos Fanatic", "Crazy Archaeologist", "Scorpia",
            "Callisto", "Artio",
            "Venenatis", "Spindel",
            "Vet'ion", "Calvar'ion"
        )) { d.put(n, ELITE); }

        // Boss — endgame encounters requiring preparation/skill
        for (String n : Arrays.asList(
            "Scurrius",
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
            "Tempoross",
            "The Mimic",
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

    /** Assigns stable alphabetical dex numbers to the full roster. */
    public static Map<String, Integer> assignDexNumbers(List<String> fullRoster) {
        Map<String, Integer> nums = new LinkedHashMap<>();
        for (int i = 0; i < fullRoster.size(); i++) {
            nums.put(fullRoster.get(i), i + 1);
        }
        return nums;
    }
}
