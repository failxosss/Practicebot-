package dev.practicebot;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

/** Win/loss statistics per player, stored in stats.yml (autosaved asynchronously). */
public final class StatsManager {
    public static final class Entry {
        public String name = "?";
        public int wins;
        public int losses;
        public int streak;
        public int best;

        public int fights() { return wins + losses; }
        public int winRate() { return fights() == 0 ? 0 : (int) Math.round(wins * 100.0 / fights()); }
    }

    private final PracticeBotPlugin plugin;
    private final File file;
    private final Map<UUID, Entry> map = new HashMap<>();
    private boolean dirty;
    private BukkitTask timer;

    public StatsManager(PracticeBotPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
        load();
        timer = Bukkit.getScheduler().runTaskTimer(plugin, this::saveAsync, 1200L, 1200L);
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String key : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(key);
            if (s == null) continue;
            try {
                Entry e = new Entry();
                e.name = s.getString("name", "?");
                e.wins = s.getInt("wins");
                e.losses = s.getInt("losses");
                e.streak = s.getInt("streak");
                e.best = s.getInt("best");
                map.put(UUID.fromString(key), e);
            } catch (IllegalArgumentException ignored) { }
        }
    }

    public Entry get(UUID id) { return map.get(id); }

    public int streakOf(UUID id) {
        Entry e = map.get(id);
        return e == null ? 0 : e.streak;
    }

    public void record(Player p, boolean win) {
        Entry e = map.computeIfAbsent(p.getUniqueId(), k -> new Entry());
        e.name = p.getName();
        if (win) {
            e.wins++;
            e.streak++;
            e.best = Math.max(e.best, e.streak);
        } else {
            e.losses++;
            e.streak = 0;
        }
        dirty = true;
    }

    public Entry findByName(String name) {
        for (Entry e : map.values()) if (e.name.equalsIgnoreCase(name)) return e;
        return null;
    }

    /** Players with at least one win, sorted by wins, then by best streak. */
    public List<Entry> top(int n) {
        return map.values().stream()
                .filter(e -> e.wins > 0)
                .sorted((a, b) -> a.wins != b.wins ? Integer.compare(b.wins, a.wins) : Integer.compare(b.best, a.best))
                .limit(n)
                .collect(Collectors.toList());
    }

    private String serialize() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Entry> en : map.entrySet()) {
            String k = en.getKey().toString();
            Entry e = en.getValue();
            y.set(k + ".name", e.name);
            y.set(k + ".wins", e.wins);
            y.set(k + ".losses", e.losses);
            y.set(k + ".streak", e.streak);
            y.set(k + ".best", e.best);
        }
        return y.saveToString();
    }

    private void write(String data) {
        try {
            file.getParentFile().mkdirs();
            Files.writeString(file.toPath(), data, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save stats.yml: " + ex.getMessage());
        }
    }

    /** Serializes on the main thread, writes the file asynchronously. */
    public void saveAsync() {
        if (!dirty) return;
        dirty = false;
        String data = serialize();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(data));
    }

    /** Synchronous save, used on shutdown. */
    public void saveNow() {
        if (timer != null) timer.cancel();
        if (dirty) {
            dirty = false;
            write(serialize());
        }
    }
}
