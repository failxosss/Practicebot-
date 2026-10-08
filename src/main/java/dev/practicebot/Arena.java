package dev.practicebot;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class Arena {
    private final String name;
    private Kit kit;
    private Location playerSpawn;
    private Location botSpawn;
    private boolean playerSet;
    private boolean botSet;
    /** Optional extra spawn pairs (the pair from setplayer/setbot is always pair #1). */
    private final List<SpawnPair> extras = new ArrayList<>();
    private int lastPair = -1;

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

    public List<SpawnPair> extras() { return extras; }
    public void addExtra(SpawnPair p) { extras.add(p); }
    public void clearExtras() { extras.clear(); lastPair = -1; }

    /** All usable spawn pairs: the main pair first, then the extras. */
    public List<SpawnPair> allPairs() {
        List<SpawnPair> out = new ArrayList<>();
        SpawnPair main = new SpawnPair(playerSpawn, botSpawn);
        if (main.valid()) out.add(main);
        for (SpawnPair p : extras) if (p.valid()) out.add(p);
        return out;
    }

    public int pairCount() { return allPairs().size(); }

    /** Random spawn pair, never the same one twice in a row (when there are several). */
    public SpawnPair randomPair() {
        List<SpawnPair> all = allPairs();
        if (all.size() <= 1) return all.isEmpty() ? new SpawnPair(playerSpawn, botSpawn) : all.get(0);
        int i;
        do {
            i = ThreadLocalRandom.current().nextInt(all.size());
        } while (i == lastPair);
        lastPair = i;
        return all.get(i);
    }

    /** True when the kit and both spawns are configured and their worlds are loaded. */
    public boolean isReady() {
        return kit != null
                && playerSet && botSet
                && playerSpawn != null && playerSpawn.getWorld() != null
                && botSpawn != null && botSpawn.getWorld() != null;
    }
}
