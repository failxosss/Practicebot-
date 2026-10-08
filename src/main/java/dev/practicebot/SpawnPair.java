package dev.practicebot;

import org.bukkit.Location;

/** One player spawn + one bot spawn. An arena can have several pairs; one is picked at random per fight. */
public record SpawnPair(Location player, Location bot) {
    public boolean valid() {
        return player != null && player.getWorld() != null && bot != null && bot.getWorld() != null;
    }
}
