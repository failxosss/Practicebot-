package dev.practicebot;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public final class PbotCommand implements TabExecutor {
    private final PracticeBotPlugin plugin;
    /** Admins who already saved the player position of a new spawn pair (/pbot arena addspawn). */
    private final Map<UUID, Pending> pending = new HashMap<>();

    private record Pending(String arena, Location playerPos) {}

    public PbotCommand(PracticeBotPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p && p.hasPermission("practicebot.play")) {
                if (plugin.sessions().inSession(p)) {
                    plugin.msg(p, "already-fighting");
                } else {
                    plugin.menus().openKits(p);
                }
            } else {
                help(sender);
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "play" -> play(sender, args);
            case "leave", "quit" -> leave(sender);
            case "kits" -> kits(sender);
            case "stats" -> stats(sender, args);
            case "top" -> top(sender);
            case "arena" -> arena(sender, args);
            case "reload" -> {
                if (!sender.hasPermission("practicebot.admin")) return noPerm(sender);
                plugin.reloadAll();
                plugin.msg(sender, "reloaded");
            }
            default -> help(sender);
        }
        return true;
    }

    private void play(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.msg(sender, "players-only");
            return;
        }
        if (!p.hasPermission("practicebot.play")) {
            noPerm(sender);
            return;
        }
        if (args.length < 3) {
            if (plugin.sessions().inSession(p)) {
                plugin.msg(p, "already-fighting");
                return;
            }
            plugin.menus().openKits(p);
            return;
        }
        Kit kit = Kit.fromString(args[1]);
        Difficulty diff = Difficulty.fromString(args[2]);
        if (kit == null) {
            plugin.msg(p, "unknown-kit", "kits", kitNames());
            return;
        }
        if (diff == null) {
            plugin.msg(p, "unknown-difficulty", "list", "normal, medium, hard, professional");
            return;
        }
        plugin.sessions().start(p, kit, diff);
    }

    private void leave(CommandSender sender) {
        if (!(sender instanceof Player p)) return;
        BotSession s = plugin.sessions().get(p);
        if (s == null) {
            plugin.msg(p, "not-fighting");
            return;
        }
        plugin.sessions().end(s, SessionManager.Result.LEFT);
    }

    private void kits(CommandSender sender) {
        plugin.msg(sender, "kits.header");
        for (Kit k : Kit.values()) {
            plugin.msg(sender, "kits.line", "id", k.id(), "desc", k.description(), "count", plugin.arenas().forKit(k).size());
        }
    }

    // ---------- statistics ----------

    private void stats(CommandSender sender, String[] args) {
        StatsManager.Entry e;
        String name;
        if (args.length >= 2) {
            e = plugin.stats().findByName(args[1]);
            name = e != null ? e.name : args[1];
        } else if (sender instanceof Player p) {
            e = plugin.stats().get(p.getUniqueId());
            name = p.getName();
        } else {
            plugin.msg(sender, "usage", "usage", "/pbot stats <player>");
            return;
        }
        if (e == null || e.fights() == 0) {
            plugin.msg(sender, "stats.none", "player", name);
            return;
        }
        plugin.msg(sender, "stats.header", "player", e.name);
        plugin.msg(sender, "stats.line", "wins", e.wins, "losses", e.losses, "rate", e.winRate(),
                "streak", e.streak, "best", e.best);
    }

    private void top(CommandSender sender) {
        List<StatsManager.Entry> top = plugin.stats().top(10);
        if (top.isEmpty()) {
            plugin.msg(sender, "top.empty");
            return;
        }
        plugin.msg(sender, "top.header");
        int pos = 1;
        for (StatsManager.Entry e : top) {
            plugin.msg(sender, "top.line", "pos", pos++, "player", e.name, "wins", e.wins, "best", e.best);
        }
    }

    // ---------- /pbot arena ... ----------

    private void arena(CommandSender sender, String[] args) {
        if (!sender.hasPermission("practicebot.admin")) {
            noPerm(sender);
            return;
        }
        if (args.length < 2) {
            arenaHelp(sender);
            return;
        }
        String sub = args[1].toLowerCase();
        ArenaManager am = plugin.arenas();

        switch (sub) {
            case "list" -> {
                if (am.all().isEmpty()) {
                    plugin.msg(sender, "arena.none");
                    plugin.msg(sender, "no-arena-admin", "kit", "<kit>");
                    return;
                }
                for (Arena a : am.all()) {
                    plugin.msg(sender, "arena.list-line", "name", a.name(), "kit", a.kit().displayName(),
                            "world", a.playerSpawn().getWorld().getName(), "spawns", a.pairCount(),
                            "status", Lang.t(a.isReady() ? "arena.ready" : "arena.unfinished"));
                    if (!a.isReady()) nextStep(sender, a);
                }
            }
            case "info" -> {
                if (args.length < 3) { plugin.msg(sender, "usage", "usage", "/pbot arena info <name>"); return; }
                Arena a = am.get(args[2]);
                if (a == null) { plugin.msg(sender, "arena.not-found"); return; }
                plugin.msg(sender, "arena.info", "name", a.name(), "kit", a.kit().displayName(),
                        "player", Lang.t(a.isPlayerSet() ? "arena.set" : "arena.not-set"),
                        "bot", Lang.t(a.isBotSet() ? "arena.set" : "arena.not-set"),
                        "spawns", a.pairCount());
                nextStep(sender, a);
            }
            case "create" -> {
                if (!(sender instanceof Player p)) { plugin.msg(sender, "players-only"); return; }
                if (args.length < 4) { plugin.msg(p, "usage", "usage", "/pbot arena create <name> <kit>"); return; }
                String name = args[2];
                if (!name.matches("[A-Za-z0-9_\\-]{1,24}")) { plugin.msg(p, "arena.bad-name"); return; }
                if (am.exists(name)) { plugin.msg(p, "arena.exists"); return; }
                Kit kit = Kit.fromString(args[3]);
                if (kit == null) { plugin.msg(p, "unknown-kit", "kits", kitNames()); return; }
                Location loc = p.getLocation();
                Arena a = new Arena(name, kit, loc.clone(), loc.clone(), false, false);
                am.add(a);
                plugin.msg(p, "arena.created", "name", name, "kit", kit.displayName());
                nextStep(p, a);
            }
            case "setplayer", "setbot" -> {
                if (!(sender instanceof Player p)) { plugin.msg(sender, "players-only"); return; }
                if (args.length < 3) { plugin.msg(p, "usage", "usage", "/pbot arena " + sub + " <name>"); return; }
                Arena a = am.get(args[2]);
                if (a == null) { plugin.msg(p, "arena.not-found"); return; }
                boolean isPlayer = sub.equals("setplayer");
                Location other = isPlayer ? a.botSpawn() : a.playerSpawn();
                boolean otherSet = isPlayer ? a.isBotSet() : a.isPlayerSet();
                if (otherSet && other.getWorld() != null && !other.getWorld().equals(p.getWorld())) {
                    plugin.msg(p, "arena.diff-world", "world", other.getWorld().getName());
                    return;
                }
                if (isPlayer) a.setPlayerSpawn(p.getLocation().clone());
                else a.setBotSpawn(p.getLocation().clone());
                am.save();
                plugin.msg(p, isPlayer ? "arena.spawn-set-player" : "arena.spawn-set-bot", "name", a.name());
                nextStep(p, a);
            }
            case "addspawn" -> addSpawn(sender, args);
            case "clearspawns" -> {
                if (args.length < 3) { plugin.msg(sender, "usage", "usage", "/pbot arena clearspawns <name>"); return; }
                Arena a = am.get(args[2]);
                if (a == null) { plugin.msg(sender, "arena.not-found"); return; }
                a.clearExtras();
                am.save();
                plugin.msg(sender, "arena.pairs-cleared", "name", a.name());
            }
            case "setkit" -> {
                if (args.length < 4) { plugin.msg(sender, "usage", "usage", "/pbot arena setkit <name> <kit>"); return; }
                Arena a = am.get(args[2]);
                Kit kit = Kit.fromString(args[3]);
                if (a == null) { plugin.msg(sender, "arena.not-found"); return; }
                if (kit == null) { plugin.msg(sender, "unknown-kit", "kits", kitNames()); return; }
                a.setKit(kit);
                am.save();
                plugin.msg(sender, "arena.kit-set", "name", a.name(), "kit", kit.displayName());
                nextStep(sender, a);
            }
            case "delete", "remove" -> {
                if (args.length < 3) { plugin.msg(sender, "usage", "usage", "/pbot arena delete <name>"); return; }
                for (BotSession s : new ArrayList<>(plugin.sessions().all())) {
                    if (s.arena().name().equalsIgnoreCase(args[2])) plugin.sessions().end(s, SessionManager.Result.LEFT);
                }
                plugin.msg(sender, am.remove(args[2]) ? "arena.deleted" : "arena.not-found");
            }
            case "tp" -> {
                if (!(sender instanceof Player p)) { plugin.msg(sender, "players-only"); return; }
                if (args.length < 3) { plugin.msg(p, "usage", "usage", "/pbot arena tp <name>"); return; }
                Arena a = am.get(args[2]);
                if (a == null) { plugin.msg(p, "arena.not-found"); return; }
                p.teleport(a.playerSpawn());
            }
            default -> arenaHelp(sender);
        }
    }

    /**
     * Optional extra spawn pair. First call saves the player position, second call (for the same arena)
     * saves the bot position and completes the pair. Fights then start at a random pair.
     */
    private void addSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { plugin.msg(sender, "players-only"); return; }
        if (args.length < 3) { plugin.msg(p, "usage", "usage", "/pbot arena addspawn <name>"); return; }
        Arena a = plugin.arenas().get(args[2]);
        if (a == null) { plugin.msg(p, "arena.not-found"); return; }
        if (!a.isReady()) {
            plugin.msg(p, "arena.finish-first");
            nextStep(p, a);
            return;
        }
        if (!p.getWorld().equals(a.playerSpawn().getWorld())) {
            plugin.msg(p, "arena.diff-world", "world", a.playerSpawn().getWorld().getName());
            return;
        }
        Pending pend = pending.get(p.getUniqueId());
        if (pend == null || !pend.arena().equalsIgnoreCase(a.name())) {
            pending.put(p.getUniqueId(), new Pending(a.name(), p.getLocation().clone()));
            plugin.msg(p, "arena.pair-pending", "name", a.name());
            return;
        }
        pending.remove(p.getUniqueId());
        a.addExtra(new SpawnPair(pend.playerPos(), p.getLocation().clone()));
        plugin.arenas().save();
        plugin.msg(p, "arena.pair-added", "n", a.pairCount(), "name", a.name());
    }

    /**
     * Tells the admin exactly what to do next to finish setting up the arena.
     * Called after every arena command that changes an arena.
     */
    private void nextStep(CommandSender to, Arena a) {
        String n = a.name();
        if (!a.isPlayerSet()) {
            plugin.msg(to, "next.player", "name", n);
            return;
        }
        if (!a.isBotSet()) {
            plugin.msg(to, "next.bot", "name", n);
            return;
        }
        plugin.msg(to, "next.ready", "name", n);
        plugin.msg(to, "next.walls");
        plugin.msg(to, "next.test", "kit", a.kit().id());
        if (a.pairCount() <= 1) plugin.msg(to, "next.random", "name", n);

        List<String> missing = new ArrayList<>();
        for (Kit k : Kit.values()) if (plugin.arenas().forKit(k).isEmpty()) missing.add(k.id());
        if (!missing.isEmpty()) plugin.msg(to, "next.missing", "kits", String.join(", ", missing));
        else plugin.msg(to, "next.more");
    }

    private void arenaHelp(CommandSender s) {
        plugin.msg(s, "arena.help-header");
        for (String line : Lang.list("arena.help")) plugin.raw(s, " " + line);
    }

    private void help(CommandSender s) {
        plugin.msg(s, "help.header");
        for (String line : Lang.list("help.player")) plugin.raw(s, " " + line);
        if (s.hasPermission("practicebot.admin")) {
            for (String line : Lang.list("help.admin")) plugin.raw(s, " " + line);
        }
    }

    private boolean noPerm(CommandSender s) {
        plugin.msg(s, "no-permission");
        return true;
    }

    private String kitNames() {
        return Arrays.stream(Kit.values()).map(Kit::id).collect(Collectors.joining(", "));
    }

    // ---------- tab complete ----------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = sender.hasPermission("practicebot.admin");
        if (args.length == 1) {
            out.addAll(List.of("play", "leave", "kits", "stats", "top"));
            if (admin) out.addAll(List.of("arena", "reload"));
        } else if (args[0].equalsIgnoreCase("play")) {
            if (args.length == 2) for (Kit k : Kit.values()) out.add(k.id());
            if (args.length == 3) for (Difficulty d : Difficulty.values()) out.add(d.name().toLowerCase());
        } else if (args[0].equalsIgnoreCase("stats") && args.length == 2) {
            for (Player p : plugin.getServer().getOnlinePlayers()) out.add(p.getName());
        } else if (admin && args[0].equalsIgnoreCase("arena")) {
            if (args.length == 2) {
                out.addAll(List.of("create", "setplayer", "setbot", "addspawn", "clearspawns", "setkit", "info", "delete", "list", "tp"));
            } else if (args.length == 3 && !args[1].equalsIgnoreCase("create") && !args[1].equalsIgnoreCase("list")) {
                for (Arena a : plugin.arenas().all()) out.add(a.name());
            } else if (args.length == 4 && (args[1].equalsIgnoreCase("create") || args[1].equalsIgnoreCase("setkit"))) {
                for (Kit k : Kit.values()) out.add(k.id());
            }
        }
        String last = args[args.length - 1].toLowerCase();
        return out.stream().filter(s -> s.toLowerCase().startsWith(last)).collect(Collectors.toList());
    }
}
