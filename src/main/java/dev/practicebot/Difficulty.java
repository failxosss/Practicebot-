package dev.practicebot;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public enum Difficulty {
    NORMAL("Normal", Material.LIME_CONCRETE, ChatColor.GREEN, 2.8, 4.0, 0.50, 0.85, 0.3, 0.03, 6.0, 6.0),
    MEDIUM("Medium", Material.YELLOW_CONCRETE, ChatColor.YELLOW, 3.0, 6.0, 0.65, 0.90, 0.5, 0.05, 7.0, 5.0),
    HARD("Hard", Material.ORANGE_CONCRETE, ChatColor.GOLD, 3.2, 8.0, 0.80, 0.95, 0.7, 0.08, 8.0, 4.0),
    PROFESSIONAL("Professional", Material.RED_CONCRETE, ChatColor.RED, 3.4, 11.0, 0.92, 1.00, 0.9, 0.12, 9.0, 3.0);

    private final String displayName;
    private final Material icon;
    private final ChatColor color;

    public double reach;
    public double cps;
    public double accuracy;
    public double speed;
    public double strafeChance;
    public double jumpChance;
    public double healBelow;
    public double healCooldown;

    Difficulty(String displayName, Material icon, ChatColor color, double reach, double cps, double accuracy,
               double speed, double strafeChance, double jumpChance, double healBelow, double healCooldown) {
        this.displayName = displayName;
        this.icon = icon;
        this.color = color;
        this.reach = reach;
        this.cps = cps;
        this.accuracy = accuracy;
        this.speed = speed;
        this.strafeChance = strafeChance;
        this.jumpChance = jumpChance;
        this.healBelow = healBelow;
        this.healCooldown = healCooldown;
    }

    public String displayName() { return displayName; }
    public Material icon() { return icon; }
    public ChatColor color() { return color; }

    public static Difficulty fromString(String s) {
        if (s == null) return null;
        for (Difficulty d : values()) {
            if (d.name().equalsIgnoreCase(s) || d.displayName.equalsIgnoreCase(s)) return d;
        }
        if (s.equalsIgnoreCase("pro")) return PROFESSIONAL;
        return null;
    }

    public static void loadAll(FileConfiguration cfg) {
        for (Difficulty d : values()) {
            ConfigurationSection s = cfg.getConfigurationSection("difficulties." + d.name());
            if (s == null) continue;
            d.reach = s.getDouble("reach", d.reach);
            d.cps = Math.max(0.5, s.getDouble("cps", d.cps));
            d.accuracy = s.getDouble("accuracy", d.accuracy);
            d.speed = s.getDouble("speed", d.speed);
            d.strafeChance = s.getDouble("strafe-chance", d.strafeChance);
            d.jumpChance = s.getDouble("jump-chance", d.jumpChance);
            d.healBelow = s.getDouble("heal-below", d.healBelow);
            d.healCooldown = s.getDouble("heal-cooldown", d.healCooldown);
        }
    }
}
