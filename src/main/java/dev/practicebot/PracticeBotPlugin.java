package dev.practicebot;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class PracticeBotPlugin extends JavaPlugin {
    private static final String PREFIX = "&8[&cPracticeBot&8] &r";

    private ArenaManager arenas;
    private SessionManager sessions;
    private MenuManager menus;

    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("Citizens") == null) {
            getLogger().severe("Citizens nenalezen! PracticeBot ho potrebuje pro vytvareni bot-hracu.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        Difficulty.loadAll(getConfig());

        arenas = new ArenaManager(this);
        sessions = new SessionManager(this);
        menus = new MenuManager(this);

        Bukkit.getPluginManager().registerEvents(new SessionListener(this, sessions), this);
        Bukkit.getPluginManager().registerEvents(menus, this);

        PluginCommand cmd = getCommand("pbot");
        if (cmd != null) {
            PbotCommand handler = new PbotCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        // arény nacist az po nacteni svetu
        Bukkit.getScheduler().runTask(this, () -> arenas.load());
        getLogger().info("PracticeBot zapnut.");
    }

    @Override
    public void onDisable() {
        if (sessions != null) sessions.endAll();
    }

    public ArenaManager arenas() { return arenas; }
    public SessionManager sessions() { return sessions; }
    public MenuManager menus() { return menus; }

    public void msg(CommandSender to, String text) {
        to.sendMessage(ChatColor.translateAlternateColorCodes('&', PREFIX + text));
    }
}
