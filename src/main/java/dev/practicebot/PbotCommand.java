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

    public PbotCommand(PracticeBotPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p && p.hasPermission("practicebot.play")) {
                if (plugin.sessions().inSession(p)) {
                    plugin.msg(p, "&cYou are already in a fight. End it with &e/pbot leave&c.");
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
            case "arena" -> arena(sender, args);
            case "reload" -> {
                if (!sender.hasPermission("practicebot.admin")) return noPerm(sender);
                plugin.reloadConfig();
                Difficulty.loadAll(plugin.getConfig());
                plugin.arenas().load();
                plugin.msg(sender, "&aPlugin reloaded.");
            }
            default -> help(sender);
        }
        return true;
    }

    private void play(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.msg(sender, "&cPlayers only.");
            return;
        }
        if (!p.hasPermission("practicebot.play")) {
            noPerm(sender);
            return;
        }
        if (args.length < 3) {
            if (plugin.sessions().inSession(p)) {
                plugin.msg(p, "&cYou are already in a fight.");
                return;
            }
            plugin.menus().openKits(p);
            return;
        }
        Kit kit = Kit.fromString(args[1]);
        Difficulty diff = Difficulty.fromString(args[2]);
        if (kit == null) {
            plugin.msg(p, "&cUnknown kit. Available: &e" + kitNames());
            return;
        }
        if (diff == null) {
            plugin.msg(p, "&cUnknown difficulty. Available: &enormal, medium, hard, professional");
            return;
        }
        plugin.sessions().start(p, kit, diff);
    }

    private void leave(CommandSender sender) {
        if (!(sender instanceof Player p)) return;
        BotSession s = plugin.sessions().get(p);
        if (s == null) {
            plugin.msg(p, "&cYou are not in a fight.");
            return;
        }
        plugin.sessions().end(s, SessionManager.Result.LEFT);
    }

    private void kits(CommandSender sender) {
        plugin.msg(sender, "&7Kits:");
        for (Kit k : Kit.values()) {
            plugin.msg(sender, " &e" + k.id() + " &7- " + k.description() + " &8(arenas: " + plugin.arenas().forKit(k).size() + ")");
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
                    plugin.msg(sender, "&7No arenas yet.");
                    plugin.msg(sender, "&6Next step: &fstand in the arena and run &e/pbot arena create <name> <kit>");
                    return;
                }
                for (Arena a : am.all()) {
                    String status = a.isReady() ? "&aready" : "&cnot finished";
                    plugin.msg(sender, " &e" + a.name() + " &7- kit &b" + a.kit().displayName()
                            + " &7- world &f" + a.playerSpawn().getWorld().getName() + " &7- " + status);
                    if (!a.isReady()) nextStep(sender, a);
                }
            }
            case "info" -> {
                if (args.length < 3) { plugin.msg(sender, "&cUsage: /pbot arena info <name>"); return; }
                Arena a = am.get(args[2]);
                if (a == null) { plugin.msg(sender, "&cArena does not exist."); return; }
                plugin.msg(sender, "&7Arena &e" + a.name() + "&7 | kit &b" + a.kit().displayName()
                        + " &7| player spawn: " + (a.isPlayerSet() ? "&aset" : "&cnot set")
                        + " &7| bot spawn: " + (a.isBotSet() ? "&aset" : "&cnot set"));
                nextStep(sender, a);
            }
            case "create" -> {
                if (!(sender instanceof Player p)) { plugin.msg(sender, "&cPlayers only."); return; }
                if (args.length < 4) { plugin.msg(p, "&cUsage: /pbot arena create <name> <kit>"); return; }
                String name = args[2];
                if (!name.matches("[A-Za-z0-9_\\-]{1,24}")) {
                    plugin.msg(p, "&cThe name may only contain letters, numbers, _ and -.");
                    return;
                }
                if (am.exists(name)) { plugin.msg(p, "&cAn arena with this name already exists."); return; }
                Kit kit = Kit.fromString(args[3]);
                if (kit == null) { plugin.msg(p, "&cUnknown kit. Available: &e" + kitNames()); return; }
                Location loc = p.getLocation();
                Arena a = new Arena(name, kit, loc.clone(), loc.clone(), false, false);
                am.add(a);
                plugin.msg(p, "&aArena &e" + name + " &acreated for kit &b" + kit.displayName() + "&a.");
                nextStep(p, a);
            }
            case "setplayer", "setbot" -> {
                if (!(sender instanceof Player p)) { plugin.msg(sender, "&cPlayers only."); return; }
                if (args.length < 3) { plugin.msg(p, "&cUsage: /pbot arena " + sub + " <name>"); return; }
                Arena a = am.get(args[2]);
                if (a == null) { plugin.msg(p, "&cArena does not exist."); return; }
                boolean isPlayer = sub.equals("setplayer");
                Location other = isPlayer ? a.botSpawn() : a.playerSpawn();
                boolean otherSet = isPlayer ? a.isBotSet() : a.isPlayerSet();
                if (otherSet && other.getWorld() != null && !other.getWorld().equals(p.getWorld())) {
                    plugin.msg(p, "&cBoth spawns must be in the same world (&f" + other.getWorld().getName() + "&c).");
                    return;
                }
                if (isPlayer) a.setPlayerSpawn(p.getLocation().clone());
                else a.setBotSpawn(p.getLocation().clone());
                am.save();
                plugin.msg(p, "&a" + (isPlayer ? "Player" : "Bot") + " spawn set for arena &e" + a.name() + "&a.");
                nextStep(p, a);
            }
            case "setkit" -> {
                if (args.length < 4) { plugin.msg(sender, "&cUsage: /pbot arena setkit <name> <kit>"); return; }
                Arena a = am.get(args[2]);
                Kit kit = Kit.fromString(args[3]);
                if (a == null) { plugin.msg(sender, "&cArena does not exist."); return; }
                if (kit == null) { plugin.msg(sender, "&cUnknown kit. Available: &e" + kitNames()); return; }
                a.setKit(kit);
                am.save();
                plugin.msg(sender, "&aArena &e" + a.name() + " &anow uses kit &b" + kit.displayName() + "&a.");
                nextStep(sender, a);
            }
            case "delete", "remove" -> {
                if (args.length < 3) { plugin.msg(sender, "&cUsage: /pbot arena delete <name>"); return; }
                for (BotSession s : new ArrayList<>(plugin.sessions().all())) {
                    if (s.arena().name().equalsIgnoreCase(args[2])) plugin.sessions().end(s, SessionManager.Result.LEFT);
                }
                plugin.msg(sender, am.remove(args[2]) ? "&aArena deleted." : "&cArena does not exist.");
            }
            case "tp" -> {
                if (!(sender instanceof Player p)) { plugin.msg(sender, "&cPlayers only."); return; }
                if (args.length < 3) { plugin.msg(p, "&cUsage: /pbot arena tp <name>"); return; }
                Arena a = am.get(args[2]);
                if (a == null) { plugin.msg(p, "&cArena does not exist."); return; }
                p.teleport(a.playerSpawn());
            }
            default -> arenaHelp(sender);
        }
    }

    /**
     * Tells the admin exactly what to do next to finish setting up the arena.
     * Called after every arena command that changes an arena.
     */
    private void nextStep(CommandSender to, Arena a) {
        String n = a.name();
        if (!a.isPlayerSet()) {
            plugin.msg(to, "&6Next step &7(1/2)&6: &fstand where the &ePLAYER &fshould spawn and run &e/pbot arena setplayer " + n);
            return;
        }
        if (!a.isBotSet()) {
            plugin.msg(to, "&6Next step &7(2/2)&6: &fgo to the other side of the arena (where the &eBOT &fshould spawn) and run &e/pbot arena setbot " + n);
            return;
        }
        plugin.msg(to, "&a\u2714 Arena &e" + n + " &ais ready and players can use it.");
        plugin.msg(to, "&6Recommended: &fmake sure the arena has a floor and walls/barriers around it. Players cannot break or place blocks in a fight, and falling into the void counts as a loss.");
        plugin.msg(to, "&6Test it: &fstand outside the arena and run &e/pbot play " + a.kit().id() + " normal");

        List<String> missing = new ArrayList<>();
        for (Kit k : Kit.values()) if (plugin.arenas().forKit(k).isEmpty()) missing.add(k.id());
        if (!missing.isEmpty()) {
            plugin.msg(to, "&6Optional: &fthese kits still have no arena: &e" + String.join(", ", missing)
                    + " &f- create one with &e/pbot arena create <name> <kit>");
        } else {
            plugin.msg(to, "&6Optional: &fyou can create more arenas for the same kit - players are spread to the least busy one.");
        }
    }

    private void arenaHelp(CommandSender s) {
        plugin.msg(s, "&7Arena admin:");
        plugin.msg(s, " &e/pbot arena create <name> <kit>");
        plugin.msg(s, " &e/pbot arena setplayer <name> &7- player spawn (where you stand)");
        plugin.msg(s, " &e/pbot arena setbot <name> &7- bot spawn (where you stand)");
        plugin.msg(s, " &e/pbot arena setkit <name> <kit>");
        plugin.msg(s, " &e/pbot arena info <name> &7- shows the next setup step");
        plugin.msg(s, " &e/pbot arena delete <name>");
        plugin.msg(s, " &e/pbot arena list &7| &e/pbot arena tp <name>");
    }

    private void help(CommandSender s) {
        plugin.msg(s, "&7PracticeBot commands:");
        plugin.msg(s, " &e/pbot &7- opens the GUI");
        plugin.msg(s, " &e/pbot play <kit> <difficulty> &7- quick start");
        plugin.msg(s, " &e/pbot leave &7- ends the fight");
        plugin.msg(s, " &e/pbot kits &7- list of kits");
        if (s.hasPermission("practicebot.admin")) {
            plugin.msg(s, " &e/pbot arena ... &7- arena management");
            plugin.msg(s, " &e/pbot reload");
        }
    }

    private boolean noPerm(CommandSender s) {
        plugin.msg(s, "&cYou do not have permission.");
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
            out.addAll(List.of("play", "leave", "kits"));
            if (admin) out.addAll(List.of("arena", "reload"));
        } else if (args[0].equalsIgnoreCase("play")) {
            if (args.length == 2) for (Kit k : Kit.values()) out.add(k.id());
            if (args.length == 3) for (Difficulty d : Difficulty.values()) out.add(d.name().toLowerCase());
        } else if (admin && args[0].equalsIgnoreCase("arena")) {
            if (args.length == 2) {
                out.addAll(List.of("create", "setplayer", "setbot", "setkit", "info", "delete", "list", "tp"));
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
