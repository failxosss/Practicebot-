package dev.practicebot;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.MemoryNPCDataStore;
import net.citizensnpcs.api.npc.NPCRegistry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;

public final class SessionManager {
    public enum Result { WIN, LOSS, LEFT, SHUTDOWN }

    private final PracticeBotPlugin plugin;
    private final NPCRegistry registry;
    private final Map<UUID, BotSession> sessions = new HashMap<>();
    /** Pairs "a>b" for which we called hidePlayer (so we do not break other plugins' vanish). */
    private final Set<String> hiddenPairs = new HashSet<>();

    public SessionManager(PracticeBotPlugin plugin) {
        this.plugin = plugin;
        // Anonymous registry = bots are not saved to Citizens saves.yml
        this.registry = CitizensAPI.createAnonymousNPCRegistry(new MemoryNPCDataStore());
    }

    public NPCRegistry registry() { return registry; }
    public BotSession get(Player p) { return sessions.get(p.getUniqueId()); }
    public boolean inSession(Player p) { return sessions.containsKey(p.getUniqueId()); }
    public Collection<BotSession> all() { return sessions.values(); }

    public int countIn(Arena arena) {
        int c = 0;
        for (BotSession s : sessions.values()) if (s.arena() == arena) c++;
        return c;
    }

    public BotSession byEntity(Entity e) {
        if (e == null) return null;
        for (BotSession s : sessions.values()) {
            Entity be = s.botEntity();
            if (be != null && be.getUniqueId().equals(e.getUniqueId())) return s;
        }
        return null;
    }

    public BotSession byNpc(net.citizensnpcs.api.npc.NPC npc) {
        for (BotSession s : sessions.values()) if (s.npc() == npc) return s;
        return null;
    }

    public boolean start(Player p, Kit kit, Difficulty diff) {
        if (inSession(p)) {
            plugin.msg(p, "&cYou are already in a fight. End it with &e/pbot leave&c.");
            return false;
        }
        Arena arena = plugin.arenas().pickFor(kit, this);
        if (arena == null) {
            plugin.msg(p, "&cThere is no arena for kit &e" + kit.displayName() + "&c yet.");
            if (p.hasPermission("practicebot.admin")) {
                plugin.msg(p, "&6Next step: &fstand in the arena and run &e/pbot arena create <name> " + kit.id());
            }
            return false;
        }
        BotSession s = new BotSession(plugin, this, p, arena, kit, diff);
        sessions.put(p.getUniqueId(), s);
        try {
            s.begin();
        } catch (Exception ex) {
            plugin.getLogger().severe("Failed to start the fight: " + ex);
            ex.printStackTrace();
            sessions.remove(p.getUniqueId());
            s.cleanup();
            plugin.msg(p, "&cThe fight could not be started (see console).");
            refreshVisibility();
            return false;
        }
        refreshVisibility();
        // Citizens may spawn the NPC with a small delay, so refresh once more
        Bukkit.getScheduler().runTaskLater(plugin, this::refreshVisibility, 5L);
        plugin.msg(p, "&aFight: &e" + kit.displayName() + " &7| " + diff.color() + diff.displayName()
                + " &7| arena &e" + arena.name());
        return true;
    }

    public void end(BotSession s, Result result) {
        if (s.isEnded()) return;
        sessions.remove(s.player().getUniqueId());
        Player p = s.player();
        s.cleanup();
        refreshVisibility();
        if (!p.isOnline()) return;
        switch (result) {
            case WIN -> {
                p.sendTitle(ChatColor.GREEN + "YOU WON", ChatColor.GRAY + s.kit().displayName() + " | " + s.difficulty().displayName(), 5, 50, 10);
                plugin.msg(p, "&aYou defeated the bot!");
            }
            case LOSS -> {
                p.sendTitle(ChatColor.RED + "YOU LOST", ChatColor.GRAY + "Try again", 5, 50, 10);
                plugin.msg(p, "&cThe bot defeated you.");
            }
            case LEFT -> plugin.msg(p, "&7Fight ended.");
            default -> { }
        }
    }

    public void endAll() {
        for (BotSession s : new ArrayList<>(sessions.values())) end(s, Result.SHUTDOWN);
        showEveryone();
    }

    /** If a crash hit the player mid-fight, give their items back on the next login. */
    public void restorePending(Player p) {
        File f = new File(plugin.getDataFolder(), "pending/" + p.getUniqueId() + ".yml");
        if (!f.exists() || inSession(p)) return;
        PlayerState st = PlayerState.load(f);
        if (st != null) {
            st.restore(p);
            plugin.msg(p, "&7Your inventory from the interrupted fight was restored.");
        }
        f.delete();
    }

    // ---------- visibility ----------

    /**
     * A player in a fight sees only themselves and their own bot. Other players see neither
     * them nor their bot. Players outside fights see each other normally.
     */
    public void refreshVisibility() {
        List<? extends Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (Player a : online) {
            for (Player b : online) {
                if (a.equals(b)) continue;
                boolean shouldSee = !(inSession(a) || inSession(b));
                String key = a.getUniqueId() + ">" + b.getUniqueId();
                if (shouldSee) {
                    if (hiddenPairs.remove(key)) a.showPlayer(plugin, b);
                } else {
                    if (hiddenPairs.add(key)) a.hidePlayer(plugin, b);
                }
            }
        }
        for (BotSession s : sessions.values()) {
            Entity e = s.botEntity();
            if (e == null) continue;
            for (Player o : online) {
                if (o.equals(s.player())) o.showEntity(plugin, e);
                else o.hideEntity(plugin, e);
            }
        }
    }

    private void showEveryone() {
        List<? extends Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (Player a : online) {
            for (Player b : online) {
                if (a.equals(b)) continue;
                if (hiddenPairs.remove(a.getUniqueId() + ">" + b.getUniqueId())) a.showPlayer(plugin, b);
            }
        }
    }
}
