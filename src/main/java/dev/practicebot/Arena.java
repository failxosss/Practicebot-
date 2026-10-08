package dev.practicebot;

import org.bukkit.Location;

public final class Arena {
    private final String name;
    private Kit kit;
    private Location playerSpawn;
    private Location botSpawn;
    private boolean playerSet;
    private boolean botSet;

    public Arena(String name, Kit kit, Location playerSpawn, Location botSpawn, boolean playerSet, boolean botSet) {
        this.name = name;
        this.kit = kit;
        this.playerSpawn = playerSpawn;
        this.botSpawn = botSpawn;
        this.playerSet = playerSet;
        this.botSet = botSet;
    }

    public String name() { return name; }
    public Kit kit() { return kit; }
    public Location playerSpawn() { return playerSpawn; }
    public Location botSpawn() { return botSpawn; }
    public boolean isPlayerSet() { return playerSet; }
    public boolean isBotSet() { return botSet; }
    public void setKit(Kit kit) { this.kit = kit; }
    public void setPlayerSpawn(Location l) { this.playerSpawn = l; this.playerSet = true; }
    public void setBotSpawn(Location l) { this.botSpawn = l; this.botSet = true; }

    /** True when the kit and both spawns are configured and their worlds are loaded. */
    public boolean isReady() {
        return kit != null
                && playerSet && botSet
                && playerSpawn != null && playerSpawn.getWorld() != null
                && botSpawn != null && botSpawn.getWorld() != null;
    }
}
