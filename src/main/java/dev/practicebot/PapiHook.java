package dev.practicebot;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Locale;

/**
 * PlaceholderAPI expansion (only loaded when PlaceholderAPI is installed).
 * %practicebot_wins% %practicebot_losses% %practicebot_fights% %practicebot_streak%
 * %practicebot_beststreak% %practicebot_winrate%
 * %practicebot_top_name_1% %practicebot_top_wins_1% %practicebot_top_best_1%  (positions 1-10)
 */
public final class PapiHook extends PlaceholderExpansion {
    private final PracticeBotPlugin plugin;

    public PapiHook(PracticeBotPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() { return "practicebot"; }

    @Override
    public String getAuthor() { return "Failxos"; }

    @Override
    public String getVersion() { return plugin.getDescription().getVersion(); }

    @Override
    public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String p = params.toLowerCase(Locale.ROOT);
        StatsManager stats = plugin.stats();

        if (p.startsWith("top_")) {
            String[] parts = p.split("_");
            if (parts.length != 3) return null;
            int pos;
            try {
                pos = Integer.parseInt(parts[2]);
            } catch (NumberFormatException ex) {
                return null;
            }
            List<StatsManager.Entry> top = stats.top(10);
            boolean has = pos >= 1 && pos <= top.size();
            switch (parts[1]) {
                case "name": return has ? top.get(pos - 1).name : "-";
                case "wins": return has ? String.valueOf(top.get(pos - 1).wins) : "0";
                case "best": return has ? String.valueOf(top.get(pos - 1).best) : "0";
                default: return null;
            }
        }

        if (player == null) return "";
        StatsManager.Entry e = stats.get(player.getUniqueId());
        if (e == null) e = new StatsManager.Entry();
        switch (p) {
            case "wins": return String.valueOf(e.wins);
            case "losses": return String.valueOf(e.losses);
            case "fights": return String.valueOf(e.fights());
            case "streak": return String.valueOf(e.streak);
            case "beststreak": return String.valueOf(e.best);
            case "winrate": return String.valueOf(e.winRate());
            default: return null;
        }
    }
}
