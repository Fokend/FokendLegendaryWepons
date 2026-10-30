package FuckFokend.fokendLegendaryWepons;

import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

final class EntityRightClickBridge implements Listener {
    private final FokendLegendaryWepons plugin;

    EntityRightClickBridge(FokendLegendaryWepons plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityClick(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        ItemStack main = p.getInventory().getItemInMainHand();
        ItemStack helm = p.getInventory().getHelmet();
        boolean mainIsLegendary = plugin.getId(main) != null;
        boolean wearingWarden = plugin.isLegendary(helm, FokendLegendaryWepons.ID_WARDEN_HELMET);
        boolean maskShiftClick = p.isSneaking()
                && (main == null || main.getType().isAir())
                && plugin.isLegendary(helm, FokendLegendaryWepons.ID_DEFAULT_MASK);
        if (mainIsLegendary || wearingWarden || maskShiftClick) {
            PlayerInteractEvent fake = new PlayerInteractEvent(
                    p, Action.RIGHT_CLICK_AIR, main, null, BlockFace.SELF, EquipmentSlot.HAND);
            plugin.getServer().getPluginManager().callEvent(fake);
            if (fake.useItemInHand() == Event.Result.DENY || fake.isCancelled()) {
                e.setCancelled(true);
            }
        }
    }
}
