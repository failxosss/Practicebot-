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
import java.util.concurrent.CompletableFuture;

public final class SessionManager {
    public enum Result { WIN, LOSS, LEFT, SHUTDOWN }

    private final PracticeBotPlugin plugin;
    private final NPCRegistry registry;
    private final Map<UUID, BotSession> sessions = new HashMap<>();
    /** Players whose arena chunks are still loading. */
    private final Set<UUID> starting = new HashSet<>();
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

    /**
     * Picks an arena and a random spawn pair, loads the chunks asynchronously (so a slow server never makes
     * the player wait inside the fight) and only then teleports the player and starts the fight.
     */
    public boolean start(Player p, Kit kit, Difficulty diff) {
        UUID id = p.getUniqueId();
        if (inSession(p) || starting.contains(id)) {
            plugin.msg(p, "already-fighting");
            return false;
        }
        Arena arena = plugin.arenas().pickFor(kit, this);
        if (arena == null) {
            plugin.msg(p, "no-arena", "kit", kit.displayName());
            if (p.hasPermission("practicebot.admin")) plugin.msg(p, "no-arena-admin", "kit", kit.id());
            return false;
        }
        SpawnPair pair = arena.randomPair();
        List<ChunkHold.Ref> refs = ChunkHold.refs(pair);

        CompletableFuture<Void> future;
        try {
            future = ChunkHold.load(plugin, refs);
        } catch (Exception ex) {
            plugin.getLogger().severe("Failed to load arena chunks: " + ex);
            ChunkHold.release(plugin, refs);
            plugin.msg(p, "start-failed");
            return false;
        }
        starting.add(id);
        if (!future.isDone()) plugin.msg(p, "preparing");
        future.whenComplete((v, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            starting.remove(id);
            if (err != null) plugin.getLogger().warning("Chunk preload reported an error: " + err);
            if (!p.isOnline() || inSession(p)) {
                ChunkHold.release(plugin, refs);
                return;
            }
            launch(p, kit, diff, arena, pair, refs);
        }));
        return true;
    }

    private void launch(Player p, Kit kit, Difficulty diff, Arena arena, SpawnPair pair, List<ChunkHold.Ref> refs) {
        BotSession s = new BotSession(plugin, this, p, arena, kit, diff, pair, refs);
        sessions.put(p.getUniqueId(), s);
        try {
            s.begin();
        } catch (Exception ex) {
            plugin.getLogger().severe("Failed to start the fight: " + ex);
            ex.printStackTrace();
            sessions.remove(p.getUniqueId());
            s.cleanup();
            plugin.msg(p, "start-failed");
            refreshVisibility();
            return;
        }
        refreshVisibility();
        // Citizens may spawn the NPC with a small delay, so refresh once more
        Bukkit.getScheduler().runTaskLater(plugin, this::refreshVisibility, 5L);
        plugin.msg(p, "fight-started", "kit", kit.displayName(),
                "difficulty", diff.color() + diff.displayName(), "arena", arena.name());
    }

    public void end(BotSession s, Result result) {
        if (s.isEnded()) return;
        sessions.remove(s.player().getUniqueId());
        Player p = s.player();
        s.cleanup();
        refreshVisibility();
        if (!p.isOnline()) return;
        String sub = ChatColor.GRAY + s.kit().displayName() + " | " + s.difficulty().displayName();
        switch (result) {
            case WIN -> {
                plugin.stats().record(p, true);
                p.sendTitle(ChatColor.GREEN + Lang.t("title.win"), sub, 5, 50, 10);
                plugin.msg(p, "msg.win");
                reward(p, s, true);
            }
            case LOSS -> {
                plugin.stats().record(p, false);
                p.sendTitle(ChatColor.RED + Lang.t("title.loss"), ChatColor.GRAY + Lang.t("sub.loss"), 5, 50, 10);
                plugin.msg(p, "msg.loss");
                reward(p, s, false);
            }
            case LEFT -> plugin.msg(p, "fight-ended");
            default -> { }
        }
    }

    /** Runs the commands from config.yml (rewards.on-win / rewards.on-loss) as console. */
    private void reward(Player p, BotSession s, boolean win) {
        if (!plugin.getConfig().getBoolean("rewards.enabled", false)) return;
        String path = win ? "rewards.on-win" : "rewards.on-loss";
        List<String> cmds = new ArrayList<>();
        cmds.addAll(plugin.getConfig().getStringList(path + ".ALL"));
        cmds.addAll(plugin.getConfig().getStringList(path + "." + s.difficulty().name()));
        cmds.addAll(plugin.getConfig().getStringList(path + "." + s.kit().name()));
        int streak = plugin.stats().streakOf(p.getUniqueId());
        for (String c : cmds) {
            String cmd = c.replace("{player}", p.getName())
                    .replace("{kit}", s.kit().id())
                    .replace("{difficulty}", s.difficulty().name().toLowerCase())
                    .replace("{streak}", String.valueOf(streak));
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            if (cmd.isBlank()) continue;
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            } catch (Exception ex) {
                plugin.getLogger().warning("Reward command failed: " + cmd + " (" + ex.getMessage() + ")");
            }
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
            plugin.msg(p, "restored");
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
