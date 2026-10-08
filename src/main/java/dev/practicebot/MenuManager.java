package dev.practicebot;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** GUI: 1) vyber kitu  2) vyber obtiznosti. */
public final class MenuManager implements Listener {
    private static final int[] SLOTS = {10, 12, 14, 16};
    private static final int BACK_SLOT = 22;

    private enum Type { KIT, DIFFICULTY }

    private static final class MenuHolder implements InventoryHolder {
        final Type type;
        final Kit kit;
        Inventory inv;
        MenuHolder(Type type, Kit kit) { this.type = type; this.kit = kit; }
        @Override public Inventory getInventory() { return inv; }
    }

    private final PracticeBotPlugin plugin;

    public MenuManager(PracticeBotPlugin plugin) {
        this.plugin = plugin;
    }

    public void openKits(Player p) {
        MenuHolder h = new MenuHolder(Type.KIT, null);
        Inventory inv = Bukkit.createInventory(h, 27, ChatColor.DARK_GRAY + "PracticeBot " + ChatColor.GRAY + "- vyber kit");
        h.inv = inv;
        fill(inv);
        Kit[] kits = Kit.values();
        for (int i = 0; i < kits.length && i < SLOTS.length; i++) {
            Kit k = kits[i];
            int arenas = plugin.arenas().forKit(k).size();
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + k.description());
            lore.add("");
            lore.add((arenas > 0 ? ChatColor.GREEN : ChatColor.RED) + "Arény: " + arenas);
            lore.add(ChatColor.YELLOW + "Klikni pro vyber");
            inv.setItem(SLOTS[i], item(k.icon(), ChatColor.AQUA + "" + ChatColor.BOLD + k.displayName(), lore));
        }
        p.openInventory(inv);
    }

    public void openDifficulties(Player p, Kit kit) {
        MenuHolder h = new MenuHolder(Type.DIFFICULTY, kit);
        Inventory inv = Bukkit.createInventory(h, 27, ChatColor.DARK_GRAY + kit.displayName() + ChatColor.GRAY + " - obtiznost");
        h.inv = inv;
        fill(inv);
        Difficulty[] ds = Difficulty.values();
        for (int i = 0; i < ds.length && i < SLOTS.length; i++) {
            Difficulty d = ds[i];
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Presnost: " + ChatColor.WHITE + (int) (d.accuracy * 100) + "%");
            lore.add(ChatColor.GRAY + "CPS: " + ChatColor.WHITE + d.cps);
            lore.add(ChatColor.GRAY + "Dosah: " + ChatColor.WHITE + d.reach);
            lore.add("");
            lore.add(ChatColor.YELLOW + "Klikni pro start");
            inv.setItem(SLOTS[i], item(d.icon(), d.color() + "" + ChatColor.BOLD + d.displayName(), lore));
        }
        inv.setItem(BACK_SLOT, item(Material.ARROW, ChatColor.RED + "Zpet", List.of()));
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof MenuHolder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getSlot();

        if (h.type == Type.KIT) {
            Kit[] kits = Kit.values();
            for (int i = 0; i < kits.length && i < SLOTS.length; i++) {
                if (SLOTS[i] == slot) {
                    if (plugin.arenas().forKit(kits[i]).isEmpty()) {
                        plugin.msg(p, "&cPro kit &e" + kits[i].displayName() + "&c zatim neni arena.");
                        return;
                    }
                    openDifficulties(p, kits[i]);
                    return;
                }
            }
        } else {
            if (slot == BACK_SLOT) {
                openKits(p);
                return;
            }
            Difficulty[] ds = Difficulty.values();
            for (int i = 0; i < ds.length && i < SLOTS.length; i++) {
                if (SLOTS[i] == slot) {
                    p.closeInventory();
                    plugin.sessions().start(p, h.kit, ds[i]);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof MenuHolder) e.setCancelled(true);
    }

    private static void fill(Inventory inv) {
        ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
    }

    private static ItemStack item(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        if (!lore.isEmpty()) meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }
}
