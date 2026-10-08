package dev.practicebot;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class ArenaManager {
    private final PracticeBotPlugin plugin;
    private final File file;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    public ArenaManager(PracticeBotPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
    }

    public void load() {
        arenas.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String key : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(key);
            if (s == null) continue;
            Kit kit = Kit.fromString(s.getString("kit"));
            Object p = s.get("player");
            Object b = s.get("bot");
            Location pl = p instanceof Location ? (Location) p : null;
            Location bl = b instanceof Location ? (Location) b : null;
            // arenas saved by older versions have no flags -> they were fully configured
            boolean playerSet = s.getBoolean("player-set", true);
            boolean botSet = s.getBoolean("bot-set", true);
            if (kit == null || pl == null || bl == null || pl.getWorld() == null || bl.getWorld() == null) {
                plugin.getLogger().warning("Arena '" + key + "' is broken (missing world, kit or spawn), skipping.");
                continue;
            }
            Arena a = new Arena(key, kit, pl, bl, playerSet, botSet);
            if (!a.isReady()) {
                plugin.getLogger().warning("Arena '" + key + "' is not finished yet - players cannot use it. Run /pbot arena list for the next step.");
            }
            arenas.put(key.toLowerCase(), a);
        }
        plugin.getLogger().info("Loaded arenas: " + arenas.size());
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Arena a : arenas.values()) {
            y.set(a.name() + ".kit", a.kit().name());
            y.set(a.name() + ".player", a.playerSpawn());
            y.set(a.name() + ".bot", a.botSpawn());
            y.set(a.name() + ".player-set", a.isPlayerSet());
            y.set(a.name() + ".bot-set", a.isBotSet());
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save arenas.yml: " + e.getMessage());
        }
    }

    public Arena get(String name) { return name == null ? null : arenas.get(name.toLowerCase()); }
    public boolean exists(String name) { return get(name) != null; }
    public Collection<Arena> all() { return arenas.values(); }

    public void add(Arena a) { arenas.put(a.name().toLowerCase(), a); save(); }
    public boolean remove(String name) {
        boolean r = arenas.remove(name.toLowerCase()) != null;
        if (r) save();
        return r;
    }

    /** Only finished arenas for the given kit. */
    public List<Arena> forKit(Kit kit) {
        List<Arena> out = new ArrayList<>();
        for (Arena a : arenas.values()) if (a.kit() == kit && a.isReady()) out.add(a);
        return out;
    }

    /** Picks the arena for the given kit with the fewest active fights. */
    public Arena pickFor(Kit kit, SessionManager sessions) {
        Arena best = null;
        int bestCount = Integer.MAX_VALUE;
        for (Arena a : forKit(kit)) {
            int c = sessions.countIn(a);
            if (c < bestCount) {
                best = a;
                bestCount = c;
            }
        }
        return best;
    }
}
