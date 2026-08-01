package net.mellowsmp.duels.managers;

import net.mellowsmp.duels.MellowDuels;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tracks per-player and per-kit duel statistics using an embedded SQLite
 * database (or MySQL if configured). Designed so leaderboard queries can be
 * added easily on top of the existing schema.
 */
public class StatsManager {

    public static class PlayerStats {
        public int wins, losses, kills, deaths, matches;
        public int currentStreak, bestStreak;
        public long totalDurationMillis;

        public double winRate() {
            return matches == 0 ? 0 : (wins * 100.0) / matches;
        }

        public long averageDurationMillis() {
            return matches == 0 ? 0 : totalDurationMillis / matches;
        }
    }

    private final MellowDuels plugin;
    private final ConfigManager configManager;
    private Connection connection;

    public StatsManager(MellowDuels plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void init() {
        try {
            if ("MYSQL".equalsIgnoreCase(configManager.storageType())) {
                String host = configManager.raw().getString("storage.mysql.host");
                int port = configManager.raw().getInt("storage.mysql.port");
                String db = configManager.raw().getString("storage.mysql.database");
                String user = configManager.raw().getString("storage.mysql.username");
                String pass = configManager.raw().getString("storage.mysql.password");
                String url = "jdbc:mysql://" + host + ":" + port + "/" + db;
                connection = DriverManager.getConnection(url, user, pass);
            } else {
                File dbFile = new File(plugin.getDataFolder(), "stats.db");
                connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            }
            createTables();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to connect to statistics database: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS player_stats (" +
                        "uuid VARCHAR(36), kit VARCHAR(64), wins INT DEFAULT 0, losses INT DEFAULT 0, " +
                        "kills INT DEFAULT 0, deaths INT DEFAULT 0, matches INT DEFAULT 0, " +
                        "current_streak INT DEFAULT 0, best_streak INT DEFAULT 0, total_duration_ms BIGINT DEFAULT 0, " +
                        "PRIMARY KEY (uuid, kit))")) {
            ps.executeUpdate();
        }
    }

    /** Records the result of a finished duel for both players, split per-kit and combined ("__all__"). */
    public void recordResult(UUID winner, UUID loser, String kit, long durationMillis) {
        try {
            upsertResult(winner, kit, true, durationMillis);
            upsertResult(winner, "__all__", true, durationMillis);
            upsertResult(loser, kit, false, durationMillis);
            upsertResult(loser, "__all__", false, durationMillis);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to record duel result: " + e.getMessage());
        }
    }

    private void upsertResult(UUID uuid, String kit, boolean won, long durationMillis) throws SQLException {
        PlayerStats existing = getStats(uuid, kit);
        int wins = existing.wins + (won ? 1 : 0);
        int losses = existing.losses + (won ? 0 : 1);
        int matches = existing.matches + 1;
        int currentStreak = won ? existing.currentStreak + 1 : 0;
        int bestStreak = Math.max(existing.bestStreak, currentStreak);
        long totalDuration = existing.totalDurationMillis + durationMillis;

        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO player_stats (uuid, kit, wins, losses, matches, current_streak, best_streak, total_duration_ms) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                        "ON CONFLICT(uuid, kit) DO UPDATE SET wins=?, losses=?, matches=?, current_streak=?, best_streak=?, total_duration_ms=?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, kit);
            ps.setInt(3, wins);
            ps.setInt(4, losses);
            ps.setInt(5, matches);
            ps.setInt(6, currentStreak);
            ps.setInt(7, bestStreak);
            ps.setLong(8, totalDuration);
            ps.setInt(9, wins);
            ps.setInt(10, losses);
            ps.setInt(11, matches);
            ps.setInt(12, currentStreak);
            ps.setInt(13, bestStreak);
            ps.setLong(14, totalDuration);
            ps.executeUpdate();
        }
    }

    public PlayerStats getStats(UUID uuid, String kit) {
        PlayerStats stats = new PlayerStats();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM player_stats WHERE uuid = ? AND kit = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, kit);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    stats.wins = rs.getInt("wins");
                    stats.losses = rs.getInt("losses");
                    stats.matches = rs.getInt("matches");
                    stats.currentStreak = rs.getInt("current_streak");
                    stats.bestStreak = rs.getInt("best_streak");
                    stats.totalDurationMillis = rs.getLong("total_duration_ms");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load stats: " + e.getMessage());
        }
        return stats;
    }

    /** Basic leaderboard query: top players by wins for a given kit ("__all__" for overall). */
    public List<Object[]> topByWins(String kit, int limit) {
        List<Object[]> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT uuid, wins FROM player_stats WHERE kit = ? ORDER BY wins DESC LIMIT ?")) {
            ps.setString(1, kit);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new Object[]{rs.getString("uuid"), rs.getInt("wins")});
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to query leaderboard: " + e.getMessage());
        }
        return results;
    }

    public void close() {
        try {
            if (connection != null) connection.close();
        } catch (SQLException ignored) {
        }
    }
}
