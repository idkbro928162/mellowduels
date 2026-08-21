package net.mellowsmp.duels.managers;

import net.mellowsmp.duels.MellowDuels;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

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
 * database (or MySQL if configured).
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

    public record LeaderboardEntry(UUID uuid, String name, int wins) {
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
            if (configManager.isMysql()) {
                try {
                    Class.forName("com.mysql.cj.jdbc.Driver");
                } catch (ClassNotFoundException ignored) {
                    Class.forName("com.mysql.jdbc.Driver");
                }
                String host = configManager.raw().getString("storage.mysql.host");
                int port = configManager.raw().getInt("storage.mysql.port");
                String db = configManager.raw().getString("storage.mysql.database");
                String user = configManager.raw().getString("storage.mysql.username");
                String pass = configManager.raw().getString("storage.mysql.password");
                String url = "jdbc:mysql://" + host + ":" + port + "/" + db
                        + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8";
                connection = DriverManager.getConnection(url, user, pass);
            } else {
                Class.forName("org.sqlite.JDBC");
                File dbFile = new File(plugin.getDataFolder(), "stats.db");
                File parent = dbFile.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    plugin.getLogger().warning("Could not create plugin data folder for stats.db");
                }
                connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            }
            createTables();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect to statistics database: " + e.getMessage());
            connection = null;
        }
    }

    private void createTables() throws SQLException {
        if (connection == null) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS player_stats (" +
                        "uuid VARCHAR(36) NOT NULL, kit VARCHAR(64) NOT NULL, wins INT DEFAULT 0, losses INT DEFAULT 0, " +
                        "kills INT DEFAULT 0, deaths INT DEFAULT 0, matches INT DEFAULT 0, " +
                        "current_streak INT DEFAULT 0, best_streak INT DEFAULT 0, total_duration_ms BIGINT DEFAULT 0, " +
                        "PRIMARY KEY (uuid, kit))")) {
            ps.executeUpdate();
        }
    }

    /** Records the result of a finished duel for both players, split per-kit and combined ("__all__"). */
    public void recordResult(UUID winner, UUID loser, String kit, long durationMillis) {
        if (connection == null || winner == null || loser == null) {
            return;
        }
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
        int kills = existing.kills + (won ? 1 : 0);
        int deaths = existing.deaths + (won ? 0 : 1);
        int matches = existing.matches + 1;
        int currentStreak = won ? existing.currentStreak + 1 : 0;
        int bestStreak = Math.max(existing.bestStreak, currentStreak);
        long totalDuration = existing.totalDurationMillis + durationMillis;

        String sql;
        if (configManager.isMysql()) {
            sql = "INSERT INTO player_stats (uuid, kit, wins, losses, kills, deaths, matches, current_streak, best_streak, total_duration_ms) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE wins=?, losses=?, kills=?, deaths=?, matches=?, current_streak=?, best_streak=?, total_duration_ms=?";
        } else {
            sql = "INSERT INTO player_stats (uuid, kit, wins, losses, kills, deaths, matches, current_streak, best_streak, total_duration_ms) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT(uuid, kit) DO UPDATE SET wins=?, losses=?, kills=?, deaths=?, matches=?, current_streak=?, best_streak=?, total_duration_ms=?";
        }

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
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
            ps.setInt(11, wins);
            ps.setInt(12, losses);
            ps.setInt(13, kills);
            ps.setInt(14, deaths);
            ps.setInt(15, matches);
            ps.setInt(16, currentStreak);
            ps.setInt(17, bestStreak);
            ps.setLong(18, totalDuration);
            ps.executeUpdate();
        }
    }

    public PlayerStats getStats(UUID uuid, String kit) {
        PlayerStats stats = new PlayerStats();
        if (connection == null || uuid == null) {
            return stats;
        }
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
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load stats: " + e.getMessage());
        }
        return stats;
    }

    /** Basic leaderboard query: top players by wins for a given kit ("__all__" for overall). */
    public List<LeaderboardEntry> topByWins(String kit, int limit) {
        List<LeaderboardEntry> results = new ArrayList<>();
        if (connection == null) {
            return results;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT uuid, wins FROM player_stats WHERE kit = ? ORDER BY wins DESC LIMIT ?")) {
            ps.setString(1, kit);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
                    String name = offline.getName() != null ? offline.getName() : uuid.toString();
                    results.add(new LeaderboardEntry(uuid, name, rs.getInt("wins")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to query leaderboard: " + e.getMessage());
        }
        return results;
    }

    public void close() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException ignored) {
        } finally {
            connection = null;
        }
    }
}
