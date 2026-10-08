package dev.practicebot;

import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.*;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** One fight: a player vs. their own bot in an arena. */
public final class BotSession {
    private static final int EAT_TICKS = 32;
    private static final double EAT_SLOWDOWN = 0.35;

    private final PracticeBotPlugin plugin;
    private final SessionManager manager;
    private final Player player;
    private final Arena arena;
    private final Kit kit;
    private final Difficulty diff;
    private final SpawnPair pair;
    private final List<ChunkHold.Ref> chunks;
    private final File stateFile;

    private NPC npc;
    private Equipment equipment;
    private PlayerState state;
    private BukkitTask task;
    private FightBoard board;
    private boolean ended;

    private int tick;
    private int fightTick;
    private int countdownTicks;
    private int nextAttack;
    private int nextHeal;
    private int strafeUntil;
    private int strafeDir;
    private int healItems;
    private int nextJump;
    private int eatingUntil;      // tick when the golden apple is finished, 0 = not eating
    private int restoreHandAt;    // tick when the weapon goes back into the bot's hand, 0 = nothing to restore
    private int nextPearl;
    private int pearls;
    private final ArrayDeque<Long> clicks = new ArrayDeque<>();

    // cached config values
    private boolean sprintEnabled;
    private double sprintMultiplier;
    private boolean chaseJump;

    public BotSession(PracticeBotPlugin plugin, SessionManager manager, Player player, Arena arena, Kit kit,
                      Difficulty diff, SpawnPair pair, List<ChunkHold.Ref> chunks) {
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;
        this.arena = arena;
        this.kit = kit;
        this.diff = diff;
        this.pair = pair;
        this.chunks = chunks;
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

    /** Called for every arm swing of the player (used for the CPS counter). */
    public void recordClick() {
        clicks.addLast(System.currentTimeMillis());
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
        player.teleport(pair.player()); // chunks are already loaded (see SessionManager.start)

        String name = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("bot-name", "&cBot &7({difficulty})")
                        .replace("{difficulty}", diff.displayName()));
        npc = manager.registry().createNPC(EntityType.PLAYER, name);
        npc.setProtected(false);

        String skin = plugin.getConfig().getString("bot-skin", "");
        if (skin != null && !skin.isBlank()) {
            npc.getOrAddTrait(SkinTrait.class).setSkinName(skin);
        }

        equipment = npc.getOrAddTrait(Equipment.class);
        ItemStack[] armor = kit.armor();
        equipment.set(Equipment.EquipmentSlot.HAND, kit.weapon());
        equipment.set(Equipment.EquipmentSlot.BOOTS, armor[0]);
        equipment.set(Equipment.EquipmentSlot.LEGGINGS, armor[1]);
        equipment.set(Equipment.EquipmentSlot.CHESTPLATE, armor[2]);
        equipment.set(Equipment.EquipmentSlot.HELMET, armor[3]);

        npc.spawn(pair.bot());
        Entity e = npc.getEntity();
        if (e instanceof LivingEntity le) {
            le.setHealth(20.0);
            le.setCollidable(false);
        }

        sprintEnabled = plugin.getConfig().getBoolean("bot-sprint", true);
        sprintMultiplier = Math.max(1.0, plugin.getConfig().getDouble("sprint-speed-multiplier", 1.3));
        chaseJump = plugin.getConfig().getBoolean("bot-chase-jump", true);

        healItems = kit.botHealItems();
        pearls = kit == Kit.NODEBUFF ? 4 : 0;
        nextPearl = 20 * 6;

        if (plugin.getConfig().getBoolean("scoreboard", true)) {
            board = new FightBoard(player, Lang.t("board.title"), 7);
            board.show();
        }

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
        if (board != null && tick % 5 == 0) updateBoard(bot);

        if (countdownTicks > 0) {
            if (countdownTicks % 20 == 0) {
                int sec = countdownTicks / 20;
                player.sendTitle(ChatColor.YELLOW + String.valueOf(sec), "", 0, 25, 5);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
            }
            countdownTicks--;
            if (countdownTicks == 0) {
                player.sendTitle(ChatColor.GREEN + Lang.t("title.fight"), "", 0, 20, 10);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.4f, 1.5f);
            }
            return;
        }
        fightTick++;

        if (!bot.getWorld().equals(player.getWorld())) {
            npc.teleport(pair.bot(), PlayerTeleportEvent.TeleportCause.PLUGIN);
            return;
        }

        Location bl = bot.getLocation();
        Location pl = player.getLocation();
        double dist = bl.distance(pl);
        if (dist > 40) {
            npc.teleport(pair.bot(), PlayerTeleportEvent.TeleportCause.PLUGIN);
            return;
        }

        npc.faceLocation(pl.clone().add(0, 1.0, 0));

        // --- items: eating / throwing / holding ---
        if (eatingUntil > 0 && tick >= eatingUntil) finishEating(bot);
        if (restoreHandAt > 0 && eatingUntil == 0 && tick >= restoreHandAt) {
            setHand(kit.weapon());
            restoreHandAt = 0;
        }
        if (eatingUntil > 0) eatEffects(bot);
        else tryHeal(bot);
        tryPearl(bot, dist);
        boolean eating = eatingUntil > 0;

        // --- movement ---
        Navigator nav = npc.getNavigator();
        boolean melee = dist <= diff.reach - 0.4;
        updateSprint(bot, melee, eating);
        double speed = diff.speed * (sprintEnabled && !eating ? sprintMultiplier : 1.0) * (eating ? EAT_SLOWDOWN : 1.0);

        if (!melee) {
            nav.getLocalParameters().speedModifier((float) speed).range(60f);
            if (!nav.isNavigating() || tick % 10 == 0) nav.setTarget(player, false);
            // bunny-hop while chasing, like a real player sprint-jumping
            if (chaseJump && !eating && dist > 4.5) tryJump(bot, bl, pl, 0.10);
        } else {
            if (nav.isNavigating()) nav.cancelNavigation();
            strafe(bot, bl, pl, dist, eating ? EAT_SLOWDOWN : 1.0);
            if (!eating) tryJump(bot, bl, pl, 0.05);
        }

        tryAttack(bot, dist);
    }

    /**
     * Sprinting like a player: always sprint while moving/fighting (sprint hits deal extra knockback).
     * While falling in melee the bot stops sprinting so the hit can be a critical hit (vanilla rule).
     */
    private void updateSprint(LivingEntity bot, boolean melee, boolean eating) {
        if (!sprintEnabled || !(bot instanceof Player bp)) return;
        boolean falling = !bot.isOnGround() && bot.getVelocity().getY() < 0;
        bp.setSprinting(!eating && !(melee && falling));
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
        if (eatingUntil > 0 || tick < nextAttack || dist > diff.reach) return;
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

    private void strafe(LivingEntity bot, Location bl, Location pl, double dist, double scale) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (tick >= strafeUntil) {
            strafeUntil = tick + 8 + rnd.nextInt(18);
            strafeDir = rnd.nextDouble() < diff.strafeChance ? (rnd.nextBoolean() ? 1 : -1) : 0;
        }
        Vector toPlayer = pl.toVector().subtract(bl.toVector()).setY(0);
        if (toPlayer.lengthSquared() < 1.0E-4) return;
        toPlayer.normalize();
        Vector perp = new Vector(-toPlayer.getZ(), 0, toPlayer.getX());

        Vector add = perp.multiply(0.09 * strafeDir * scale);
        if (dist < 1.6) add.add(toPlayer.clone().multiply(-0.05)); // do not stick to the player
        bot.setVelocity(bot.getVelocity().add(add));
    }

    // ---------- healing: real splash potions / real eating ----------

    private void tryHeal(LivingEntity bot) {
        if (healItems <= 0 || tick < nextHeal || bot.getHealth() > diff.healBelow) return;
        switch (kit.healType()) {
            case POTION -> throwPotion(bot);
            case GAPPLE -> startEating(bot);
            default -> { }
        }
    }

    /** Throws a real splash healing potion at the bot's own feet. */
    private void throwPotion(LivingEntity bot) {
        healItems--;
        nextHeal = tick + (int) (diff.healCooldown * 20);
        ItemStack potion = Kit.healingSplash();
        setHand(potion);
        restoreHandAt = tick + 8;
        bot.swingMainHand();
        player.playSound(bot.getLocation(), Sound.ENTITY_SPLASH_POTION_THROW, 1f, 1f);

        Location from = bot.getLocation().add(0, 1.0, 0);
        ThrownPotion thrown = bot.getWorld().spawn(from, ThrownPotion.class, tp -> {
            tp.setItem(potion);
            tp.setShooter(bot);
            tp.setVelocity(new Vector(0, -0.6, 0));
        });
        hideFromOthers(thrown);
    }

    /** The bot holds the apple, slows down and cannot attack until it has finished eating. */
    private void startEating(LivingEntity bot) {
        healItems--;
        eatingUntil = tick + EAT_TICKS;
        restoreHandAt = 0;
        setHand(new ItemStack(Material.GOLDEN_APPLE));
        bot.swingMainHand();
    }

    private void eatEffects(LivingEntity bot) {
        if (tick % 4 != 0) return;
        Location l = bot.getEyeLocation();
        l.add(l.getDirection().multiply(0.4));
        player.playSound(l, Sound.ENTITY_GENERIC_EAT, 0.8f, 1f);
        player.spawnParticle(Particle.ITEM, l, 6, 0.1, 0.1, 0.1, 0.04, new ItemStack(Material.GOLDEN_APPLE));
    }

    private void finishEating(LivingEntity bot) {
        eatingUntil = 0;
        nextHeal = tick + (int) (diff.healCooldown * 20);
        bot.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
        bot.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 0));
        setHand(kit.weapon());
        player.playSound(bot.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.8f, 1f);
    }

    // ---------- ender pearls (NoDebuff) ----------

    /** When far away, the bot throws a real ender pearl toward the player. */
    private void tryPearl(LivingEntity bot, double dist) {
        if (pearls <= 0 || tick < nextPearl || dist < 12 || eatingUntil > 0 || bot.getHealth() <= 8.0) return;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (rnd.nextDouble() > 0.03) return;
        pearls--;
        nextPearl = tick + 20 * (8 + rnd.nextInt(8));

        setHand(new ItemStack(Material.ENDER_PEARL));
        restoreHandAt = tick + 8;
        bot.swingMainHand();
        player.playSound(bot.getLocation(), Sound.ENTITY_ENDER_PEARL_THROW, 1f, 1f);

        Vector dir = player.getLocation().add(0, 1.0, 0).toVector().subtract(bot.getEyeLocation().toVector());
        double d = dir.length();
        if (d < 1.0E-3) return;
        dir.setY(dir.getY() + d * 0.10); // compensate gravity
        dir.normalize().multiply(1.5);
        EnderPearl pearl = bot.launchProjectile(EnderPearl.class, dir);
        hideFromOthers(pearl);
    }

    /** Called by SessionListener when the bot's pearl lands: we teleport the bot ourselves. */
    void pearlLanded(Location dest) {
        if (ended || npc == null || !npc.isSpawned()) return;
        npc.teleport(dest, PlayerTeleportEvent.TeleportCause.ENDER_PEARL);
        npc.getNavigator().cancelNavigation();
        Entity e = botEntity();
        if (e instanceof LivingEntity le) le.damage(5.0); // pearl fall damage, like vanilla
    }

    // ---------- helpers ----------

    private void setHand(ItemStack item) {
        if (equipment != null) equipment.set(Equipment.EquipmentSlot.HAND, item);
    }

    private void hideFromOthers(Entity e) {
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (!o.equals(player)) o.hideEntity(plugin, e);
        }
    }

    private int cps() {
        long now = System.currentTimeMillis();
        while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000) clicks.pollFirst();
        return clicks.size();
    }

    private void updateBoard(LivingEntity bot) {
        int secs = fightTick / 20;
        double botHp = bot.getHealth() + bot.getAbsorptionAmount();
        double myHp = player.getHealth() + player.getAbsorptionAmount();
        board.setLine(0, Lang.t("board.mode", "kit", kit.displayName(), "difficulty", diff.color() + diff.displayName()));
        board.setLine(1, " ");
        board.setLine(2, Lang.t("board.time", "time", String.format(Locale.ROOT, "%d:%02d", secs / 60, secs % 60)));
        board.setLine(3, Lang.t("board.bot", "hp", String.format(Locale.ROOT, "%.1f", botHp)));
        board.setLine(4, Lang.t("board.you", "hp", String.format(Locale.ROOT, "%.1f", myHp)));
        board.setLine(5, Lang.t("board.cps", "cps", cps()));
        board.setLine(6, Lang.t("board.streak", "streak", plugin.stats().streakOf(player.getUniqueId())));
    }

    // ---------- end ----------

    /** Removes the bot and restores the player. Called by SessionManager. */
    void cleanup() {
        ended = true;
        if (task != null) task.cancel();
        if (board != null) board.hide();
        if (npc != null) {
            try {
                npc.destroy();
            } catch (Exception ignored) { }
        }
        ChunkHold.release(plugin, chunks);
        player.setCollidable(true);
        if (player.isOnline() && state != null) {
            state.restore(player);
        }
        if (stateFile.exists()) stateFile.delete();
    }
}
