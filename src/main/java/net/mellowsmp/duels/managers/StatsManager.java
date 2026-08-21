package net.mellowsmp.duels.managers;

import net.mellowsmp.duels.BasedDuels;

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

    private final BasedDuels plugin;
    private final ConfigManager configManager;
    private Connection connection;
    private boolean available;

    public StatsManager(BasedDuels plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public boolean init() {
        try {
            if ("MYSQL".equalsIgnoreCase(configManager.storageType())) {
                String host = configManager.raw().getString("storage.mysql.host");
                int port = configManager.raw().getInt("storage.mysql.port");
                String db = configManager.raw().getString("storage.mysql.database");
                String user = configManager.raw().getString("storage.mysql.username");
                String pass = configManager.raw().getString("storage.mysql.password");
                String url = "jdbc:mysql://" + host + ":" + port + "/" + db
                        + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC";
                connection = DriverManager.getConnection(url, user, pass);
            } else {
                if (!"SQLITE".equals(configManager.storageType())) {
                    throw new SQLException("Unsupported storage.type: " + configManager.storageType());
                }
                if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                    throw new SQLException("Could not create plugin data folder");
                }
                File dbFile = new File(plugin.getDataFolder(), "stats.db");
                connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
                try (PreparedStatement ps = connection.prepareStatement("PRAGMA busy_timeout = 5000")) {
                    ps.execute();
                }
            }
            createTables();
            available = true;
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to connect to statistics database: " + e.getMessage());
            close();
            return false;
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
    public synchronized void recordResult(UUID winner, UUID loser, String kit, long durationMillis) {
        if (!isAvailable()) {
            return;
        }
        boolean originalAutoCommit = true;
        try {
            originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            upsertResult(winner, kit, true, durationMillis);
            upsertResult(winner, "__all__", true, durationMillis);
            upsertResult(loser, kit, false, durationMillis);
            upsertResult(loser, "__all__", false, durationMillis);
            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackError) {
                e.addSuppressed(rollbackError);
            }
            plugin.getLogger().warning("Failed to record duel result: " + e.getMessage());
        } finally {
            try {
                connection.setAutoCommit(originalAutoCommit);
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to restore database transaction state: " + e.getMessage());
            }
        }
    }

    private void upsertResult(UUID uuid, String kit, boolean won, long durationMillis) throws SQLException {
        PlayerStats existing = queryStats(uuid, kit);
        int wins = existing.wins + (won ? 1 : 0);
        int losses = existing.losses + (won ? 0 : 1);
        int kills = existing.kills + (won ? 1 : 0);
        int deaths = existing.deaths + (won ? 0 : 1);
        int matches = existing.matches + 1;
        int currentStreak = won ? existing.currentStreak + 1 : 0;
        int bestStreak = Math.max(existing.bestStreak, currentStreak);
        long totalDuration = existing.totalDurationMillis + Math.max(0, durationMillis);

        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE player_stats SET wins=?, losses=?, kills=?, deaths=?, matches=?, current_streak=?, " +
                        "best_streak=?, total_duration_ms=? WHERE uuid=? AND kit=?")) {
            ps.setInt(1, wins);
            ps.setInt(2, losses);
            ps.setInt(3, kills);
            ps.setInt(4, deaths);
            ps.setInt(5, matches);
            ps.setInt(6, currentStreak);
            ps.setInt(7, bestStreak);
            ps.setLong(8, totalDuration);
            ps.setString(9, uuid.toString());
            ps.setString(10, kit);
            if (ps.executeUpdate() > 0) {
                return;
            }
        }

        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO player_stats (uuid, kit, wins, losses, kills, deaths, matches, current_streak, " +
                        "best_streak, total_duration_ms) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, kit);
            ps.setInt(3, wins);
            ps.setInt(4, losses);
            ps.setInt(5, kills);
            ps.setInt(6, deaths);
            ps.setInt(7, matches);
            ps.setInt(8, currentStreak);
            ps.setInt(9, bestStreak);
            ps.setLong(10, totalDuration);
            ps.executeUpdate();
        }
    }

    public synchronized PlayerStats getStats(UUID uuid, String kit) {
        if (!isAvailable()) {
            return new PlayerStats();
        }
        try {
            return queryStats(uuid, kit);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load stats: " + e.getMessage());
            return new PlayerStats();
        }
    }

    private PlayerStats queryStats(UUID uuid, String kit) throws SQLException {
        PlayerStats stats = new PlayerStats();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM player_stats WHERE uuid = ? AND kit = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, kit);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    stats.wins = rs.getInt("wins");
                    stats.losses = rs.getInt("losses");
                    stats.kills = rs.getInt("kills");
                    stats.deaths = rs.getInt("deaths");
                    stats.matches = rs.getInt("matches");
                    stats.currentStreak = rs.getInt("current_streak");
                    stats.bestStreak = rs.getInt("best_streak");
                    stats.totalDurationMillis = rs.getLong("total_duration_ms");
                }
            }
        }
        return stats;
    }

    /** Basic leaderboard query: top players by wins for a given kit ("__all__" for overall). */
    public synchronized List<Object[]> topByWins(String kit, int limit) {
        List<Object[]> results = new ArrayList<>();
        if (!isAvailable()) {
            return results;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT uuid, wins FROM player_stats WHERE kit = ? ORDER BY wins DESC, uuid ASC LIMIT ?")) {
            ps.setString(1, kit);
            ps.setInt(2, Math.max(1, Math.min(limit, 100)));
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

    public synchronized boolean isAvailable() {
        if (!available || connection == null) {
            return false;
        }
        try {
            return !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized void close() {
        available = false;
        try {
            if (connection != null) connection.close();
        } catch (SQLException ignored) {
        } finally {
            connection = null;
        }
    }
}
