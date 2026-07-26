package net.runelite.client.plugins.bestiary.service;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.plugins.bestiary.model.CreatureQuality;
import net.runelite.client.plugins.bestiary.model.CreatureRarity;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Low-level SQLite layer for the Bestiary plugin.
 * Manages a single WAL-mode connection. All methods are called from the
 * client thread or executor threads managed by BestiaryDataService.
 *
 * Three tables:
 *   captures    — one row per CapturedCreature
 *   kill_counts — one row per npcName (total kills)
 *   metadata    — key/value for credits, XP, unlocked achievements
 */
@Slf4j
@Singleton
public class BestiaryDatabase {

    private static final String DB_NAME = "bestiary.db";
    private static final int    SCHEMA_VERSION = 7;

    private final File dbFile;
    private Connection conn;

    @Inject
    public BestiaryDatabase() {
        this.dbFile = new File(
            System.getProperty("user.home"),
            ".runelite" + File.separator + "bestiary" + File.separator + DB_NAME
        );
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void open() {
        try {
            dbFile.getParentFile().mkdirs();
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute("PRAGMA foreign_keys=ON");
            }
            migrateSchema();
            log.info("Bestiary DB opened: {}", dbFile.getAbsolutePath());
        } catch (SQLException e) {
            log.error("Failed to open bestiary DB", e);
        }
    }

    public void close() {
        try {
            if (conn != null && !conn.isClosed()) conn.close();
        } catch (SQLException e) {
            log.error("Failed to close bestiary DB", e);
        }
    }

    // -------------------------------------------------------------------------
    // Schema
    // -------------------------------------------------------------------------

    private void migrateSchema() throws SQLException {
        // Create metadata first so we can read/write schema version
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS metadata (" +
                "  key   TEXT PRIMARY KEY," +
                "  value TEXT NOT NULL" +
                ")"
            );
        }
        String storedVersion = getMetadata("schema_version", "0");
        if (!String.valueOf(SCHEMA_VERSION).equals(storedVersion)) {
            log.info("Bestiary DB schema v{} → v{}: rebuilding tables", storedVersion, SCHEMA_VERSION);
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("DROP TABLE IF EXISTS captures");
                st.executeUpdate("DROP TABLE IF EXISTS kill_counts");
            }
        }
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS captures (" +
                "  id                   TEXT    PRIMARY KEY," +
                "  npc_id               INTEGER NOT NULL," +
                "  npc_name             TEXT    NOT NULL," +
                "  npc_combat_level     INTEGER NOT NULL DEFAULT -1," +
                "  rarity               TEXT    NOT NULL," +
                "  stat_atk             INTEGER NOT NULL DEFAULT 50," +
                "  stat_str             INTEGER NOT NULL DEFAULT 50," +
                "  stat_def             INTEGER NOT NULL DEFAULT 50," +
                "  stat_mag             INTEGER NOT NULL DEFAULT 50," +
                "  stat_rng             INTEGER NOT NULL DEFAULT 50," +
                "  stat_agi             INTEGER NOT NULL DEFAULT 50," +
                "  capture_time         INTEGER NOT NULL," +
                "  region_name          TEXT    NOT NULL DEFAULT ''," +
                "  capture_level        INTEGER NOT NULL DEFAULT 1," +
                "  kills_before_capture INTEGER NOT NULL DEFAULT 0," +
                "  player_name          TEXT    NOT NULL DEFAULT ''," +
                "  nickname             TEXT," +
                "  favourite            INTEGER NOT NULL DEFAULT 0," +
                "  shiny                INTEGER NOT NULL DEFAULT 0," +
                "  album_cover          INTEGER NOT NULL DEFAULT 0," +
                "  prayer               INTEGER NOT NULL DEFAULT 1," +
                "  observed_hp          INTEGER NOT NULL DEFAULT 0," +
                "  rerolled_by          TEXT    NOT NULL DEFAULT ''" +
                ")"
            );
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS kill_counts (" +
                "  npc_name TEXT    PRIMARY KEY," +
                "  kills    INTEGER NOT NULL DEFAULT 0" +
                ")"
            );
        }
        setMetadata("schema_version", String.valueOf(SCHEMA_VERSION));
    }

    // -------------------------------------------------------------------------
    // Captures
    // -------------------------------------------------------------------------

    public void insertCapture(CapturedCreature c) {
        String sql =
            "INSERT OR REPLACE INTO captures " +
            "(id, npc_id, npc_name, npc_combat_level, rarity," +
            " stat_atk, stat_str, stat_def, stat_mag, stat_rng, stat_agi," +
            " capture_time, region_name, capture_level, kills_before_capture," +
            " player_name, nickname, favourite, shiny, album_cover, prayer, observed_hp, rerolled_by)" +
            " VALUES (?,?,?,?,?, ?,?,?,?,?,?, ?,?,?,?, ?,?,?,?,?, ?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.id);
            ps.setInt(2, c.npcId);
            ps.setString(3, c.npcName);
            ps.setInt(4, c.npcCombatLevel);
            ps.setString(5, c.rarity.name());
            ps.setInt(6, c.quality.attack);
            ps.setInt(7, c.quality.strength);
            ps.setInt(8, c.quality.defence);
            ps.setInt(9, c.quality.magic);
            ps.setInt(10, c.quality.ranged);
            ps.setInt(11, c.quality.agility);
            ps.setLong(12, c.captureTime.getEpochSecond());
            ps.setString(13, c.regionName != null ? c.regionName : "");
            ps.setInt(14, c.captureLevel);
            ps.setInt(15, c.killsBeforeCapture);
            ps.setString(16, c.playerName != null ? c.playerName : "");
            ps.setString(17, c.nickname);
            ps.setInt(18, c.favourite ? 1 : 0);
            ps.setInt(19, c.shiny ? 1 : 0);
            ps.setInt(20, c.albumCover ? 1 : 0);
            ps.setInt(21, c.prayer);
            ps.setInt(22, c.observedHp);
            ps.setString(23, c.rerolledBy != null ? c.rerolledBy : "");
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to insert capture {}", c.id, e);
        }
    }

    /**
     * Updates the mutable fields of an existing capture (nickname, favourite,
     * playerName, regionName). Immutable fields (stats, rarity, etc.) are not touched.
     */
    public void updateCaptureMutable(CapturedCreature c) {
        String sql = "UPDATE captures SET nickname=?, favourite=?, player_name=?, region_name=?, album_cover=? WHERE id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.nickname);
            ps.setInt(2, c.favourite ? 1 : 0);
            ps.setString(3, c.playerName != null ? c.playerName : "");
            ps.setString(4, c.regionName != null ? c.regionName : "");
            ps.setInt(5, c.albumCover ? 1 : 0);
            ps.setString(6, c.id);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to update capture {}", c.id, e);
        }
    }

    /**
     * Batch-updates mutable fields for all creatures in a list, wrapped in a
     * single transaction. Used by saveNow() to sync any in-memory mutations
     * that weren't individually tracked (e.g. playerName backfill).
     */
    public void batchUpdateMutable(List<CapturedCreature> creatures) {
        String sql = "UPDATE captures SET nickname=?, favourite=?, player_name=?, region_name=?, album_cover=? WHERE id=?";
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (CapturedCreature c : creatures) {
                    ps.setString(1, c.nickname);
                    ps.setInt(2, c.favourite ? 1 : 0);
                    ps.setString(3, c.playerName != null ? c.playerName : "");
                    ps.setString(4, c.regionName != null ? c.regionName : "");
                    ps.setInt(5, c.albumCover ? 1 : 0);
                    ps.setString(6, c.id);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            log.error("Failed to batch-update captures", e);
            try { conn.rollback(); } catch (SQLException ex) { log.error("Rollback failed", ex); }
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ex) { log.error("setAutoCommit failed", ex); }
        }
    }

    public List<CapturedCreature> loadAllCaptures() {
        List<CapturedCreature> result = new ArrayList<>();
        String sql =
            "SELECT id, npc_id, npc_name, npc_combat_level, rarity," +
            " stat_atk, stat_str, stat_def, stat_mag, stat_rng, stat_agi," +
            " capture_time, region_name, capture_level, kills_before_capture," +
            " player_name, nickname, favourite, shiny, album_cover, prayer, observed_hp, rerolled_by" +
            " FROM captures ORDER BY capture_time ASC";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                CreatureQuality quality = new CreatureQuality(
                    rs.getInt("stat_atk"), rs.getInt("stat_str"), rs.getInt("stat_def"),
                    rs.getInt("stat_mag"), rs.getInt("stat_rng"), rs.getInt("stat_agi")
                );
                CapturedCreature c = CapturedCreature.builder()
                    .id(rs.getString("id"))
                    .npcId(rs.getInt("npc_id"))
                    .npcName(rs.getString("npc_name"))
                    .npcCombatLevel(rs.getInt("npc_combat_level"))
                    .rarity(CreatureRarity.valueOf(rs.getString("rarity")))
                    .quality(quality)
                    .captureTime(Instant.ofEpochSecond(rs.getLong("capture_time")))
                    .regionName(rs.getString("region_name"))
                    .captureLevel(rs.getInt("capture_level"))
                    .killsBeforeCapture(rs.getInt("kills_before_capture"))
                    .playerName(rs.getString("player_name"))
                    .shiny(rs.getInt("shiny") == 1)
                    .prayer(rs.getInt("prayer"))
                    .observedHp(rs.getInt("observed_hp"))
                    .rerolledBy(rs.getString("rerolled_by"))
                    .build();
                c.nickname   = rs.getString("nickname");
                c.favourite  = rs.getInt("favourite") == 1;
                c.albumCover = rs.getInt("album_cover") == 1;
                result.add(c);
            }
        } catch (SQLException e) {
            log.error("Failed to load captures", e);
        }
        return result;
    }

    public void deleteAllCaptures() {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM captures");
        } catch (SQLException e) {
            log.error("Failed to delete captures", e);
        }
    }

    public void deleteCapture(String id) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM captures WHERE id=?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to delete capture {}", id, e);
        }
    }

    // -------------------------------------------------------------------------
    // Kill counts
    // -------------------------------------------------------------------------

    public Map<String, Integer> loadKillCounts() {
        Map<String, Integer> result = new HashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT npc_name, kills FROM kill_counts")) {
            while (rs.next()) {
                result.put(rs.getString("npc_name"), rs.getInt("kills"));
            }
        } catch (SQLException e) {
            log.error("Failed to load kill counts", e);
        }
        return result;
    }

    public void upsertKillCount(String npcName, int kills) {
        String sql =
            "INSERT INTO kill_counts (npc_name, kills) VALUES (?,?)" +
            " ON CONFLICT(npc_name) DO UPDATE SET kills=excluded.kills";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, npcName);
            ps.setInt(2, kills);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to upsert kill count for {}", npcName, e);
        }
    }

    public void deleteAllKillCounts() {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM kill_counts");
        } catch (SQLException e) {
            log.error("Failed to delete kill counts", e);
        }
    }

    // -------------------------------------------------------------------------
    // Metadata
    // -------------------------------------------------------------------------

    public void setMetadata(String key, String value) {
        String sql =
            "INSERT INTO metadata (key, value) VALUES (?,?)" +
            " ON CONFLICT(key) DO UPDATE SET value=excluded.value";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to set metadata key={}", key, e);
        }
    }

    public String getMetadata(String key, String defaultValue) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT value FROM metadata WHERE key=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("value");
            }
        } catch (SQLException e) {
            log.error("Failed to get metadata key={}", key, e);
        }
        return defaultValue;
    }

    public void deleteAllMetadata() {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM metadata");
        } catch (SQLException e) {
            log.error("Failed to delete metadata", e);
        }
    }
}
