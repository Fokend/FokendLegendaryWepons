package FuckFokend.fokendLegendaryWepons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Keyed;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

final class CraftProtectionListener implements Listener {
    private final FokendLegendaryWepons plugin;

    CraftProtectionListener(FokendLegendaryWepons plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        CraftingInventory inv = e.getInventory();
        ItemStack res = inv.getResult();
        String resId = plugin.getId(res);
        if (resId != null && !plugin.isCraftingCore(resId) && plugin.craftedUniques.contains(resId)) {
            inv.setResult(null);
            return;
        }
        for (ItemStack m : inv.getMatrix()) {
            String matId = plugin.getId(m);
            if (matId != null) {
                // Разрешаем использовать ядра (cb_orange, cb_purple, cb_green) только для крафта легендарных предметов
                if (plugin.isCraftingCore(matId) && resId != null && !plugin.isCraftingCore(resId)) {
                    continue;
                }
                inv.setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent e) {
        ItemStack res = e.getRecipe().getResult();
        String resId = plugin.getId(res);
        if (resId == null) return;
        if (e.isShiftClick() || e.getHotbarButton() != -1 || e.getAction() == InventoryAction.NOTHING
                || e.getCursor() == null || !e.getCursor().getType().isAir()) {
            e.setCancelled(true);
            e.getWhoClicked().sendMessage(Component.text("Легендарные предметы и ядра нужно забирать пустым курсором (ЛКМ).", NamedTextColor.RED));
            return;
        }
        if (plugin.isCraftingCore(resId)) {
            return;
        }
        if (plugin.craftedUniques.contains(resId)) {
            e.setCancelled(true);
            e.getWhoClicked().sendMessage(Component.text("Этот легендарный предмет уже был создан на сервере!", NamedTextColor.RED));
            return;
        }
        plugin.craftedUniques.add(resId);
        if (e.getRecipe() instanceof Keyed keyed) {
            plugin.craftedUniques.add(keyed.getKey().getKey());
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                online.undiscoverRecipe(keyed.getKey());
            }
        }
        plugin.saveSouls();
        Component itemName = res.getItemMeta() != null && res.getItemMeta().hasDisplayName()
                ? res.getItemMeta().displayName()
                : Component.text(resId, NamedTextColor.GOLD);
        plugin.getServer().broadcast(Component.text(e.getWhoClicked().getName() + " сковал легендарное оружие: ", NamedTextColor.YELLOW)
                .append(itemName)
                .append(Component.text("!", NamedTextColor.YELLOW)));
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            online.playSound(online.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.9F, 1.1F);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent e) {
        if (plugin.getId(e.getResult()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareGrindstone(PrepareGrindstoneEvent e) {
        var inventory = e.getInventory();
        if (plugin.getId(inventory.getUpperItem()) != null
                || plugin.getId(inventory.getLowerItem()) != null) {
            e.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareSmithing(PrepareSmithingEvent e) {
        var inventory = e.getInventory();
        if (plugin.getId(inventory.getInputTemplate()) != null
                || plugin.getId(inventory.getInputEquipment()) != null
                || plugin.getId(inventory.getInputMineral()) != null) {
            e.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent e) {
        var inventory = e.getInventory();
        String firstId = plugin.getId(inventory.getFirstItem());
        String secondId = plugin.getId(inventory.getSecondItem());
        // Core upgrades are intentionally handled by CoreUpgradeListener.
        if (plugin.isCraftingCore(firstId) || plugin.isCraftingCore(secondId)) return;
        if (firstId != null || secondId != null) e.setResult(null);
    }
}
