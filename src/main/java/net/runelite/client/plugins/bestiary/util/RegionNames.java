package net.runelite.client.plugins.bestiary.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps OSRS region IDs to human-readable area names.
 * Region ID formula: ((worldX >> 6) << 8) | (worldY >> 6)
 * Unknown regions fall back to "Region N".
 */
public final class RegionNames {

    private static final Map<Integer, String> NAMES = new HashMap<>();

    static {
        // --- Misthalin ---
        NAMES.put(12850, "Lumbridge");
        NAMES.put(12849, "Lumbridge Swamp");
        NAMES.put(12848, "South of Lumbridge");
        NAMES.put(12338, "Draynor Village");
        NAMES.put(12337, "Wizards' Tower");
        NAMES.put(12336, "Draynor Manor");
        NAMES.put(12597, "Varrock West");
        NAMES.put(12853, "Varrock East");
        NAMES.put(12598, "Grand Exchange");
        NAMES.put(12854, "Varrock Northeast");
        NAMES.put(12341, "Barbarian Village");
        NAMES.put(12342, "Edgeville");
        NAMES.put(13105, "Al Kharid");
        NAMES.put(13106, "Al Kharid South");
        NAMES.put(13361, "Digsite");
        NAMES.put(13362, "Exam Centre");
        NAMES.put(12596, "Champions' Guild");
        NAMES.put(12595, "South Lumbridge");
        NAMES.put(12339, "Draynor Bank");

        // --- Asgarnia ---
        NAMES.put(11828, "Falador");
        NAMES.put(12084, "Falador East");
        NAMES.put(12083, "White Wolf Mountain");
        NAMES.put(12082, "Port Sarim");
        NAMES.put(11826, "Rimmington");
        NAMES.put(11824, "Mudskipper Point");
        NAMES.put(11573, "Taverley");
        NAMES.put(11574, "Taverley East");
        NAMES.put(11575, "Burthorpe");
        NAMES.put(11317, "Burthorpe / White Wolf");
        NAMES.put(11571, "Crafting Guild");
        NAMES.put(11572, "Port Sarim South");
        NAMES.put(11830, "Ice Mountain");
        NAMES.put(12086, "Edgeville Monastery");
        NAMES.put(12085, "Falador Farm");
        NAMES.put(11829, "Falador West");
        NAMES.put(11570, "Rimmington Mine");

        // --- Kandarin ---
        NAMES.put(10547, "Ardougne North");
        NAMES.put(10546, "Ardougne South");
        NAMES.put(10804, "East Ardougne");
        NAMES.put(10805, "Ardougne Zoo");
        NAMES.put(10288, "Yanille");
        NAMES.put(10806, "Seers' Village");
        NAMES.put(11062, "Camelot");
        NAMES.put(11063, "Camelot East");
        NAMES.put(10293, "Fishing Guild");
        NAMES.put(9781, "Gnome Stronghold");
        NAMES.put(9780, "Gnome Agility");
        NAMES.put(10033, "Tree Gnome Village");
        NAMES.put(10039, "Barbarian Outpost");
        NAMES.put(10040, "Barbarian Assault");
        NAMES.put(11315, "Witchaven");
        NAMES.put(11316, "Witchaven South");
        NAMES.put(11062, "Camelot");
        NAMES.put(10807, "Seer's Village North");
        NAMES.put(9273, "Piscatoris");
        NAMES.put(10549, "Ranging Guild");
        NAMES.put(10550, "McGrubor's Wood");
        NAMES.put(10291, "Ardougne Sewers");
        NAMES.put(11318, "Catherby");
        NAMES.put(10287, "Castle Wars");

        // --- Morytania ---
        NAMES.put(13878, "Canifis");
        NAMES.put(13623, "Slayer Tower");
        NAMES.put(13622, "Slayer Tower South");
        NAMES.put(14131, "Barrows");
        NAMES.put(14130, "Barrows West");
        NAMES.put(13874, "Burgh de Rott");
        NAMES.put(13877, "Mort Myre Swamp");
        NAMES.put(13876, "Mort Myre East");
        NAMES.put(14390, "Ectofuntus");
        NAMES.put(14646, "Port Phasmatys");
        NAMES.put(14387, "Darkmeyer");
        NAMES.put(14386, "Meiyerditch");
        NAMES.put(13879, "Haunted Mine");
        NAMES.put(14135, "Mort'ton");
        NAMES.put(13875, "Swamp South");
        NAMES.put(14647, "Slepe");

        // --- Wilderness ---
        NAMES.put(12343, "Wilderness (Levels 1-5)");
        NAMES.put(12344, "Wilderness (Levels 6-10)");
        NAMES.put(12345, "Wilderness (Levels 11-15)");
        NAMES.put(12601, "Wilderness (Levels 16-20)");
        NAMES.put(12857, "Wilderness (Levels 21-25)");
        NAMES.put(13113, "Wilderness (Levels 26-30)");
        NAMES.put(13369, "Wilderness (Levels 31-35)");
        NAMES.put(12092, "Lava Maze");
        NAMES.put(12093, "Lava Maze North");
        NAMES.put(12349, "Mage Arena");
        NAMES.put(12605, "Wilderness Resource Area");
        NAMES.put(11835, "Chaos Altar");
        NAMES.put(13116, "Demonic Ruins");
        NAMES.put(12604, "Edgeville Wilderness");
        NAMES.put(12348, "Mage Arena Entrance");
        NAMES.put(11578, "God Wars Dungeon (Surface)");
        NAMES.put(12602, "Revenant Caves");
        NAMES.put(12603, "Wilderness Level 20 Area");
        NAMES.put(11580, "Wilderness Agility Course");

        // --- Desert ---
        NAMES.put(13358, "Pollnivneach");
        NAMES.put(13357, "Bedabin Camp");
        NAMES.put(13613, "Nardah");
        NAMES.put(13099, "Sophanem");
        NAMES.put(13098, "Menaphos");
        NAMES.put(12846, "Smoke Dungeon Entrance");
        NAMES.put(13356, "Agility Pyramid");
        NAMES.put(13614, "Nardah South");
        NAMES.put(13100, "Uzer");
        NAMES.put(13359, "Pollnivneach North");
        NAMES.put(13104, "Shantay Pass");
        NAMES.put(12848, "Lumbridge Desert");

        // --- Karamja ---
        NAMES.put(11055, "Karamja Jungle");
        NAMES.put(11057, "Brimhaven");
        NAMES.put(11056, "Karamja West");
        NAMES.put(11825, "Musa Point");
        NAMES.put(11821, "Karamja East");
        NAMES.put(11310, "Shilo Village");
        NAMES.put(14638, "Mos Le'Harmless");
        NAMES.put(14894, "Harmony Island");
        NAMES.put(11566, "Corsair Cove");
        NAMES.put(11567, "Corsair Cove North");
        NAMES.put(10992, "Tai Bwo Wannai");
        NAMES.put(10993, "Tai Bwo Wannai East");

        // --- Fremennik ---
        NAMES.put(10553, "Rellekka");
        NAMES.put(10297, "Rellekka West");
        NAMES.put(10042, "Waterbirth Island");
        NAMES.put(10044, "Miscellania");
        NAMES.put(10300, "Etceteria");
        NAMES.put(8252, "Lunar Isle");
        NAMES.put(9275, "Neitiznot");
        NAMES.put(9531, "Jatizso");
        NAMES.put(10809, "Sinclair Mansion");
        NAMES.put(10554, "Rellekka East");
        NAMES.put(9276, "Neitiznot Bridge");
        NAMES.put(9532, "Jatizso Mine");

        // --- Tirannwn ---
        NAMES.put(9265, "Lletya");
        NAMES.put(8753, "Isafdar");
        NAMES.put(8754, "Tyras Camp");
        NAMES.put(8752, "Crystal Cave");
        NAMES.put(9009, "Arandar");
        NAMES.put(9266, "Lletya South");

        // --- Zeah / Great Kourend ---
        NAMES.put(6457, "Kourend Castle");
        NAMES.put(6458, "Kourend East");
        NAMES.put(6967, "Hosidius");
        NAMES.put(6712, "Hosidius Kitchen");
        NAMES.put(6966, "Hosidius West");
        NAMES.put(5944, "Shayzien");
        NAMES.put(5688, "Shayzien South");
        NAMES.put(5689, "Shayzien East");
        NAMES.put(6459, "Arceuus");
        NAMES.put(6460, "Arceuus Library");
        NAMES.put(5947, "Lovakengj");
        NAMES.put(5946, "Lovakengj North");
        NAMES.put(7227, "Piscarilius");
        NAMES.put(7228, "Piscarilius East");
        NAMES.put(4919, "Mount Quidamortem");
        NAMES.put(6198, "Woodcutting Guild");
        NAMES.put(4922, "Farming Guild");
        NAMES.put(4921, "Kourend South");
        NAMES.put(5432, "Chambers of Xeric");

        // --- Underground / Dungeons ---
        NAMES.put(12185, "Dwarven Mine");
        NAMES.put(12184, "Mining Guild");
        NAMES.put(7505, "Stronghold of Security");
        NAMES.put(9043, "Ape Atoll Dungeon");
        NAMES.put(11153, "Taverley Dungeon");
        NAMES.put(9372, "Waterbirth Dungeon");
        NAMES.put(7758, "Smoke Dungeon");
        NAMES.put(9285, "Chaos Druid Tower");

        // --- Other Notable Areas ---
        NAMES.put(9551, "Ape Atoll");
        NAMES.put(9807, "Ape Atoll North");
        NAMES.put(9808, "Ape Atoll East");
        NAMES.put(10794, "Legends' Quest Area");
        NAMES.put(10038, "Tree Gnome Maze");
        NAMES.put(11827, "Falador South");
        NAMES.put(12340, "Lumbridge West");
        NAMES.put(12594, "River Lum");
        NAMES.put(13362, "Varrock Southeast");
        NAMES.put(12599, "Varrock North");
        NAMES.put(11576, "Death Plateau");
        NAMES.put(11577, "Trollheim");
        NAMES.put(11833, "Skull");
        NAMES.put(11834, "North of Edgeville");
        NAMES.put(12086, "Monastery");
        NAMES.put(12087, "South Wilderness");
    }

    private RegionNames() {}

    public static String get(int regionId) {
        return NAMES.getOrDefault(regionId, "Region " + regionId);
    }
}
