package dev.practicebot;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.entity.Player;

/** Definice kitu. Hrac i bot dostanou stejnou vybavu. */
public enum Kit {
    SWORD("Sword", Material.DIAMOND_SWORD, "Klasicky souboj s diamantovym mecem", HealType.NONE, 0, 7.0),
    AXE("Axe", Material.DIAMOND_AXE, "Souboj se sekerou (vetsi damage, pomalejsi)", HealType.NONE, 0, 9.0),
    NODEBUFF("NoDebuff", Material.SPLASH_POTION, "Diamant + splash healing potiony", HealType.POTION, 12, 7.0),
    GAPPLE("Gapple", Material.GOLDEN_APPLE, "Diamant + zlata jablka", HealType.GAPPLE, 8, 7.0);

    public enum HealType { NONE, POTION, GAPPLE }

    private final String displayName;
    private final Material icon;
    private final String description;
    private final HealType healType;
    private final int botHealItems;
    private final double fallbackDamage;

    Kit(String displayName, Material icon, String description, HealType healType, int botHealItems, double fallbackDamage) {
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
        this.healType = healType;
        this.botHealItems = botHealItems;
        this.fallbackDamage = fallbackDamage;
    }

    public String displayName() { return displayName; }
    public Material icon() { return icon; }
    public String description() { return description; }
    public HealType healType() { return healType; }
    public int botHealItems() { return botHealItems; }
    public double fallbackDamage() { return fallbackDamage; }
    public String id() { return name().toLowerCase(); }

    public static Kit fromString(String s) {
        if (s == null) return null;
        for (Kit k : values()) {
            if (k.name().equalsIgnoreCase(s) || k.displayName.equalsIgnoreCase(s)) return k;
        }
        return null;
    }

    // ---- vybava ----

    public ItemStack weapon() {
        Material m = this == AXE ? Material.DIAMOND_AXE : Material.DIAMOND_SWORD;
        return piece(m, 0);
    }

    /** Poradi pro setArmorContents: boots, leggings, chestplate, helmet. */
    public ItemStack[] armor() {
        boolean diamond = this == NODEBUFF || this == GAPPLE;
        int prot = diamond ? 2 : 0;
        return new ItemStack[]{
                piece(diamond ? Material.DIAMOND_BOOTS : Material.IRON_BOOTS, prot),
                piece(diamond ? Material.DIAMOND_LEGGINGS : Material.IRON_LEGGINGS, prot),
                piece(diamond ? Material.DIAMOND_CHESTPLATE : Material.IRON_CHESTPLATE, prot),
                piece(diamond ? Material.DIAMOND_HELMET : Material.IRON_HELMET, prot)
        };
    }

    public void giveTo(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setArmorContents(armor());
        inv.setItem(0, weapon());
        switch (this) {
            case SWORD, AXE -> {
                inv.setItem(1, new ItemStack(Material.GOLDEN_APPLE, 4));
                inv.setItem(2, new ItemStack(Material.COOKED_BEEF, 32));
            }
            case NODEBUFF -> {
                inv.setItem(1, new ItemStack(Material.ENDER_PEARL, 16));
                for (int i = 2; i < 36; i++) inv.setItem(i, healingSplash());
            }
            case GAPPLE -> {
                inv.setItem(1, new ItemStack(Material.GOLDEN_APPLE, 16));
                inv.setItem(2, new ItemStack(Material.COOKED_BEEF, 32));
            }
        }
        p.updateInventory();
    }

    private static ItemStack healingSplash() {
        ItemStack pot = new ItemStack(Material.SPLASH_POTION);
        PotionMeta pm = (PotionMeta) pot.getItemMeta();
        pm.setBasePotionType(PotionType.STRONG_HEALING);
        pot.setItemMeta(pm);
        return pot;
    }

    private static ItemStack piece(Material m, int protection) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        if (protection > 0) {
            Enchantment e = Enchantment.getByKey(NamespacedKey.minecraft("protection"));
            if (e != null) meta.addEnchant(e, protection, true);
        }
        item.setItemMeta(meta);
        return item;
    }
}
