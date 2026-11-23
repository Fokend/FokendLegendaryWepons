package fokend.legendaryweapons;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Moves legendary ids from the old item keys to the current one as items come into view:
 * a player's inventory and ender chest on join, and any container when it is opened.
 */
final class LegacyItemMigrationListener implements Listener {
    private final FokendLegendaryWepons plugin;

    LegacyItemMigrationListener(FokendLegendaryWepons plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        this.migrate(e.getPlayer().getInventory());
        this.migrate(e.getPlayer().getEnderChest());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        this.migrate(e.getInventory());
    }

    private void migrate(Inventory inv) {
        ItemStack[] contents = inv.getContents();
        int changed = 0;
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (this.plugin.migrateLegacyId(item)) {
                inv.setItem(i, item);
                changed++;
            }
        }
        if (changed > 0) {
            this.plugin.getLogger().info("Migrated " + changed + " legendary item(s) to the current id key");
        }
    }
}
