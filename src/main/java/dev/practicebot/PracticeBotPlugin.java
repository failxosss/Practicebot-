package dev.practicebot;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class PracticeBotPlugin extends JavaPlugin {
    private static final String PREFIX = ChatColor.translateAlternateColorCodes('&', "&8[&cPracticeBot&8] &r");
    /** Bump this when config.yml changes in an incompatible way. */
    private static final int CONFIG_VERSION = 3;

    private ArenaManager arenas;
    private SessionManager sessions;
    private MenuManager menus;
    private StatsManager stats;

    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("Citizens") == null) {
            getLogger().severe("Citizens not found! PracticeBot needs it to create bot players.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        migrateConfig();
        reloadConfig();
        Lang.init(this);
        Difficulty.loadAll(getConfig());

        arenas = new ArenaManager(this);
        sessions = new SessionManager(this);
        menus = new MenuManager(this);
        stats = new StatsManager(this);

        Bukkit.getPluginManager().registerEvents(new SessionListener(this, sessions), this);
        Bukkit.getPluginManager().registerEvents(menus, this);

        PluginCommand cmd = getCommand("pbot");
        if (cmd != null) {
            PbotCommand handler = new PbotCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PapiHook(this).register();
            getLogger().info("PlaceholderAPI found - placeholders registered (%practicebot_...%).");
        }

        // load arenas only after the worlds are loaded
        Bukkit.getScheduler().runTask(this, () -> arenas.load());
        getLogger().info("PracticeBot enabled.");
    }

    @Override
    public void onDisable() {
        if (sessions != null) sessions.endAll();
        if (stats != null) stats.saveNow();
    }

    /** Reloads config, language files and arenas. */
    public void reloadAll() {
        reloadConfig();
        Lang.init(this);
        Difficulty.loadAll(getConfig());
        arenas.load();
    }

    /** An outdated config.yml is moved to config-old.yml and a fresh one is generated. */
    private void migrateConfig() {
        File f = new File(getDataFolder(), "config.yml");
        if (f.exists() && YamlConfiguration.loadConfiguration(f).getInt("config-version", 1) < CONFIG_VERSION) {
            File old = new File(getDataFolder(), "config-old.yml");
            if (old.exists()) old.delete();
            if (f.renameTo(old)) {
                getLogger().warning("Your config.yml was outdated. It was moved to config-old.yml and a new one was generated.");
            }
        }
        saveDefaultConfig();
    }

    public ArenaManager arenas() { return arenas; }
    public SessionManager sessions() { return sessions; }
    public MenuManager menus() { return menus; }
    public StatsManager stats() { return stats; }

    /** Sends a translated message (by language key) with the plugin prefix. */
    public void msg(CommandSender to, String key, Object... kv) {
        to.sendMessage(PREFIX + Lang.t(key, kv));
    }

    /** Sends an already translated/colored text with the plugin prefix. */
    public void raw(CommandSender to, String text) {
        to.sendMessage(PREFIX + text);
    }
}
