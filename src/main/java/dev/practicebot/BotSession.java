package dev.practicebot;

import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.concurrent.ThreadLocalRandom;

/** One fight: a player vs. their own bot in an arena. */
public final class BotSession {
    private final PracticeBotPlugin plugin;
    private final SessionManager manager;
    private final Player player;
    private final Arena arena;
    private final Kit kit;
    private final Difficulty diff;
    private final File stateFile;

    private NPC npc;
    private PlayerState state;
    private BukkitTask task;
    private boolean ended;

    private int tick;
    private int countdownTicks;
    private int nextAttack;
    private int nextHeal;
    private int strafeUntil;
    private int strafeDir;
    private int healItems;
    private int nextJump;

    // cached config values
    private boolean sprintEnabled;
    private double sprintMultiplier;
    private boolean chaseJump;

    public BotSession(PracticeBotPlugin plugin, SessionManager manager, Player player, Arena arena, Kit kit, Difficulty diff) {
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;
        this.arena = arena;
        this.kit = kit;
        this.diff = diff;
        this.stateFile = new File(plugin.getDataFolder(), "pending/" + player.getUniqueId() + ".yml");
    }

    public Player player() { return player; }
    public Arena arena() { return arena; }
    public Kit kit() { return kit; }
    public Difficulty difficulty() { return diff; }
    public NPC npc() { return npc; }
    public boolean isEnded() { return ended; }
    public boolean isCountingDown() { return countdownTicks > 0; }
    public File stateFile() { return stateFile; }

    public Entity botEntity() {
        return npc != null && npc.isSpawned() ? npc.getEntity() : null;
    }

    // ---------- start ----------

    public void begin() {
        state = PlayerState.capture(player);
        state.save(stateFile);

        for (PotionEffect e : player.getActivePotionEffects()) player.removePotionEffect(e.getType());
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.setCollidable(false);
        kit.giveTo(player);
        arena.playerSpawn().getChunk().load();
        player.teleport(arena.playerSpawn());

        String name = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("bot-name", "&cBot &7({difficulty})")
                        .replace("{difficulty}", diff.displayName()));
        npc = manager.registry().createNPC(EntityType.PLAYER, name);
        npc.setProtected(false);

        String skin = plugin.getConfig().getString("bot-skin", "");
        if (skin != null && !skin.isBlank()) {
            npc.getOrAddTrait(SkinTrait.class).setSkinName(skin);
        }

        Equipment eq = npc.getOrAddTrait(Equipment.class);
        org.bukkit.inventory.ItemStack[] armor = kit.armor();
        eq.set(Equipment.EquipmentSlot.HAND, kit.weapon());
        eq.set(Equipment.EquipmentSlot.BOOTS, armor[0]);
        eq.set(Equipment.EquipmentSlot.LEGGINGS, armor[1]);
        eq.set(Equipment.EquipmentSlot.CHESTPLATE, armor[2]);
        eq.set(Equipment.EquipmentSlot.HELMET, armor[3]);

        arena.botSpawn().getChunk().load();
        npc.spawn(arena.botSpawn());
        Entity e = npc.getEntity();
        if (e instanceof LivingEntity le) {
            le.setHealth(20.0);
            le.setCollidable(false);
        }

        sprintEnabled = plugin.getConfig().getBoolean("bot-sprint", true);
        sprintMultiplier = Math.max(1.0, plugin.getConfig().getDouble("sprint-speed-multiplier", 1.3));
        chaseJump = plugin.getConfig().getBoolean("bot-chase-jump", true);

        healItems = kit.botHealItems();
        countdownTicks = Math.max(0, plugin.getConfig().getInt("countdown-seconds", 3)) * 20;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    // ---------- AI ----------

    private void tick() {
        if (ended) return;
        if (!player.isOnline()) {
            manager.end(this, SessionManager.Result.LEFT);
            return;
        }
        Entity ent = botEntity();
        if (!(ent instanceof LivingEntity bot)) return; // the bot is (re)spawning right now
        tick++;

        if (countdownTicks > 0) {
            if (countdownTicks % 20 == 0) {
                int sec = countdownTicks / 20;
                player.sendTitle(ChatColor.YELLOW + String.valueOf(sec), "", 0, 25, 5);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
            }
            countdownTicks--;
            if (countdownTicks == 0) {
                player.sendTitle(ChatColor.GREEN + "FIGHT!", "", 0, 20, 10);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.4f, 1.5f);
            }
            return;
        }

        if (!bot.getWorld().equals(player.getWorld())) {
            npc.teleport(arena.botSpawn(), PlayerTeleportEvent.TeleportCause.PLUGIN);
            return;
        }

        Location bl = bot.getLocation();
        Location pl = player.getLocation();
        double dist = bl.distance(pl);
        if (dist > 40) {
            npc.teleport(arena.botSpawn(), PlayerTeleportEvent.TeleportCause.PLUGIN);
            return;
        }

        npc.faceLocation(pl.clone().add(0, 1.0, 0));
        tryHeal(bot);

        Navigator nav = npc.getNavigator();
        boolean melee = dist <= diff.reach - 0.4;
        updateSprint(bot, melee);

        if (!melee) {
            if (!nav.isNavigating() || tick % 10 == 0) {
                float speed = (float) (diff.speed * (sprintEnabled ? sprintMultiplier : 1.0));
                nav.getLocalParameters().speedModifier(speed).range(60f);
                nav.setTarget(player, false);
            }
            // bunny-hop while chasing, like a real player sprint-jumping
            if (chaseJump && dist > 4.5) tryJump(bot, bl, pl, 0.10);
        } else {
            if (nav.isNavigating()) nav.cancelNavigation();
            strafe(bot, bl, pl, dist);
            tryJump(bot, bl, pl, 0.05);
        }

        tryAttack(bot, dist);
    }

    /**
     * Sprinting like a player: always sprint while moving/fighting (sprint hits deal extra knockback).
     * While falling in melee the bot stops sprinting so the hit can be a critical hit (vanilla rule).
     */
    private void updateSprint(LivingEntity bot, boolean melee) {
        if (!sprintEnabled || !(bot instanceof Player bp)) return;
        boolean falling = !bot.isOnGround() && bot.getVelocity().getY() < 0;
        bp.setSprinting(!(melee && falling));
    }

    /** Jumps (like a player would) with a small forward push toward the target. */
    private void tryJump(LivingEntity bot, Location bl, Location pl, double forward) {
        if (diff.jumpChance <= 0 || tick < nextJump || !bot.isOnGround()) return;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (rnd.nextDouble() >= diff.jumpChance) return;
        nextJump = tick + 10 + rnd.nextInt(10);

        Vector toPlayer = pl.toVector().subtract(bl.toVector()).setY(0);
        Vector v = bot.getVelocity();
        if (toPlayer.lengthSquared() > 1.0E-4) {
            toPlayer.normalize().multiply(forward);
            v.add(toPlayer);
        }
        v.setY(0.42);
        bot.setVelocity(v);
    }

    private void tryAttack(LivingEntity bot, double dist) {
        if (tick < nextAttack || dist > diff.reach) return;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        nextAttack = tick + Math.max(2, (int) Math.round(20.0 / diff.cps)) + rnd.nextInt(0, 2);

        bot.swingMainHand();
        if (rnd.nextDouble() > diff.accuracy || !bot.hasLineOfSight(player)) return;

        if (bot instanceof Player bp) {
            bp.attack(player);
        } else {
            player.damage(kit.fallbackDamage(), bot);
        }
    }

    private void strafe(LivingEntity bot, Location bl, Location pl, double dist) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (tick >= strafeUntil) {
            strafeUntil = tick + 8 + rnd.nextInt(18);
            strafeDir = rnd.nextDouble() < diff.strafeChance ? (rnd.nextBoolean() ? 1 : -1) : 0;
        }
        Vector toPlayer = pl.toVector().subtract(bl.toVector()).setY(0);
        if (toPlayer.lengthSquared() < 1.0E-4) return;
        toPlayer.normalize();
        Vector perp = new Vector(-toPlayer.getZ(), 0, toPlayer.getX());

        Vector add = perp.multiply(0.09 * strafeDir);
        if (dist < 1.6) add.add(toPlayer.clone().multiply(-0.05)); // do not stick to the player
        Vector v = bot.getVelocity().add(add);
        bot.setVelocity(v);

    }

    private void tryHeal(LivingEntity bot) {
        if (healItems <= 0 || tick < nextHeal || bot.getHealth() > diff.healBelow) return;
        healItems--;
        nextHeal = tick + (int) (diff.healCooldown * 20);
        Location l = bot.getLocation().add(0, 1, 0);
        switch (kit.healType()) {
            case POTION -> {
                bot.setHealth(Math.min(20.0, bot.getHealth() + 6.0));
                bot.getWorld().playSound(l, Sound.ENTITY_SPLASH_POTION_BREAK, 1f, 1f);
                bot.getWorld().spawnParticle(Particle.HEART, l, 6, 0.4, 0.4, 0.4);
            }
            case GAPPLE -> {
                bot.setHealth(Math.min(20.0, bot.getHealth() + 4.0));
                bot.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
                bot.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 0));
                bot.getWorld().playSound(l, Sound.ENTITY_GENERIC_EAT, 1f, 1f);
            }
            default -> { }
        }
    }

    // ---------- end ----------

    /** Removes the bot and restores the player. Called by SessionManager. */
    void cleanup() {
        ended = true;
        if (task != null) task.cancel();
        if (npc != null) {
            try {
                npc.destroy();
            } catch (Exception ignored) { }
        }
        player.setCollidable(true);
        if (player.isOnline() && state != null) {
            state.restore(player);
        }
        if (stateFile.exists()) stateFile.delete();
    }
}
