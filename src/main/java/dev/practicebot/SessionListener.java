package dev.practicebot;

import net.citizensnpcs.api.event.NPCSpawnEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.*;

import java.util.List;

public final class SessionListener implements Listener {
    private final PracticeBotPlugin plugin;
    private final SessionManager sessions;

    public SessionListener(PracticeBotPlugin plugin, SessionManager sessions) {
        this.plugin = plugin;
        this.sessions = sessions;
    }

    // ---------- damage isolation ----------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Entity victim = e.getEntity();
        Entity damager = e.getDamager();
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) damager = shooter;

        BotSession victimBot = sessions.byEntity(victim);
        BotSession damagerBot = sessions.byEntity(damager);

        // the bot can only be damaged by its owner
        if (victimBot != null) {
            if (!damager.equals(victimBot.player())) e.setCancelled(true);
            return;
        }
        // the bot can only hit its owner
        if (damagerBot != null) {
            if (!victim.equals(damagerBot.player())) e.setCancelled(true);
            return;
        }
        // a player in a fight cannot hit anyone else; nobody else can hit a player in a fight
        if (damager instanceof Player dp && sessions.inSession(dp)) e.setCancelled(true);
        if (victim instanceof Player vp && sessions.inSession(vp)) e.setCancelled(true);
    }

    // ---------- death = end of the fight (no death screen) ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLethal(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof org.bukkit.entity.LivingEntity le)) return;

        BotSession botSession = sessions.byEntity(le);
        if (botSession != null) {
            if (botSession.isCountingDown()) { e.setCancelled(true); return; }
            if (e.getFinalDamage() >= le.getHealth() + le.getAbsorptionAmount()) {
                e.setCancelled(true);
                sessions.end(botSession, SessionManager.Result.WIN);
            }
            return;
        }
        if (le instanceof Player p) {
            BotSession s = sessions.get(p);
            if (s == null) return;
            if (s.isCountingDown()) { e.setCancelled(true); return; }
            if (e.getFinalDamage() >= p.getHealth() + p.getAbsorptionAmount()) {
                e.setCancelled(true);
                sessions.end(s, SessionManager.Result.LOSS);
            }
        }
    }

    // ---------- Citizens ----------

    @EventHandler
    public void onNpcSpawn(NPCSpawnEvent e) {
        if (sessions.byNpc(e.getNPC()) != null) {
            Bukkit.getScheduler().runTask(plugin, sessions::refreshVisibility);
            Bukkit.getScheduler().runTaskLater(plugin, sessions::refreshVisibility, 3L);
        }
    }

    // ---------- join / quit ----------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        sessions.restorePending(e.getPlayer());
        Bukkit.getScheduler().runTask(plugin, sessions::refreshVisibility);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        BotSession s = sessions.get(e.getPlayer());
        if (s != null) sessions.end(s, SessionManager.Result.LEFT);
        Bukkit.getScheduler().runTask(plugin, sessions::refreshVisibility);
    }

    // ---------- restrictions during a fight ----------

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        BotSession s = sessions.get(e.getPlayer());
        if (s != null && s.isCountingDown() && e.getTo() != null
                && (e.getFrom().getX() != e.getTo().getX() || e.getFrom().getZ() != e.getTo().getZ())) {
            e.setTo(e.getFrom());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (sessions.inSession(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (sessions.inSession(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (sessions.inSession(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (sessions.inSession(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && sessions.inSession(p)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && sessions.inSession(p) && e.getFoodLevel() < p.getFoodLevel()) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        // Block teleport commands during a fight (plugin teleports and ender pearls are allowed)
        if (!sessions.inSession(e.getPlayer())) return;
        PlayerTeleportEvent.TeleportCause c = e.getCause();
        if (c == PlayerTeleportEvent.TeleportCause.COMMAND) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!sessions.inSession(p) || p.hasPermission("practicebot.admin")) return;
        String msg = e.getMessage().toLowerCase();
        String label = msg.substring(1).split(" ")[0];
        if (label.equals("pbot") || label.equals("practicebot") || label.equals("pb")) return;
        List<String> allowed = plugin.getConfig().getStringList("allowed-commands");
        for (String a : allowed) if (a.equalsIgnoreCase(label)) return;
        e.setCancelled(true);
        plugin.msg(p, "&cYou cannot use commands during a fight. End it with &e/pbot leave&c.");
    }
}
