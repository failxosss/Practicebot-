package dev.practicebot;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/** Sidebar shown during a fight. The player's previous scoreboard is restored afterwards. */
public final class FightBoard {
    private final Player player;
    private final Scoreboard previous;
    private final Scoreboard board;
    private final Objective objective;
    private final Team[] teams;
    private final String[] entries;

    public FightBoard(Player player, String title, int lines) {
        this.player = player;
        this.previous = player.getScoreboard();
        this.board = Bukkit.getScoreboardManager().getNewScoreboard();
        this.objective = board.registerNewObjective("pbot", Criteria.DUMMY, c(title));
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        this.teams = new Team[lines];
        this.entries = new String[lines];
        for (int i = 0; i < lines; i++) {
            entries[i] = "\u00a7" + Integer.toHexString(i) + "\u00a7r";
            teams[i] = board.registerNewTeam("l" + i);
            teams[i].addEntry(entries[i]);
            objective.getScore(entries[i]).setScore(lines - i);
        }
    }

    private static Component c(String legacy) {
        return LegacyComponentSerializer.legacySection().deserialize(legacy);
    }

    public void show() { player.setScoreboard(board); }

    public void hide() {
        if (player.isOnline()) player.setScoreboard(previous);
    }

    public void setLine(int i, String text) {
        if (i < 0 || i >= teams.length) return;
        teams[i].prefix(c(text));
    }
}
