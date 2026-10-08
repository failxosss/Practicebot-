package dev.practicebot;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Loads chunks asynchronously and keeps them loaded with plugin tickets, so fights start without waiting. */
public final class ChunkHold {
    private static final int RADIUS = 1;

    public record Ref(World world, int x, int z) {}

    private ChunkHold() {}

    public static List<Ref> refs(SpawnPair pair) {
        Set<Ref> set = new LinkedHashSet<>();
        add(set, pair.player());
        add(set, pair.bot());
        return new ArrayList<>(set);
    }

    private static void add(Set<Ref> set, Location l) {
        if (l == null || l.getWorld() == null) return;
        int cx = l.getBlockX() >> 4;
        int cz = l.getBlockZ() >> 4;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                set.add(new Ref(l.getWorld(), cx + dx, cz + dz));
            }
        }
    }

    /** Must be called on the main thread. Tickets are added immediately, the future completes when all chunks are loaded. */
    @SuppressWarnings("unchecked")
    public static CompletableFuture<Void> load(Plugin plugin, List<Ref> refs) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (Ref r : refs) {
            r.world().addPluginChunkTicket(r.x(), r.z(), plugin);
            futures.add(r.world().getChunkAtAsync(r.x(), r.z(), true));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    public static void release(Plugin plugin, List<Ref> refs) {
        if (refs == null) return;
        for (Ref r : refs) r.world().removePluginChunkTicket(r.x(), r.z(), plugin);
    }
}
