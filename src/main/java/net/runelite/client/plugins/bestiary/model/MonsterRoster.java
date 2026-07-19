package net.runelite.client.plugins.bestiary.model;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Canonical list of known OSRS NPC names — the "Pokédex" of the bestiary.
 * Names must match exactly what npc.getName() returns in-game (sentence case
 * for common monsters, title case for bosses / proper names).
 *
 * This list is the starting point; any NPC present in the player's kill counts
 * is also included automatically, so new monsters are discovered as you play.
 *
 * NOTE: This list needs ongoing review. Add missing NPCs as the plugin matures.
 */
public class MonsterRoster {

    /** All known NPC names in the static roster (add new entries here). */
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
        "Baby blue dragon",
        "Blue dragon", "Black dragon", "Red dragon",
        "Bronze dragon", "Iron dragon", "Steel dragon",
        "Mithril dragon", "Adamant dragon", "Rune dragon",
        "Lava dragon",
        "Brutal black dragon", "Brutal red dragon", "Brutal blue dragon", "Brutal green dragon",
        "Green dragon", "Baby green dragon",

        // === Wilderness ===
        "Chaos druid warrior",
        "Dark warrior",
        "Ice warrior", "Ice spider",
        "Skeleton (Wilderness)",
        "Lava dragon",

        // === Bossses — Solo ===
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
        "Callisto",
        "Venenatis",
        "Vet'ion",
        "Artio",
        "Spindel",
        "Calvar'ion",
        "Corporeal Beast",

        // === GWD ===
        "Commander Zilyana",
        "General Graardor",
        "K'ril Tsutsaroth",
        "Kree'arra",

        // === Dagannoth Kings ===
        "Dagannoth Rex",
        "Dagannoth Prime",
        "Dagannoth Supreme",

        // === Grotesque Guardians ===
        "Dusk",
        "Dawn",

        // === Nightmare ===
        "The Nightmare",
        "Phosani's Nightmare",

        // === Barrows ===
        "Ahrim the Blighted",
        "Dharok the Wretched",
        "Guthan the Infested",
        "Karil the Tainted",
        "Torag the Corrupted",
        "Verac the Defiled",

        // === CoX bosses ===
        "Tekton",
        "Great Olm",
        "Vespula",
        "Lizardman shaman",

        // === ToB bosses ===
        "Maiden of Sugadinti",
        "Pestilent Bloat",
        "Sotetseg",
        "Xarpus",
        "Verzik Vitur",

        // === ToA bosses ===
        "Akkha",
        "Ba-Ba",
        "Kephri",
        "Zebak",
        "Tumeken's Warden",
        "Elidinis' Warden",

        // === Abyssal Sire ===
        "Abyssal Sire"
    );

    /**
     * Builds the full display roster by merging the static list with any NPC
     * the player has actually killed. Uses case-insensitive deduplication so
     * slight capitalisation differences between the roster and game names don't
     * create duplicate slots. Kill-count names (exact game names) take
     * precedence over roster names on a collision.
     *
     * @return alphabetically-sorted list of canonical NPC names
     */
    public static List<String> buildFullRoster(Map<String, Integer> killCounts) {
        // lowercaseKey → canonical name (kill-count name wins over roster name)
        Map<String, String> deduped = new LinkedHashMap<>();

        for (String name : ROSTER) {
            deduped.putIfAbsent(name.toLowerCase(), name);
        }
        // Kill-count names are exact game strings → override roster entry if present
        for (String name : killCounts.keySet()) {
            deduped.put(name.toLowerCase(), name);
        }

        return deduped.values().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    /**
     * Assigns stable dex numbers to the full roster, ordered alphabetically.
     * Alphabetical is stable regardless of what you've captured.
     */
    public static Map<String, Integer> assignDexNumbers(List<String> fullRoster) {
        Map<String, Integer> nums = new LinkedHashMap<>();
        for (int i = 0; i < fullRoster.size(); i++) {
            nums.put(fullRoster.get(i), i + 1);
        }
        return nums;
    }
}
