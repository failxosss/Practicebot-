package dev.practicebot;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Saves the player's state before a fight and restores it afterwards. Also saved to disk (crash protection). */
public final class PlayerState {
    private ItemStack[] contents;
    private ItemStack[] armor;
    private ItemStack[] extra;
    private Location location;
    private GameMode gameMode;
    private double health;
    private int food;
    private float saturation;
    private float exp;
    private int level;
    private boolean allowFlight;
    private boolean flying;
    private List<PotionEffect> effects = new ArrayList<>();

    private PlayerState() {}

    public static PlayerState capture(Player p) {
        PlayerState s = new PlayerState();
        s.contents = p.getInventory().getContents().clone();
        s.armor = p.getInventory().getArmorContents().clone();
        s.extra = p.getInventory().getExtraContents().clone();
        s.location = p.getLocation().clone();
        s.gameMode = p.getGameMode();
        s.health = p.getHealth();
        s.food = p.getFoodLevel();
        s.saturation = p.getSaturation();
        s.exp = p.getExp();
        s.level = p.getLevel();
        s.allowFlight = p.getAllowFlight();
        s.flying = p.isFlying();
        s.effects = new ArrayList<>(p.getActivePotionEffects());
        return s;
    }

    public void restore(Player p) {
        p.getInventory().clear();
        p.getInventory().setContents(contents);
        p.getInventory().setArmorContents(armor);
        p.getInventory().setExtraContents(extra);
        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
        p.addPotionEffects(effects);
        p.setGameMode(gameMode);
        p.setAllowFlight(allowFlight);
        p.setFlying(flying && allowFlight);
        p.setHealth(Math.max(1.0, Math.min(health, p.getMaxHealth())));
        p.setFoodLevel(food);
        p.setSaturation(saturation);
        p.setExp(exp);
        p.setLevel(level);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        p.teleport(location);
        p.updateInventory();
    }

    public void save(File f) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("contents", Arrays.asList(contents));
        y.set("armor", Arrays.asList(armor));
        y.set("extra", Arrays.asList(extra));
        y.set("location", location);
        y.set("gamemode", gameMode.name());
        y.set("health", health);
        y.set("food", food);
        y.set("saturation", (double) saturation);
        y.set("exp", (double) exp);
        y.set("level", level);
        y.set("allowFlight", allowFlight);
        y.set("flying", flying);
        y.set("effects", effects);
        try {
            f.getParentFile().mkdirs();
            y.save(f);
        } catch (IOException e) {
            throw new RuntimeException("Could not save player state: " + e.getMessage(), e);
        }
    }

    public static PlayerState load(File f) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        PlayerState s = new PlayerState();
        s.contents = items(y.getList("contents"));
        s.armor = items(y.getList("armor"));
        s.extra = items(y.getList("extra"));
        Object loc = y.get("location");
        if (!(loc instanceof Location)) return null;
        s.location = (Location) loc;
        try {
            s.gameMode = GameMode.valueOf(y.getString("gamemode", "SURVIVAL"));
        } catch (IllegalArgumentException ex) {
            s.gameMode = GameMode.SURVIVAL;
        }
        s.health = y.getDouble("health", 20.0);
        s.food = y.getInt("food", 20);
        s.saturation = (float) y.getDouble("saturation", 5.0);
        s.exp = (float) y.getDouble("exp", 0.0);
        s.level = y.getInt("level", 0);
        s.allowFlight = y.getBoolean("allowFlight", false);
        s.flying = y.getBoolean("flying", false);
        List<?> eff = y.getList("effects");
        if (eff != null) for (Object o : eff) if (o instanceof PotionEffect pe) s.effects.add(pe);
        return s;
    }

    private static ItemStack[] items(List<?> list) {
        if (list == null) return new ItemStack[0];
        ItemStack[] out = new ItemStack[list.size()];
        for (int i = 0; i < out.length; i++) {
            Object o = list.get(i);
            out[i] = o instanceof ItemStack is ? is : null;
        }
        return out;
    }
}
