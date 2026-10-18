package FuckFokend.fokendLegendaryWepons;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

final class ItemProtectionListener implements Listener {
    private final FokendLegendaryWepons plugin;
    ItemProtectionListener(FokendLegendaryWepons plugin) { this.plugin = plugin; }

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onItemSpawn(EntitySpawnEvent e) {
            if (e.getEntity() instanceof Item item) plugin.protectDroppedItem(item);
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onItemDamage(EntityDamageEvent e) {
            if (e.getEntity() instanceof Item item && plugin.getId(item.getItemStack()) != null) e.setCancelled(true);
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onPlace(BlockPlaceEvent e) {
            if (plugin.getId(e.getItemInHand()) != null) {
                e.setCancelled(true);
                e.setBuild(false);
            }
        }

        @EventHandler(priority = EventPriority.HIGH)
        public void onVoidDeath(PlayerDeathEvent e) {
            Player p = e.getEntity();
            if (p.hasMetadata(plugin.META_DEATH_REASON)) {
                String raw = p.getMetadata(plugin.META_DEATH_REASON).get(0).asString();
                p.removeMetadata(plugin.META_DEATH_REASON, plugin);
                String[] parts = raw.split(":", 2);
                String reason = parts[0];
                String killerName = parts.length > 1 ? parts[1] : "Игрок";
                if ("mask_hammer".equals(reason)) {
                    e.deathMessage(Component.text(p.getName() + " был расплющен Молотом Маски игрока " + killerName, NamedTextColor.RED));
                } else if ("mask_thompson".equals(reason)) {
                    e.deathMessage(Component.text(p.getName() + " был изрешечён из Томпсонов игрока " + killerName, NamedTextColor.RED));
                }
            }

            EntityDamageEvent last = p.getLastDamageCause();
            if (last == null || (last.getCause() != DamageCause.VOID && p.getLocation().getY() >= p.getWorld().getMinHeight())) return;
            List<ItemStack> rescue = new ArrayList<>();
            Iterator<ItemStack> it = e.getDrops().iterator();
            while (it.hasNext()) {
                ItemStack drop = it.next();
                if (plugin.getId(drop) != null) {
                    rescue.add(drop);
                    it.remove();
                }
            }
            if (!rescue.isEmpty()) {
                Location safe = findSafeLocationNearVoid(p.getLocation());
                for (ItemStack item : rescue) {
                    Item dropped = safe.getWorld().dropItem(safe, item);
                    dropped.setVelocity(new Vector(0, 0.1, 0));
                    plugin.protectDroppedItem(dropped);
                }
            }
        }

        private Location findSafeLocationNearVoid(Location origin) {
            World w = origin.getWorld();
            int cx = origin.getBlockX(), cz = origin.getBlockZ();
            for (int r = 0; r <= 40; r += 2) {
                for (int dx = -r; dx <= r; dx += 2) {
                    for (int dz = -r; dz <= r; dz += 2) {
                        if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                        Block highest = w.getHighestBlockAt(cx + dx, cz + dz);
                        if (!highest.getType().isAir() && highest.getType().isSolid())
                            return highest.getLocation().add(0.5, 1.2, 0.5);
                    }
                }
            }
            return w.getSpawnLocation().add(0.5, 1.0, 0.5);
        }
    
}
