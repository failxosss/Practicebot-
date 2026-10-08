package dev.practicebot;

import org.bukkit.Location;

public final class Arena {
    private final String name;
    private Kit kit;
    private Location playerSpawn;
    private Location botSpawn;

    public Arena(String name, Kit kit, Location playerSpawn, Location botSpawn) {
        this.name = name;
        this.kit = kit;
        this.playerSpawn = playerSpawn;
        this.botSpawn = botSpawn;
    }

    public String name() { return name; }
    public Kit kit() { return kit; }
    public Location playerSpawn() { return playerSpawn; }
    public Location botSpawn() { return botSpawn; }
    public void setKit(Kit kit) { this.kit = kit; }
    public void setPlayerSpawn(Location l) { this.playerSpawn = l; }
    public void setBotSpawn(Location l) { this.botSpawn = l; }

    public boolean isReady() {
        return kit != null
                && playerSpawn != null && playerSpawn.getWorld() != null
                && botSpawn != null && botSpawn.getWorld() != null;
    }
}
