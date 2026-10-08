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
                    plugin.msg(p, "&cUz jsi v souboji. Ukonci ho pres &e/pbot leave&c.");
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
                plugin.msg(sender, "&aPlugin znovu nacten.");
            }
            default -> help(sender);
        }
        return true;
    }

    private void play(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.msg(sender, "&cJen pro hrace.");
            return;
        }
        if (!p.hasPermission("practicebot.play")) {
            noPerm(sender);
            return;
        }
        if (args.length < 3) {
            if (plugin.sessions().inSession(p)) {
                plugin.msg(p, "&cUz jsi v souboji.");
                return;
            }
            plugin.menus().openKits(p);
            return;
        }
        Kit kit = Kit.fromString(args[1]);
        Difficulty diff = Difficulty.fromString(args[2]);
        if (kit == null) {
            plugin.msg(p, "&cNeznamy kit. Dostupne: &e" + kitNames());
            return;
        }
        if (diff == null) {
            plugin.msg(p, "&cNeznama obtiznost. Dostupne: &enormal, medium, hard, professional");
            return;
        }
        plugin.sessions().start(p, kit, diff);
    }

    private void leave(CommandSender sender) {
        if (!(sender instanceof Player p)) return;
        BotSession s = plugin.sessions().get(p);
        if (s == null) {
            plugin.msg(p, "&cNejsi v souboji.");
            return;
        }
        plugin.sessions().end(s, SessionManager.Result.LEFT);
    }

    private void kits(CommandSender sender) {
        plugin.msg(sender, "&7Kity:");
        for (Kit k : Kit.values()) {
            plugin.msg(sender, " &e" + k.id() + " &7- " + k.description() + " &8(arén: " + plugin.arenas().forKit(k).size() + ")");
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
                    plugin.msg(sender, "&7Zadne arény. Vytvor: &e/pbot arena create <nazev> <kit>");
                    return;
                }
                for (Arena a : am.all()) {
                    plugin.msg(sender, " &e" + a.name() + " &7- kit &b" + a.kit().displayName()
                            + " &7- svet &f" + a.playerSpawn().getWorld().getName());
                }
            }
            case "create" -> {
                if (!(sender instanceof Player p)) { plugin.msg(sender, "&cJen pro hrace."); return; }
                if (args.length < 4) { plugin.msg(p, "&cPouziti: /pbot arena create <nazev> <kit>"); return; }
                String name = args[2];
                if (!name.matches("[A-Za-z0-9_\\-]{1,24}")) {
                    plugin.msg(p, "&cNazev muze obsahovat jen pismena, cisla, _ a -.");
                    return;
                }
                if (am.exists(name)) { plugin.msg(p, "&cArena s timto nazvem uz existuje."); return; }
                Kit kit = Kit.fromString(args[3]);
                if (kit == null) { plugin.msg(p, "&cNeznamy kit. Dostupne: &e" + kitNames()); return; }
                Location loc = p.getLocation();
                am.add(new Arena(name, kit, loc.clone(), loc.clone()));
                plugin.msg(p, "&aArena &e" + name + " &avytvorena pro kit &b" + kit.displayName() + "&a.");
                plugin.msg(p, "&7Ted nastav spawny: &e/pbot arena setplayer " + name + " &7a &e/pbot arena setbot " + name);
            }
            case "setplayer", "setbot" -> {
                if (!(sender instanceof Player p)) { plugin.msg(sender, "&cJen pro hrace."); return; }
                if (args.length < 3) { plugin.msg(p, "&cPouziti: /pbot arena " + sub + " <nazev>"); return; }
                Arena a = am.get(args[2]);
                if (a == null) { plugin.msg(p, "&cArena neexistuje."); return; }
                if (sub.equals("setplayer")) a.setPlayerSpawn(p.getLocation().clone());
                else a.setBotSpawn(p.getLocation().clone());
                am.save();
                plugin.msg(p, "&aSpawn &e" + (sub.equals("setplayer") ? "hrace" : "bota") + " &anastaven pro arenu &e" + a.name() + "&a.");
            }
            case "setkit" -> {
                if (args.length < 4) { plugin.msg(sender, "&cPouziti: /pbot arena setkit <nazev> <kit>"); return; }
                Arena a = am.get(args[2]);
                Kit kit = Kit.fromString(args[3]);
                if (a == null) { plugin.msg(sender, "&cArena neexistuje."); return; }
                if (kit == null) { plugin.msg(sender, "&cNeznamy kit."); return; }
                a.setKit(kit);
                am.save();
                plugin.msg(sender, "&aArena &e" + a.name() + " &anyni pouziva kit &b" + kit.displayName() + "&a.");
            }
            case "delete", "remove" -> {
                if (args.length < 3) { plugin.msg(sender, "&cPouziti: /pbot arena delete <nazev>"); return; }
                for (BotSession s : new ArrayList<>(plugin.sessions().all())) {
                    if (s.arena().name().equalsIgnoreCase(args[2])) plugin.sessions().end(s, SessionManager.Result.LEFT);
                }
                plugin.msg(sender, am.remove(args[2]) ? "&aArena smazana." : "&cArena neexistuje.");
            }
            case "tp" -> {
                if (!(sender instanceof Player p)) { plugin.msg(sender, "&cJen pro hrace."); return; }
                if (args.length < 3) { plugin.msg(p, "&cPouziti: /pbot arena tp <nazev>"); return; }
                Arena a = am.get(args[2]);
                if (a == null) { plugin.msg(p, "&cArena neexistuje."); return; }
                p.teleport(a.playerSpawn());
            }
            default -> arenaHelp(sender);
        }
    }

    private void arenaHelp(CommandSender s) {
        plugin.msg(s, "&7Spravce arén:");
        plugin.msg(s, " &e/pbot arena create <nazev> <kit>");
        plugin.msg(s, " &e/pbot arena setplayer <nazev> &7- spawn hrace (tam kde stojis)");
        plugin.msg(s, " &e/pbot arena setbot <nazev> &7- spawn bota");
        plugin.msg(s, " &e/pbot arena setkit <nazev> <kit>");
        plugin.msg(s, " &e/pbot arena delete <nazev>");
        plugin.msg(s, " &e/pbot arena list &7| &e/pbot arena tp <nazev>");
    }

    private void help(CommandSender s) {
        plugin.msg(s, "&7PracticeBot prikazy:");
        plugin.msg(s, " &e/pbot &7- otevre GUI");
        plugin.msg(s, " &e/pbot play <kit> <obtiznost> &7- rychly start");
        plugin.msg(s, " &e/pbot leave &7- ukonci souboj");
        plugin.msg(s, " &e/pbot kits &7- seznam kitu");
        if (s.hasPermission("practicebot.admin")) {
            plugin.msg(s, " &e/pbot arena ... &7- sprava arén");
            plugin.msg(s, " &e/pbot reload");
        }
    }

    private boolean noPerm(CommandSender s) {
        plugin.msg(s, "&cNemas opravneni.");
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
                out.addAll(List.of("create", "setplayer", "setbot", "setkit", "delete", "list", "tp"));
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
